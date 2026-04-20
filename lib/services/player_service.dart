import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:just_audio/just_audio.dart';
import 'package:just_audio_background/just_audio_background.dart';
import '../models/download_item.dart';
import '../services/download_service.dart';
import 'dart:async';
import '../services/database_service.dart';
import '../services/observability_service.dart';
import '../services/artwork_cache_service.dart';
import '../utils/metadata_utils.dart';

class PlayerService {
  PlayerService._();
  static final instance = PlayerService._();

  static const String _defaultAlbumName = 'YTDown';
  static const String _defaultArtistName = 'Desconhecido';

  final AudioPlayer _player = AudioPlayer();
  AudioPlayer get player => _player;

  final _currentTrackController = StreamController<DownloadItem?>.broadcast();
  Stream<DownloadItem?> get currentTrackStream =>
      _currentTrackController.stream;
  DownloadItem? _currentTrack;
  DownloadItem? get currentTrack => _currentTrack;

  // Listener para sequência de playlist (para evitar memory leak)
  StreamSubscription<SequenceState>? _sequenceStateSubscription;

  // Itens da playlist atual (cópia imutável para evitar captura de referência externa)
  List<DownloadItem>? _playlistItems;

  // Evita múltiplas requisições de artwork para o mesmo item ao mesmo tempo.
  final Map<String, Future<DownloadItem>> _artworkInFlight = {};

  // Streams de progresso
  Stream<PlayerState> get playerStateStream => _player.playerStateStream;
  Stream<Duration> get positionStream => _player.positionStream;
  Stream<Duration?> get durationStream => _player.durationStream;
  Stream<Duration> get bufferedPositionStream => _player.bufferedPositionStream;
  Stream<SequenceState> get sequenceStateStream => _player.sequenceStateStream;

  Future<void> initialize() async {
    // Inicialização do serviço real
  }

  Future<void> _resetPlaybackContext() async {
    await _sequenceStateSubscription?.cancel();
    _sequenceStateSubscription = null;
    _playlistItems = null;
  }

  MediaItem _buildMediaItem(DownloadItem item) {
    return MediaItem(
      id: item.id,
      album: item.album ?? _defaultAlbumName,
      title: item.title,
      artist: item.artist ?? _defaultArtistName,
      artUri: _safeArtUri(item.albumImageUrl ?? item.thumbnail),
    );
  }

  Future<void> playTrack(DownloadItem item) async {
    // Cancela qualquer listener de playlist ativo e reseta referências
    await _resetPlaybackContext();

    final observability = ObservabilityService.instance;
    final playableItem = await _resolvePlayableItem(item);
    if (playableItem == null) {
      debugPrint('❌ Arquivo de áudio não encontrado: ${item.outputPath}');
      observability.warning(
        'player_track_file_missing',
        context: {
          'id': item.id,
          'title': item.title,
          'path': item.outputPath,
        },
      );
      _currentTrack = null;
      _currentTrackController.add(null);
      return;
    }

    _currentTrack = playableItem;
    _currentTrackController.add(playableItem);
    unawaited(_hydrateArtworkForTrack(playableItem));

    try {
      final source = AudioSource.uri(
        Uri.file(playableItem.outputPath),
        tag: _buildMediaItem(playableItem),
      );

      await _player.setAudioSource(source);
      await _player.play();
    } catch (e) {
      debugPrint("❌ Erro ao tocar música: $e");
      observability.warning(
        'player_play_track_failed',
        context: {
          'id': playableItem.id,
          'title': playableItem.title,
          'path': playableItem.outputPath,
          'error': e.toString(),
        },
      );
      _currentTrack = null;
      _currentTrackController.add(null);
    }
  }

  Future<DownloadItem?> _resolvePlayableItem(DownloadItem item) async {
    final file = File(item.outputPath);
    if (await file.exists()) {
      return item;
    }

    final resolvedPath = await _resolveOutputPathAfterMiss(item.outputPath);
    if (resolvedPath == null) {
      return null;
    }

    final updated = item.copyWith(
      outputPath: resolvedPath,
      format: _extractExtension(resolvedPath) ?? item.format,
    );
    final databaseService = DatabaseService.instance;
    await databaseService.updateDownload(updated);
    return updated;
  }

  Future<String?> _resolveOutputPathAfterMiss(String expectedPath) async {
    final expectedFile = File(expectedPath);
    final directory = expectedFile.parent;
    if (!await directory.exists()) return null;

    final expectedName = expectedFile.uri.pathSegments.last;
    final expectedBase = expectedName.contains('.')
        ? expectedName.substring(0, expectedName.lastIndexOf('.'))
        : expectedName;

    for (final entity in directory.listSync()) {
      if (entity is! File) continue;
      final fileName = entity.uri.pathSegments.last;
      if (fileName.startsWith(expectedBase)) {
        return entity.path;
      }
    }

    return null;
  }

  Future<List<DownloadItem>> _filterValidPlaylistItems(
      List<DownloadItem> items) async {
    final validItems = <DownloadItem>[];

    final downloadService = DownloadService.instance;
    for (final item in items) {
      final file = File(item.outputPath);
      if (await file.exists()) {
        validItems.add(item);
        continue;
      }

      debugPrint('🧹 Limpando registro fantasma na playlist: ${item.title}');
      try {
        await downloadService.deleteDownload(item);
      } catch (_) {}
    }

    return validItems;
  }

  int _safeInitialIndex(int initialIndex, int itemCount) {
    return initialIndex < itemCount ? initialIndex : 0;
  }

  List<AudioSource> _buildAudioSources(List<DownloadItem> playlistItems) {
    return playlistItems
        .map((item) => AudioSource.uri(
              Uri.file(item.outputPath),
              tag: _buildMediaItem(item),
            ))
        .toList();
  }

  Future<void> _unsubscribeSequenceState() async {
    await _sequenceStateSubscription?.cancel();
    _sequenceStateSubscription = null;
  }

  void _subscribeSequenceState() {
    _sequenceStateSubscription = _player.sequenceStateStream.listen((state) {
      final index = state.currentIndex;
      if (index == null || _playlistItems == null) return;
      if (index >= _playlistItems!.length) return;

      _currentTrack = _playlistItems![index];
      _currentTrackController.add(_currentTrack);
      if (_currentTrack == null) return;
      unawaited(_hydrateArtworkForTrack(_currentTrack!));
    });
  }

  String? _extractExtension(String path) {
    final lastDot = path.lastIndexOf('.');
    if (lastDot < 0 || lastDot == path.length - 1) return null;
    return path.substring(lastDot + 1).toLowerCase();
  }

  Future<void> playPlaylist(List<DownloadItem> items,
      {int initialIndex = 0}) async {
    // Cancela listener anterior e limpa referência para evitar memory leak
    await _resetPlaybackContext();

    final validItems = await _filterValidPlaylistItems(items);
    if (validItems.isEmpty) return;

    final safeInitialIndex = _safeInitialIndex(initialIndex, validItems.length);
    _playlistItems = List.from(validItems);

    final sources = _buildAudioSources(_playlistItems!);
    await _unsubscribeSequenceState();
    _subscribeSequenceState();

    await _player.setAudioSources(sources, initialIndex: safeInitialIndex);
    await _player.play();

    if (_playlistItems!.isNotEmpty) {
      unawaited(_hydrateArtworkForTrack(_playlistItems![safeInitialIndex]));
    }
  }

  Future<void> pause() async => await _player.pause();
  Future<void> resume() async => await _player.play();

  Future<void> stop() async {
    await _resetPlaybackContext();
    await _player.stop();
  }

  Future<void> next() async => await _player.seekToNext();
  Future<void> previous() async => await _player.seekToPrevious();
  Future<void> seek(Duration position) async => await _player.seek(position);

  Future<void> cycleLoopMode() async {
    final current = _player.loopMode;
    final next = switch (current) {
      LoopMode.off => LoopMode.all,
      LoopMode.all => LoopMode.one,
      LoopMode.one => LoopMode.off,
    };
    await _player.setLoopMode(next);
  }

  void applyTrackMetadataUpdate(DownloadItem updated) {
    _replacePlaylistItem(updated);

    if (_currentTrack?.id == updated.id) {
      _currentTrack = updated;
      _currentTrackController.add(updated);
    }
  }

  void applyArtistBatchMetadataUpdate({
    required String oldArtist,
    required String newArtist,
    String? newArtistImageUrl,
  }) {
    _applyBatchMetadataUpdate(
      oldValue: oldArtist,
      readValue: (item) => item.artist,
      applyUpdate: (item) => item.copyWith(
        artist: newArtist,
        artistImageUrl: newArtistImageUrl ?? item.artistImageUrl,
      ),
    );
  }

  void applyAlbumBatchMetadataUpdate({
    required String oldAlbum,
    required String newAlbum,
    String? newAlbumImageUrl,
  }) {
    _applyBatchMetadataUpdate(
      oldValue: oldAlbum,
      readValue: (item) => item.album,
      applyUpdate: (item) => item.copyWith(
        album: newAlbum,
        albumImageUrl: newAlbumImageUrl ?? item.albumImageUrl,
      ),
    );
  }

  void _applyBatchMetadataUpdate({
    required String oldValue,
    required String? Function(DownloadItem item) readValue,
    required DownloadItem Function(DownloadItem item) applyUpdate,
  }) {
    final normalizedOld = _normalizeArtistForCompare(oldValue);
    if (normalizedOld.isEmpty) {
      return;
    }

    final playlist = _playlistItems;
    if (playlist != null && playlist.isNotEmpty) {
      var changed = false;
      final nextPlaylist = <DownloadItem>[];

      for (final item in playlist) {
        if (_normalizeArtistForCompare(readValue(item)) == normalizedOld) {
          nextPlaylist.add(applyUpdate(item));
          changed = true;
          continue;
        }

        nextPlaylist.add(item);
      }

      if (changed) {
        _playlistItems = nextPlaylist;
      }
    }

    final current = _currentTrack;
    if (current != null &&
        _normalizeArtistForCompare(readValue(current)) == normalizedOld) {
      final updatedCurrent = applyUpdate(current);
      _currentTrack = updatedCurrent;
      _currentTrackController.add(updatedCurrent);
    }
  }

  /// Limpa recursos e evita memory leaks
  Future<void> dispose() async {
    _artworkInFlight.clear();
    await _currentTrackController.close();
    await _sequenceStateSubscription?.cancel();
    await _player.dispose();
  }

  /// Retorna um Uri válido ou null se a string for vazia/null
  static Uri? _safeArtUri(String? url) {
    final normalized = _normalizeArtworkSource(url);
    if (normalized == null) return null;

    if (normalized.startsWith('/')) {
      return Uri.file(normalized);
    }

    return Uri.tryParse(normalized);
  }

  static String? _normalizeArtworkSource(String? url) {
    final trimmed = url?.trim();
    if (trimmed == null || trimmed.isEmpty) return null;
    if (MetadataUtils.isUnknownAppMetadata(trimmed)) return null;

    final uri = Uri.tryParse(trimmed);
    if (uri != null && uri.hasScheme) {
      final scheme = uri.scheme.toLowerCase();
      if (scheme == 'http' || scheme == 'https' || scheme == 'file') {
        return trimmed;
      }
      return null;
    }

    if (trimmed.startsWith('/')) {
      return trimmed;
    }

    return null;
  }

  Future<void> _hydrateArtworkForTrack(DownloadItem item) async {
    if (item.type != DownloadType.audio) return;
    if (_hasArtwork(item.artistImageUrl) && _hasArtwork(item.albumImageUrl)) {
      return;
    }

    final existingTask = _artworkInFlight[item.id];
    if (existingTask != null) {
      await existingTask;
      return;
    }

    final task = _fetchAndPersistArtwork(item);
    _artworkInFlight[item.id] = task;

    try {
      final enriched = await task;
      _replacePlaylistItem(enriched);

      if (_currentTrack?.id == enriched.id) {
        _currentTrack = enriched;
        _currentTrackController.add(enriched);
      }
    } catch (e) {
      debugPrint('⚠️ Falha ao hidratar artwork do player: $e');
    } finally {
      if (_artworkInFlight[item.id] == task) {
        _artworkInFlight.remove(item.id);
      }
    }
  }

  Future<DownloadItem> _fetchAndPersistArtwork(DownloadItem item) async {
    var updated = item;
    final artist = (item.artist ?? '').trim();
    final album = (item.album ?? 'YTDown').trim();
    final title = item.title.trim();

    final needsArtist = !_hasArtwork(item.artistImageUrl);
    final needsAlbum = !_hasArtwork(item.albumImageUrl);

    if (needsArtist && !_isUnknownMetadata(artist)) {
      updated = updated.copyWith(
        artistImageUrl: await _getArtistImageCached(artist),
      );
    }

    if (needsAlbum && !_isUnknownMetadata(artist)) {
      var albumImage = updated.albumImageUrl;

      if (!_isUnknownMetadata(album)) {
        albumImage = await _getAlbumCoverCached(artist, album);
      }

      if (!_hasArtwork(albumImage) && !_isUnknownMetadata(title)) {
        albumImage = await _getTrackCoverCached(artist, title);
      }

      updated = updated.copyWith(albumImageUrl: albumImage);
    }

    final changed = updated.artistImageUrl != item.artistImageUrl ||
        updated.albumImageUrl != item.albumImageUrl;
    if (changed) {
      final databaseService = DatabaseService.instance;
      await databaseService.updateDownload(updated);
    }

    return updated;
  }

  void _replacePlaylistItem(DownloadItem updated) {
    final playlist = _playlistItems;
    if (playlist == null) return;
    final idx = playlist.indexWhere((track) => track.id == updated.id);
    if (idx >= 0) {
      final nextPlaylist = List<DownloadItem>.from(playlist);
      nextPlaylist[idx] = updated;
      _playlistItems = nextPlaylist;
    }
  }

  bool _hasArtwork(String? url) => _normalizeArtworkSource(url) != null;

  String _normalizeArtistForCompare(String? value) {
    if (value == null) return '';
    return MetadataUtils.normalizeMetadataText(value).trim().toLowerCase();
  }

  Future<String?> _getArtistImageCached(String artist) async {
    final artworkService = ArtworkCacheService.instance;
    return artworkService.getArtistImage(artist);
  }

  Future<String?> _getAlbumCoverCached(String artist, String album) async {
    final artworkService = ArtworkCacheService.instance;
    return artworkService.getAlbumCover(artist, album);
  }

  Future<String?> _getTrackCoverCached(String artist, String title) async {
    final artworkService = ArtworkCacheService.instance;
    return artworkService.getTrackCover(artist, title);
  }

  bool _isUnknownMetadata(String value) {
    return MetadataUtils.isUnknownAppMetadata(value);
  }
}
