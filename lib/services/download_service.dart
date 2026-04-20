import 'dart:io';
import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';
import 'package:uuid/uuid.dart';
import '../models/download_item.dart';
import '../services/database_service.dart';
import '../services/notification_service.dart';
import '../services/observability_service.dart';
import 'download_feed_service.dart';
import 'chaquo_download_service.dart';
import 'storage_service.dart';
import 'artwork_cache_service.dart';
import '../utils/metadata_utils.dart';
import '../utils/common_utils.dart';
import 'download_queue_service.dart';
import 'download_progress_service.dart';
import 'foreground_task_service.dart';
import 'artwork_manager.dart';

final _databaseService = DatabaseService.instance;
final _notificationService = NotificationService.instance;
final _observabilityService = ObservabilityService.instance;
final _chaquoDownloadService = ChaquoDownloadService.instance;
final _storageService = StorageService.instance;
final _artworkCacheService = ArtworkCacheService.instance;
final _downloadQueueService = DownloadQueueService.instance;
final _downloadProgressService = DownloadProgressService.instance;

class MetadataBatchRepairResult {
  const MetadataBatchRepairResult({
    required this.totalCandidates,
    required this.repairedCount,
    required this.failedCount,
    required this.skippedCount,
  });

  final int totalCandidates;
  final int repairedCount;
  final int failedCount;
  final int skippedCount;
}

class ArtworkBatchApplyResult {
  const ArtworkBatchApplyResult({
    required this.totalCandidates,
    required this.updatedCount,
    required this.failedCount,
    required this.skippedCount,
  });

  final int totalCandidates;
  final int updatedCount;
  final int failedCount;
  final int skippedCount;
}

typedef RewriteMetadataFunction = Future<Map<String, dynamic>> Function({
  required String filePath,
  required String title,
  String? artist,
  String? album,
  String? artworkUrl,
});

typedef DownloadVideoFunction = Future<Map<String, dynamic>> Function({
  required String url,
  required String outputPath,
  required String type,
  required String format,
  required String quality,
  String? artist,
  String? album,
  String? artworkUrl,
});

typedef ExportToPublicCollectionFunction = Future<ExportResult> Function({
  required String sourcePath,
  required DownloadType type,
  String? displayName,
  bool allowUserInteractionFallback,
});

typedef GetSandboxDownloadsDirectoryFunction = Future<Directory> Function(
    DownloadType type);
typedef ShowDownloadStartedFunction = void Function(String id, String title);
typedef ShowDownloadCompletedFunction = void Function(String id, String title);
typedef ShowDownloadFailedFunction = void Function(
  String id,
  String title,
  String error,
);

typedef GetArtistImageFunction = Future<String?> Function(String artist);
typedef GetAlbumCoverFunction = Future<String?> Function(
    String artist, String album);
typedef GetTrackCoverFunction = Future<String?> Function(
    String artist, String title);

class ArtworkLookupCache {
  ArtworkLookupCache();

  final Map<String, String?> cache = {};
  final Map<String, Future<String?>> inFlight = {};

  Future<String?> resolve(
    String key,
    Future<String?> Function() loader,
  ) async {
    if (cache.containsKey(key)) {
      return cache[key];
    }

    final existing = inFlight[key];
    if (existing != null) {
      return existing;
    }

    final task = loader();
    inFlight[key] = task;
    try {
      final value = await task;
      cache[key] = value;
      return value;
    } finally {
      if (inFlight[key] == task) {
        inFlight.remove(key);
      }
    }
  }

  void clear() {
    cache.clear();
    inFlight.clear();
  }
}

class DownloadService implements DownloadFeedService {
  DownloadService._();
  static final instance = DownloadService._();
  static const _uuidGenerator = Uuid();
  static const String _artworkKeySeparator = '|';

  final _updateController = StreamController<DownloadItem?>.broadcast();
  @override
  Stream<DownloadItem?> updates() => _updateController.stream;

  final ArtworkManager _artworkManager = ArtworkManager();
  final ArtworkLookupCache _artistArtworkLookup = ArtworkLookupCache();
  final ArtworkLookupCache _albumArtworkLookup = ArtworkLookupCache();
  final ArtworkLookupCache _trackArtworkLookup = ArtworkLookupCache();

  DownloadVideoFunction? _downloadVideoOverride;
  RewriteMetadataFunction? _rewriteMetadataOverride;
  ExportToPublicCollectionFunction? _exportToPublicCollectionOverride;
  GetSandboxDownloadsDirectoryFunction? _getSandboxDownloadsDirOverride;
  ShowDownloadStartedFunction? _showDownloadStartedOverride;
  ShowDownloadCompletedFunction? _showDownloadCompletedOverride;
  ShowDownloadFailedFunction? _showDownloadFailedOverride;
  GetArtistImageFunction? _getArtistImageOverride;
  GetAlbumCoverFunction? _getAlbumCoverOverride;
  GetTrackCoverFunction? _getTrackCoverOverride;

  /// Executa uma ação de I/O de maneira serial (bloqueada) por ID
  Future<T> _withLock<T>(String id, Future<T> Function() action) {
    return _downloadQueueService.withLock(id, action);
  }

  void _clearArtworkLookupCaches({bool includeArtworkService = false}) {
    _artistArtworkLookup.clear();
    _albumArtworkLookup.clear();
    _trackArtworkLookup.clear();
    _artworkManager.clear(includeService: includeArtworkService);
  }

  /// Libera recursos do serviço
  void dispose() {
    _artworkManager.clear(includeService: true);
    _downloadProgressService.dispose();
  }

  @visibleForTesting
  void configureTestOverrides({
    DownloadVideoFunction? downloadVideo,
    RewriteMetadataFunction? rewriteMetadata,
    ExportToPublicCollectionFunction? exportToPublicCollection,
    GetSandboxDownloadsDirectoryFunction? getSandboxDownloadsDir,
    ShowDownloadStartedFunction? showDownloadStarted,
    ShowDownloadCompletedFunction? showDownloadCompleted,
    ShowDownloadFailedFunction? showDownloadFailed,
    GetArtistImageFunction? getArtistImage,
    GetAlbumCoverFunction? getAlbumCover,
    GetTrackCoverFunction? getTrackCover,
  }) {
    _downloadVideoOverride = downloadVideo;
    _rewriteMetadataOverride = rewriteMetadata;
    _exportToPublicCollectionOverride = exportToPublicCollection;
    _getSandboxDownloadsDirOverride = getSandboxDownloadsDir;
    _showDownloadStartedOverride = showDownloadStarted;
    _showDownloadCompletedOverride = showDownloadCompleted;
    _showDownloadFailedOverride = showDownloadFailed;
    _getArtistImageOverride = getArtistImage;
    _getAlbumCoverOverride = getAlbumCover;
    _getTrackCoverOverride = getTrackCover;
  }

  @visibleForTesting
  void resetTestOverrides() {
    _downloadVideoOverride = null;
    _rewriteMetadataOverride = null;
    _exportToPublicCollectionOverride = null;
    _getSandboxDownloadsDirOverride = null;
    _showDownloadStartedOverride = null;
    _showDownloadCompletedOverride = null;
    _showDownloadFailedOverride = null;
    _getArtistImageOverride = null;
    _getAlbumCoverOverride = null;
    _getTrackCoverOverride = null;
    _artworkCacheService.resetTestOverrides();
    _clearArtworkLookupCaches(includeArtworkService: true);
  }

  /// Obtém metadados do vídeo usando Chaquo Python + yt-dlp
  Future<Map<String, dynamic>> fetchVideoInfo(String url) async {
    _observabilityService.info(
      'fetch_video_info_started',
      context: {'url': url},
    );

    try {
      final info = await _chaquoDownloadService.fetchVideoInfo(url);
      _observabilityService.info(
        'fetch_video_info_succeeded',
        context: {
          'url': url,
          'title': info['title'] as Object?,
        },
      );
      return info;
    } catch (e) {
      final simplified = _simplifyError(e.toString());
      _observabilityService.error(
        'fetch_video_info_failed',
        context: {
          'url': url,
          'error': simplified,
        },
      );
      throw Exception(simplified);
    }
  }

  String _sanitizeFileName(String input) {
    return input.replaceAll(RegExp(r'[<>:"/\\|?*]'), '_');
  }

  String _buildOutputPath(
    Directory downloadsDir,
    String title,
    String id,
    String extension,
  ) {
    final safeTitle = _sanitizeFileName(title);
    return '${downloadsDir.path}/${safeTitle}_$id.$extension';
  }

  /// Inicia o download
  Future<void> startDownload({
    required String url,
    required String title,
    String? thumbnail,
    required DownloadType type,
    required String format,
    required String quality,
    bool isPlaylist = false,
    List<dynamic>? entries,
    String? artist,
    String? album,
  }) async {
    if (isPlaylist && entries != null && entries.isNotEmpty) {
      _startPlaylistDownload(
          entries: entries,
          playlistTitle: title,
          type: type,
          format: format,
          quality: quality);
      return;
    }

    final String id = _uuidGenerator.v4();
    final downloadsDir = await _getDownloadsDir(type);
    final extension = type == DownloadType.audio ? format : 'mp4';
    final outputPath = _buildOutputPath(downloadsDir, title, id, extension);

    final item = DownloadItem(
      id: id,
      url: url,
      title: title,
      thumbnail: thumbnail,
      type: type,
      format: format,
      quality: quality,
      outputPath: outputPath,
      status: DownloadStatus.queued,
      artist: artist,
      album: album,
    );

    await _prepareAndStartDownload(item);
  }

  void _startPlaylistDownload({
    required List<dynamic> entries,
    required String playlistTitle,
    required DownloadType type,
    required String format,
    required String quality,
  }) {
    unawaited(_downloadPlaylist(
      entries: entries,
      playlistTitle: playlistTitle,
      type: type,
      format: format,
      quality: quality,
    ).catchError((error) {
      _observabilityService.error('playlist_download_failed',
          context: {'title': playlistTitle, 'error': error.toString()});
      _notificationService.showDownloadFailed(
          'playlist_error', 'Playlist: $playlistTitle', error.toString());
    }));
  }

  Future<void> _downloadPlaylist({
    required List<dynamic> entries,
    required String playlistTitle,
    required DownloadType type,
    required String format,
    required String quality,
  }) async {
    final String safePlaylistTitle = _sanitizeFileName(playlistTitle);
    final downloadsDir = await _getDownloadsDir(type);
    final playlistPath = '${downloadsDir.path}/$safePlaylistTitle';

    final directory = Directory(playlistPath);
    if (!await directory.exists()) {
      await directory.create(recursive: true);
    }

    for (var entry in entries) {
      final entryTitle = entry['title'] as String? ?? 'Sem título';
      final entryUrl = entry['url'] as String? ?? '';
      if (entryUrl.isEmpty) continue;

      final entryArtist = entry['artist'] as String?;
      final entryAlbum = entry['album'] as String?;

      final entryId = _uuidGenerator.v4();
      final extension = type == DownloadType.audio ? format : 'mp4';
      final outputPath = _buildOutputPath(
        directory,
        entryTitle,
        entryId,
        extension,
      );

      final item = DownloadItem(
        id: entryId,
        url: entryUrl,
        title: entryTitle,
        thumbnail: entry['thumbnail'] as String?,
        type: type,
        format: format,
        quality: quality,
        outputPath: outputPath,
        status: DownloadStatus.queued,
        artist: entryArtist,
        album: entryAlbum,
      );

      unawaited(_prepareAndStartDownload(item));
      await Future.delayed(const Duration(milliseconds: 200));
    }
  }

  Future<void> _prepareAndStartDownload(DownloadItem item) async {
    var workingItem = _applyMetadataFallbacks(item);
    workingItem = await _attachArtwork(workingItem);

    await _databaseService.insertDownload(workingItem);
    _downloadProgressService.addUpdate(workingItem);
    _showDownloadStarted(
      workingItem.id,
      workingItem.title,
    );

    _observabilityService.info(
      'download_started',
      context: {
        'id': workingItem.id,
        'title': workingItem.title,
        'type': workingItem.type.name,
        'format': workingItem.format,
        'quality': workingItem.quality,
      },
    );

    // Atualiza para 'baixando' ANTES de iniciar a task pesada
    final appForegroundService = AppForegroundService.instance;
    appForegroundService
        .updateCount(_downloadQueueService.totalActiveTasks + 1);

    await _downloadQueueService.add(() => _executeDownload(workingItem));

    // Ao final, atualiza de novo
    appForegroundService.updateCount(_downloadQueueService.totalActiveTasks);
  }

  Future<void> _executeDownload(DownloadItem item) async {
    await _withLock(item.id, () async {
      var workingItem = item;
      try {
        await _initializeDownloadProgress(workingItem);
        final result = await _downloadVideo(
          url: workingItem.url,
          outputPath: workingItem.outputPath,
          type: workingItem.type == DownloadType.audio ? 'audio' : 'video',
          format: workingItem.format,
          quality: workingItem.quality,
          artist: workingItem.artist,
          album: workingItem.album,
          artworkUrl: _artworkManager.preferredEmbeddedArtworkUrl(workingItem),
        );

        if (result['success'] == true) {
          workingItem = await _handleSuccessfulDownload(workingItem, result);
          return;
        }

        workingItem = await _handleFailedDownload(workingItem, result);
      } catch (error) {
        workingItem = await _handleDownloadException(workingItem, error);
      } finally {
        await _finalizeDownload(workingItem);
      }
    });
  }

  Future<void> _initializeDownloadProgress(DownloadItem workingItem) async {
    workingItem = workingItem.copyWith(status: DownloadStatus.downloading);
    _downloadProgressService.addUpdate(workingItem);
  }

  Future<DownloadItem> _handleSuccessfulDownload(
      DownloadItem workingItem, Map<String, dynamic> result) async {
    workingItem = await _processDownloadResult(workingItem, result);
    final file = File(workingItem.outputPath);
    final fileExists = await file.exists();

    if (!fileExists) {
      return await _handleMissingFileAfterDownload(workingItem);
    }

    workingItem = await _attachArtwork(workingItem);
    workingItem = workingItem.copyWith(
      status: DownloadStatus.completed,
      progress: 1.0,
      exportStatus: ExportStatus.pending,
      fileSizeBytes: await file.length(),
    );

    workingItem = await _applyAutoExportPolicy(workingItem);

    _showDownloadCompleted(
      workingItem.id,
      workingItem.title,
    );
    _observabilityService.info(
      'download_completed',
      context: {
        'id': workingItem.id,
        'title': workingItem.title,
        'fileSizeBytes': workingItem.fileSizeBytes,
        'outputPath': workingItem.outputPath,
        'tagsInjected': result['tags_injected'] == true,
      },
    );
    return workingItem;
  }

  Future<DownloadItem> _processDownloadResult(
      DownloadItem workingItem, Map<String, dynamic> result) async {
    workingItem = await _updateOutputPathIfChanged(workingItem, result);
    workingItem = _applyMetadataFallbacks(
      workingItem,
      detectedTitle: result['detected_title']?.toString(),
      detectedArtist: result['detected_artist']?.toString(),
      detectedAlbum: result['detected_album']?.toString(),
    );
    workingItem = await _resolveAndUpdateOutputPath(workingItem);
    return workingItem;
  }

  Future<DownloadItem> _updateOutputPathIfChanged(
      DownloadItem workingItem, Map<String, dynamic> result) async {
    final pythonOutputPath = result['filename']?.toString();
    if (pythonOutputPath != null &&
        pythonOutputPath.isNotEmpty &&
        pythonOutputPath != workingItem.outputPath) {
      final ext = _extractExtension(pythonOutputPath);
      workingItem = workingItem.copyWith(
        outputPath: pythonOutputPath,
        format: ext ?? workingItem.format,
      );
    }
    return workingItem;
  }

  Future<DownloadItem> _resolveAndUpdateOutputPath(
      DownloadItem workingItem) async {
    final resolvedPath =
        await _resolveOutputPathAfterDownload(workingItem.outputPath);
    if (resolvedPath != null && resolvedPath != workingItem.outputPath) {
      final ext = _extractExtension(resolvedPath);
      workingItem = workingItem.copyWith(
        outputPath: resolvedPath,
        format: ext ?? workingItem.format,
      );
    }
    return workingItem;
  }

  Future<DownloadItem> _handleMissingFileAfterDownload(
      DownloadItem workingItem) async {
    workingItem = workingItem.copyWith(
      status: DownloadStatus.failed,
      errorMessage: 'Arquivo não encontrado após concluir o download',
    );
    _showDownloadFailed(
      workingItem.id,
      workingItem.title,
      workingItem.errorMessage!,
    );
    _observabilityService.trackDownloadFailure(
      downloadId: workingItem.id,
      title: workingItem.title,
      source: 'download_file_missing_after_success',
      errorMessage: workingItem.errorMessage!,
    );
    return workingItem;
  }

  Future<DownloadItem> _handleFailedDownload(
      DownloadItem workingItem, Map<String, dynamic> result) async {
    workingItem = workingItem.copyWith(
      status: DownloadStatus.failed,
      errorMessage: _simplifyError(
        result['error']?.toString() ?? 'Falha no download',
      ),
    );
    _showDownloadFailed(
      workingItem.id,
      workingItem.title,
      workingItem.errorMessage!,
    );
    _observabilityService.trackDownloadFailure(
      downloadId: workingItem.id,
      title: workingItem.title,
      source: 'chaquo_download_result_false',
      errorMessage: workingItem.errorMessage!,
    );
    return workingItem;
  }

  Future<DownloadItem> _handleDownloadException(
      DownloadItem workingItem, Object e) async {
    workingItem = workingItem.copyWith(
      status: DownloadStatus.failed,
      errorMessage: _simplifyError(e.toString()),
    );
    _showDownloadFailed(
      workingItem.id,
      workingItem.title,
      workingItem.errorMessage!,
    );
    _observabilityService.trackDownloadFailure(
      downloadId: workingItem.id,
      title: workingItem.title,
      source: 'download_exception',
      errorMessage: workingItem.errorMessage!,
    );
    return workingItem;
  }

  Future<void> _finalizeDownload(DownloadItem workingItem) async {
    await _databaseService.updateDownload(workingItem);
    _downloadProgressService.addUpdate(workingItem);
  }

  DownloadItem _applyMetadataFallbacks(
    DownloadItem item, {
    String? detectedTitle,
    String? detectedArtist,
    String? detectedAlbum,
  }) {
    var updated = item;

    final normalizedCurrentTitle =
        MetadataUtils.normalizeMetadataText(updated.title);
    final normalizedDetectedTitle =
        _firstKnownMetadataValue(<String?>[detectedTitle]);

    if (_shouldReplaceTitle(updated, normalizedCurrentTitle)) {
      final replacementTitle = _guessTitleForMetadata(
        updated,
        normalizedDetectedTitle,
      );
      if (replacementTitle != null) {
        updated = updated.copyWith(title: replacementTitle);
      }
    }

    final normalizedKnownTitle =
        _firstKnownMetadataValue(<String?>[updated.title]);

    final normalizedArtist = _resolveArtistMetadata(
      originalItem: item,
      updatedItem: updated,
      detectedArtist: detectedArtist,
      normalizedKnownTitle: normalizedKnownTitle,
    );

    final normalizedAlbum = _resolveAlbumMetadata(
      updatedItem: updated,
      detectedAlbum: detectedAlbum,
    );

    return updated.copyWith(
      artist: normalizedArtist,
      album: normalizedAlbum,
    );
  }

  bool _shouldReplaceTitle(DownloadItem item, String normalizedCurrentTitle) {
    return MetadataUtils.isUnknownAppMetadata(item.title) ||
        normalizedCurrentTitle.toLowerCase() == 'videoplayback';
  }

  String? _guessTitleForMetadata(
    DownloadItem item,
    String? normalizedDetectedTitle,
  ) {
    final guessedTitleFromPath =
        MetadataUtils.guessAppTitleFromPath(item.outputPath);

    return _firstKnownMetadataValue(
      <String?>[
        normalizedDetectedTitle,
        guessedTitleFromPath,
      ],
    );
  }

  String _resolveArtistMetadata({
    required DownloadItem originalItem,
    required DownloadItem updatedItem,
    String? detectedArtist,
    String? normalizedKnownTitle,
  }) {
    final bool wasArtistExplicitlySet = originalItem.artist != null &&
        originalItem.artist!.isNotEmpty &&
        !MetadataUtils.isUnknownAppMetadata(originalItem.artist);

    if (wasArtistExplicitlySet) {
      return MetadataUtils.normalizeMetadataText(originalItem.artist!);
    }

    final detectedOrGuessed = _firstKnownMetadataValue(
      <String?>[
        updatedItem.artist,
        detectedArtist,
        MetadataUtils.guessAppArtistFromTitle(updatedItem.title),
        normalizedKnownTitle,
      ],
    );

    var normalizedArtist =
        detectedOrGuessed ?? normalizedKnownTitle ?? (updatedItem.artist ?? '');

    if (MetadataUtils.isUnknownAppMetadata(normalizedArtist) ||
        normalizedArtist.trim().isEmpty) {
      normalizedArtist = normalizedKnownTitle ?? 'YTDown';
    }

    return normalizedArtist;
  }

  String _resolveAlbumMetadata({
    required DownloadItem updatedItem,
    String? detectedAlbum,
  }) {
    return _firstKnownMetadataValue(
          <String?>[
            updatedItem.album,
            detectedAlbum,
          ],
        ) ??
        'YTDown';
  }

  String? _firstKnownMetadataValue(Iterable<String?> candidates) {
    for (final candidate in candidates) {
      final trimmed = candidate?.trim();
      if (trimmed == null || trimmed.isEmpty) {
        continue;
      }

      if (MetadataUtils.isUnknownAppMetadata(trimmed)) {
        continue;
      }

      final normalized = MetadataUtils.normalizeMetadataText(trimmed);
      if (normalized.isEmpty) {
        continue;
      }

      if (!MetadataUtils.isUnknownAppMetadata(normalized)) {
        return normalized;
      }
    }

    return null;
  }

  Future<DownloadItem> _attachArtwork(
    DownloadItem item, {
    bool forceRefresh = false,
  }) async {
    if (item.type != DownloadType.audio) {
      return item;
    }

    if (!forceRefresh && MetadataUtils.isUnknownAppMetadata(item.artist)) {
      return item;
    }

    if (!forceRefresh &&
        _hasArtwork(item.artistImageUrl) &&
        _hasArtwork(item.albumImageUrl)) {
      return item;
    }

    try {
      final artist = item.artist?.trim() ?? '';
      final album = (item.album ?? 'YTDown').trim();
      final canLookupByArtist =
          artist.isNotEmpty && !MetadataUtils.isUnknownAppMetadata(artist);
      final needsArtist = forceRefresh || !_hasArtwork(item.artistImageUrl);
      final needsAlbum = forceRefresh || !_hasArtwork(item.albumImageUrl);

      var artistImg = item.artistImageUrl;
      if (needsArtist && canLookupByArtist) {
        artistImg = await _getArtistImageCached(artist);
      }

      var albumImg = item.albumImageUrl;
      if (needsAlbum && canLookupByArtist) {
        albumImg = await _getAlbumCoverCached(artist, album);
        if (!_hasArtwork(albumImg) &&
            !MetadataUtils.isUnknownAppMetadata(item.title)) {
          albumImg = await _getTrackCoverCached(artist, item.title);
        }
      }

      return item.copyWith(
        artistImageUrl: artistImg,
        albumImageUrl: albumImg,
      );
    } catch (e, stackTrace) {
      // Keep flow resilient: artwork is optional.
      _observabilityService.warning(
        'artwork_lookup_exception',
        context: {
          'id': item.id,
          'title': item.title,
          'artist': item.artist,
          'album': item.album,
          'forceRefresh': forceRefresh,
          'error': e.toString(),
          'stack': stackTrace.toString(),
        },
      );
    }

    return item;
  }

  String? _extractExtension(String path) {
    final lastDot = path.lastIndexOf('.');
    if (lastDot < 0 || lastDot == path.length - 1) return null;
    return path.substring(lastDot + 1).toLowerCase();
  }

  Future<String?> _resolveOutputPathAfterDownload(String expectedPath) async {
    final expectedFile = File(expectedPath);
    if (await expectedFile.exists()) return expectedPath;

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

  Future<Directory> _getDownloadsDir(DownloadType type) async {
    final override = _getSandboxDownloadsDirOverride;
    if (override != null) return override(type);
    return _storageService.getSandboxDownloadsDir(type);
  }

  Future<Map<String, dynamic>> rewriteDownloadMetadata({
    required String downloadId,
    required String title,
    String? artist,
    String? album,
    String? artistImageUrl,
    String? albumImageUrl,
  }) async {
    return _withLock(downloadId, () async {
      final allDownloads = await _databaseService.getAllDownloads();
      DownloadItem? item;
      for (final candidate in allDownloads) {
        if (candidate.id == downloadId) {
          item = candidate;
          break;
        }
      }

      if (item == null) {
        return <String, dynamic>{
          'success': false,
          'error': 'Download não encontrado para edição de metadados',
          'stage': 'load_download',
          'retryable': false,
        };
      }

      if (item.type != DownloadType.audio) {
        return <String, dynamic>{
          'success': false,
          'error': 'Edição manual de metadados disponível apenas para áudio',
          'stage': 'validate_download_type',
          'retryable': false,
        };
      }

      if (item.status != DownloadStatus.completed) {
        return <String, dynamic>{
          'success': false,
          'error': 'Somente downloads concluídos podem ser editados',
          'stage': 'validate_download_status',
          'retryable': false,
        };
      }

      var workingItem = item;

      final resolvedPath =
          await _resolveOutputPathAfterDownload(workingItem.outputPath);
      if (resolvedPath == null) {
        return <String, dynamic>{
          'success': false,
          'error': 'Arquivo não encontrado para regravar metadados',
          'stage': 'resolve_output_path',
          'retryable': false,
        };
      }

      if (resolvedPath != workingItem.outputPath) {
        final ext = _extractExtension(resolvedPath);
        workingItem = workingItem.copyWith(
          outputPath: resolvedPath,
          format: ext ?? workingItem.format,
        );
      }

      final file = File(workingItem.outputPath);
      if (!await file.exists()) {
        return <String, dynamic>{
          'success': false,
          'error': 'Arquivo não encontrado para regravar metadados',
          'stage': 'file_exists_check',
          'retryable': false,
        };
      }

      final normalizedTitle = MetadataUtils.normalizeMetadataText(title);
      final normalizedArtistInput = artist?.trim();
      final normalizedAlbumInput = album?.trim();
      final normalizedArtistImageInput = artistImageUrl?.trim();
      final normalizedAlbumImageInput = albumImageUrl?.trim();

      String? preparedArtistImageInput;
      if (normalizedArtistImageInput != null &&
          normalizedArtistImageInput.isNotEmpty) {
        preparedArtistImageInput =
            await _prepareArtworkSource(normalizedArtistImageInput);
        if (preparedArtistImageInput == null) {
          return <String, dynamic>{
            'success': false,
            'error':
                'Imagem do artista invalida. Selecione uma foto da galeria ou use URL http/https.',
            'stage': 'validate_artist_image_input',
            'retryable': false,
          };
        }
      }

      String? preparedAlbumImageInput;
      if (normalizedAlbumImageInput != null &&
          normalizedAlbumImageInput.isNotEmpty) {
        preparedAlbumImageInput =
            await _prepareArtworkSource(normalizedAlbumImageInput);
        if (preparedAlbumImageInput == null) {
          return <String, dynamic>{
            'success': false,
            'error':
                'Imagem do album invalida. Selecione uma foto da galeria ou use URL http/https.',
            'stage': 'validate_album_image_input',
            'retryable': false,
          };
        }
      }

      workingItem = workingItem.copyWith(
        title: normalizedTitle.isEmpty ? workingItem.title : normalizedTitle,
        artist: (normalizedArtistInput == null || normalizedArtistInput.isEmpty)
            ? workingItem.artist
            : MetadataUtils.normalizeMetadataText(normalizedArtistInput),
        album: (normalizedAlbumInput == null || normalizedAlbumInput.isEmpty)
            ? workingItem.album
            : MetadataUtils.normalizeMetadataText(normalizedAlbumInput),
        artistImageUrl: (preparedArtistImageInput == null ||
                preparedArtistImageInput.isEmpty)
            ? workingItem.artistImageUrl
            : preparedArtistImageInput,
        albumImageUrl:
            (preparedAlbumImageInput == null || preparedAlbumImageInput.isEmpty)
                ? workingItem.albumImageUrl
                : preparedAlbumImageInput,
      );

      workingItem = _applyMetadataFallbacks(workingItem);
      workingItem = await _attachArtwork(workingItem);

      final manualAlbumArtwork = (preparedAlbumImageInput != null &&
              preparedAlbumImageInput.isNotEmpty)
          ? preparedAlbumImageInput
          : null;
      final manualArtistArtwork = (preparedArtistImageInput != null &&
              preparedArtistImageInput.isNotEmpty)
          ? preparedArtistImageInput
          : null;
      final artworkToEmbed = manualAlbumArtwork ??
          manualArtistArtwork ??
          _artworkManager.preferredEmbeddedArtworkUrl(workingItem);

      final rewriteResult = await _rewriteMetadata(
        filePath: workingItem.outputPath,
        title: workingItem.title,
        artist: workingItem.artist,
        album: workingItem.album,
        artworkUrl: artworkToEmbed,
      );

      if (rewriteResult['success'] != true) {
        final error = _simplifyError(
          rewriteResult['error']?.toString() ??
              'Falha ao regravar metadados manualmente',
        );
        _observabilityService.warning(
          'manual_metadata_rewrite_failed',
          context: {
            'id': workingItem.id,
            'title': workingItem.title,
            'path': workingItem.outputPath,
            'error': error,
          },
        );
        return <String, dynamic>{
          'success': false,
          'error': error,
          'stage': rewriteResult['stage']?.toString() ?? 'rewrite_metadata',
          'retryable': rewriteResult['retryable'] == true,
        };
      }

      // Preservar valores originais antes do fallback
      final originalArtist = workingItem.artist;

      workingItem = _applyMetadataFallbacks(
        workingItem,
        detectedTitle: rewriteResult['title']?.toString(),
        detectedArtist: rewriteResult['artist']?.toString(),
        detectedAlbum: rewriteResult['album']?.toString(),
      );

      // Se o Python retornou "Desconhecido" mas o usuário havia fornecido um valor,
      // restaurar o valor fornecido pelo usuário
      if (MetadataUtils.isUnknownAppMetadata(workingItem.artist) &&
          originalArtist != null &&
          originalArtist.isNotEmpty &&
          !MetadataUtils.isUnknownAppMetadata(originalArtist)) {
        workingItem = workingItem.copyWith(artist: originalArtist);
      }

      if (workingItem.fileSizeBytes == null) {
        workingItem = workingItem.copyWith(fileSizeBytes: await file.length());
      }

      String? syncWarning;
      final exportedPath = workingItem.exportedPath?.trim();
      if (workingItem.exportStatus == ExportStatus.exported) {
        final storageService = _storageService;
        final observability = _observabilityService;

        if (exportedPath == null || exportedPath.isEmpty) {
          syncWarning =
              'Metadados salvos no app, mas nao foi possivel sincronizar o arquivo exportado.';
        }

        if (exportedPath != null && exportedPath.isNotEmpty) {
          final syncResult = await storageService.syncEditedFileToExported(
            sourcePath: workingItem.outputPath,
            exportedPath: exportedPath,
          );

          if (syncResult.success) {
            observability.info(
              'manual_metadata_export_sync_succeeded',
              context: {
                'id': workingItem.id,
                'exportedPath': exportedPath,
                'strategy': syncResult.strategy,
                'stage': syncResult.stage,
              },
            );
          }

          if (!syncResult.success) {
            final syncError = _simplifyError(
              syncResult.error?.toString() ??
                  'Falha ao sincronizar arquivo exportado',
            );
            syncWarning =
                'Metadados salvos no app, mas a copia exportada pode estar desatualizada: $syncError';
            observability.warning(
              'manual_metadata_export_sync_failed',
              context: {
                'id': workingItem.id,
                'exportedPath': exportedPath,
                'error': syncError,
                'strategy': syncResult.strategy,
                'stage': syncResult.stage,
              },
            );
          }
        }
      }

      await _databaseService.updateDownload(workingItem);
      _downloadProgressService.addUpdate(workingItem);

      _observabilityService.info(
        'manual_metadata_rewrite_succeeded',
        context: {
          'id': workingItem.id,
          'title': workingItem.title,
          'artist': workingItem.artist,
          'album': workingItem.album,
          'artistImageUrl': workingItem.artistImageUrl,
          'albumImageUrl': workingItem.albumImageUrl,
        },
      );

      return <String, dynamic>{
        'success': true,
        'id': workingItem.id,
        'title': workingItem.title,
        'artist': workingItem.artist,
        'album': workingItem.album,
        'artistImageUrl': workingItem.artistImageUrl,
        'albumImageUrl': workingItem.albumImageUrl,
        if (syncWarning != null) 'warning': syncWarning,
      };
    });
  }

  bool _isSupportedArtworkUrl(String input) {
    final uri = Uri.tryParse(input);
    if (uri == null || !uri.hasScheme || uri.host.isEmpty) {
      return false;
    }

    final scheme = uri.scheme.toLowerCase();
    return scheme == 'http' || scheme == 'https';
  }

  bool _isFileUri(String input) {
    final uri = Uri.tryParse(input);
    return uri != null && uri.scheme.toLowerCase() == 'file';
  }

  String? _extractLocalPathFromArtworkInput(String input) {
    final trimmed = input.trim();
    if (trimmed.isEmpty) return null;

    if (_isFileUri(trimmed)) {
      final uri = Uri.tryParse(trimmed);
      if (uri == null) return null;
      try {
        return uri.toFilePath();
      } catch (_) {
        return null;
      }
    }

    if (trimmed.startsWith('/')) {
      return trimmed;
    }

    return null;
  }

  Future<Directory> _getManualArtworkDirectory() async {
    final docsDir = await getApplicationDocumentsDirectory();
    final dir = Directory(path.join(docsDir.path, 'manual_artwork'));
    if (!await dir.exists()) {
      await dir.create(recursive: true);
    }
    return dir;
  }

  Future<String> _persistManualArtworkFile(
    String sourcePath,
    Directory targetDir,
  ) async {
    final sourceFile = File(sourcePath);
    final normalizedSource = path.normalize(sourceFile.absolute.path);
    final normalizedTargetDir = path.normalize(targetDir.absolute.path);

    if (path.isWithin(normalizedTargetDir, normalizedSource)) {
      return normalizedSource;
    }

    final ext = path.extension(sourcePath).trim().toLowerCase();
    const allowedExts = <String>{'.jpg', '.jpeg', '.png', '.webp', '.bmp'};
    final safeExt = allowedExts.contains(ext) ? ext : '.jpg';

    final targetPath =
        path.join(targetDir.path, 'artwork_${_uuidGenerator.v4()}$safeExt');
    await sourceFile.copy(targetPath);
    return targetPath;
  }

  Future<String?> _prepareArtworkSource(String input) async {
    final trimmed = input.trim();
    if (trimmed.isEmpty) return null;

    if (_isSupportedArtworkUrl(trimmed)) {
      return trimmed;
    }

    final localPath = _extractLocalPathFromArtworkInput(trimmed);
    if (localPath == null) return null;

    final sourceFile = File(localPath);
    if (!await sourceFile.exists()) return null;

    final targetDir = await _getManualArtworkDirectory();
    final persistedPath = await _persistManualArtworkFile(localPath, targetDir);
    return Uri.file(persistedPath).toString();
  }

  Future<Map<String, dynamic>> rewriteArtistMetadataBatch({
    required String currentArtist,
    required String newArtist,
    String? newArtistImageUrl,
    void Function(int processed, int total)? onProgress,
  }) async {
    final normalizedCurrent = _normalizeArtistForBatch(currentArtist);
    final normalizedNew = MetadataUtils.normalizeMetadataText(newArtist).trim();
    final normalizedNewArtistImage = newArtistImageUrl?.trim();

    if (normalizedCurrent.isEmpty) {
      return <String, dynamic>{
        'success': false,
        'error': 'Artista atual inválido para edição em lote',
        'stage': 'validate_current_artist',
        'retryable': false,
      };
    }

    if (normalizedNew.isEmpty) {
      return <String, dynamic>{
        'success': false,
        'error': 'Novo artista não pode ficar vazio',
        'stage': 'validate_new_artist',
        'retryable': false,
      };
    }

    String? preparedNewArtistImage;
    if (normalizedNewArtistImage != null &&
        normalizedNewArtistImage.isNotEmpty) {
      preparedNewArtistImage =
          await _prepareArtworkSource(normalizedNewArtistImage);
      if (preparedNewArtistImage == null) {
        return <String, dynamic>{
          'success': false,
          'error':
              'Imagem da banda/artista invalida. Selecione uma foto da galeria ou use URL http/https.',
          'stage': 'validate_new_artist_image_input',
          'retryable': false,
        };
      }
    }

    final databaseService = _databaseService;
    final observability = _observabilityService;

    final allDownloads = await databaseService.getAllDownloads();
    final candidates = allDownloads.where(
      (item) {
        if (item.type != DownloadType.audio ||
            item.status != DownloadStatus.completed) {
          return false;
        }
        final itemArtistNorm = _normalizeArtistForBatch(item.artist);

        // Corrige o bug do banco de dados misturar null com "Desconhecido"
        if (normalizedCurrent == 'desconhecido' ||
            normalizedCurrent == 'artista desconhecido') {
          return itemArtistNorm.isEmpty ||
              itemArtistNorm == 'desconhecido' ||
              itemArtistNorm == 'artista desconhecido';
        }

        return itemArtistNorm == normalizedCurrent;
      },
    ).toList();

    observability.info(
      'manual_artist_batch_rewrite_started',
      context: {
        'currentArtist': currentArtist,
        'newArtist': normalizedNew,
        'newArtistImageUrl': preparedNewArtistImage,
        'candidates': candidates.length,
      },
    );

    if (candidates.isEmpty) {
      onProgress?.call(0, 0);
      return <String, dynamic>{
        'success': true,
        'totalCandidates': 0,
        'updatedCount': 0,
        'failedCount': 0,
        'skippedCount': 0,
        'appliedArtistImageUrl': preparedNewArtistImage,
      };
    }

    var processed = 0;
    var updated = 0;
    var failed = 0;
    var skipped = 0;
    var warningCount = 0;

    for (final item in candidates) {
      final result = await rewriteDownloadMetadata(
        downloadId: item.id,
        title: item.title,
        artist: normalizedNew,
        album: item.album,
        artistImageUrl: preparedNewArtistImage,
      );

      if (result['success'] == true) {
        updated++;
        if ((result['warning']?.toString().isNotEmpty ?? false)) {
          warningCount++;
        }
      }

      if (result['success'] != true) {
        failed++;
      }

      processed++;
      onProgress?.call(processed, candidates.length);
    }

    final updatedPaths = candidates
        .where((item) => item.status == DownloadStatus.completed)
        .map((item) => item.outputPath)
        .toList();
    await _rescanBatchPaths(
      updatedPaths,
      context: {
        'currentArtist': currentArtist,
        'newArtist': normalizedNew,
      },
    );

    _observabilityService.info(
      'manual_artist_batch_rewrite_finished',
      context: {
        'currentArtist': currentArtist,
        'newArtist': normalizedNew,
        'candidates': candidates.length,
        'updated': updated,
        'failed': failed,
        'skipped': skipped,
        'warnings': warningCount,
      },
    );

    return <String, dynamic>{
      'success': true,
      'totalCandidates': candidates.length,
      'updatedCount': updated,
      'failedCount': failed,
      'skippedCount': skipped,
      'warningCount': warningCount,
      'appliedArtistImageUrl': preparedNewArtistImage,
    };
  }

  Future<Map<String, dynamic>> rewriteAlbumMetadataBatch({
    required String currentAlbum,
    required String newAlbum,
    String? newAlbumImageUrl,
    void Function(int processed, int total)? onProgress,
  }) async {
    final normalizedCurrent = _normalizeAlbumForBatch(currentAlbum);
    final normalizedNew = MetadataUtils.normalizeMetadataText(newAlbum).trim();
    final normalizedNewAlbumImage = newAlbumImageUrl?.trim();

    if (normalizedCurrent.isEmpty) {
      return <String, dynamic>{
        'success': false,
        'error': 'Album atual inválido para edição em lote',
        'stage': 'validate_current_album',
        'retryable': false,
      };
    }

    if (normalizedNew.isEmpty) {
      return <String, dynamic>{
        'success': false,
        'error': 'Novo album não pode ficar vazio',
        'stage': 'validate_new_album',
        'retryable': false,
      };
    }

    String? preparedNewAlbumImage;
    if (normalizedNewAlbumImage != null && normalizedNewAlbumImage.isNotEmpty) {
      preparedNewAlbumImage =
          await _prepareArtworkSource(normalizedNewAlbumImage);
      if (preparedNewAlbumImage == null) {
        return <String, dynamic>{
          'success': false,
          'error':
              'Imagem do album invalida. Selecione uma foto da galeria ou use URL http/https.',
          'stage': 'validate_new_album_image_input',
          'retryable': false,
        };
      }
    }

    final databaseService = _databaseService;
    final observability = _observabilityService;

    final allDownloads = await databaseService.getAllDownloads();
    final candidates = allDownloads.where(
      (item) {
        if (item.type != DownloadType.audio ||
            item.status != DownloadStatus.completed) {
          return false;
        }

        return _normalizeForBatch(item.album) == normalizedCurrent;
      },
    ).toList();

    observability.info(
      'manual_album_batch_rewrite_started',
      context: {
        'currentAlbum': currentAlbum,
        'newAlbum': normalizedNew,
        'newAlbumImageUrl': preparedNewAlbumImage,
        'candidates': candidates.length,
      },
    );

    if (candidates.isEmpty) {
      onProgress?.call(0, 0);
      return <String, dynamic>{
        'success': true,
        'totalCandidates': 0,
        'updatedCount': 0,
        'failedCount': 0,
        'skippedCount': 0,
        'appliedAlbumImageUrl': preparedNewAlbumImage,
      };
    }

    var processed = 0;
    var updated = 0;
    var failed = 0;
    var skipped = 0;
    var warningCount = 0;

    for (final item in candidates) {
      final result = await rewriteDownloadMetadata(
        downloadId: item.id,
        title: item.title,
        artist: item.artist,
        album: normalizedNew,
        albumImageUrl: preparedNewAlbumImage,
      );

      if (result['success'] == true) {
        updated++;
        if ((result['warning']?.toString().isNotEmpty ?? false)) {
          warningCount++;
        }
      }

      if (result['success'] != true) {
        failed++;
      }

      processed++;
      onProgress?.call(processed, candidates.length);
    }

    final updatedPaths = candidates
        .where((item) => item.status == DownloadStatus.completed)
        .map((item) => item.outputPath)
        .toList();

    await _rescanBatchPaths(
      updatedPaths,
      context: {
        'currentAlbum': currentAlbum,
        'newAlbum': normalizedNew,
      },
    );

    _observabilityService.info(
      'manual_album_batch_rewrite_finished',
      context: {
        'currentAlbum': currentAlbum,
        'newAlbum': normalizedNew,
        'candidates': candidates.length,
        'updated': updated,
        'failed': failed,
        'skipped': skipped,
        'warnings': warningCount,
      },
    );

    return <String, dynamic>{
      'success': true,
      'totalCandidates': candidates.length,
      'updatedCount': updated,
      'failedCount': failed,
      'skippedCount': skipped,
      'warningCount': warningCount,
      'appliedAlbumImageUrl': preparedNewAlbumImage,
    };
  }

  Future<void> _rescanBatchPaths(
    List<String> paths, {
    Map<String, dynamic>? context,
  }) async {
    if (paths.isEmpty) return;

    final observability = _observabilityService;
    final chaquo = _chaquoDownloadService;

    try {
      observability.info(
        'batch_media_rescan_started',
        context: {
          'totalFiles': paths.length,
          ...?context,
        },
      );

      final rescanResult = await chaquo.batchRescanFiles(paths);

      final scanned = rescanResult['scanned'] ?? 0;
      final rescannedFailed = rescanResult['failed'] ?? 0;
      final rescannedTotal = rescanResult['total'] ?? 0;
      final timedOut = rescanResult['timeout'] ?? false;
      final durationMs = rescanResult['durationMs'] ?? 0;

      observability.info(
        'batch_media_rescan_completed',
        context: {
          'totalFiles': paths.length,
          'scanned': scanned,
          'failed': rescannedFailed,
          'timeout': timedOut,
          'durationMs': durationMs,
          'successRate': rescannedTotal > 0
              ? (scanned / rescannedTotal * 100).toStringAsFixed(1)
              : '0',
          ...?context,
        },
      );

      if (timedOut) {
        observability.warning(
          'batch_media_rescan_timeout',
          context: {
            'totalFiles': paths.length,
            'scanned': scanned,
            'failed': rescannedFailed,
            'durationMs': durationMs,
            ...?context,
          },
        );
      }
    } catch (e, stackTrace) {
      observability.warning(
        'batch_media_rescan_failed',
        context: {
          'error': e.toString(),
          'stackTrace': stackTrace.toString(),
          'totalFiles': paths.length,
          ...?context,
        },
      );
      observability.error(
        'batch_media_rescan_error',
        context: {
          'error': e.toString(),
          'totalFiles': paths.length,
          ...?context,
        },
      );
    }
  }

  Future<void> exportDownload(DownloadItem item) async {
    await _withLock(item.id, () async {
      var workingItem = item;
      if (workingItem.status != DownloadStatus.completed) return;

      workingItem = await _exportItem(
        workingItem,
        source: 'manual',
        allowUserInteractionFallback: true,
      );
      await _databaseService.updateDownload(workingItem);
      _downloadProgressService.addUpdate(workingItem);
    });

    final appForegroundService = AppForegroundService.instance;
    appForegroundService.updateCount(_downloadQueueService.totalActiveTasks);
  }

  Future<DownloadItem> _applyAutoExportPolicy(DownloadItem item) async {
    final shouldAutoExport = await _databaseService.getAutoExportEnabled();
    if (!shouldAutoExport) return item;

    return _exportItem(
      item,
      source: 'auto',
      allowUserInteractionFallback: false,
    );
  }

  @visibleForTesting
  Future<DownloadItem> applyAutoExportPolicyForTest(DownloadItem item) {
    return _applyAutoExportPolicy(item);
  }

  Future<DownloadItem> _exportItem(
    DownloadItem item, {
    required String source,
    required bool allowUserInteractionFallback,
  }) async {
    final previousStatus = item.exportStatus;
    final previousPath = item.exportedPath;

    final result = await _exportToPublicCollection(
      sourcePath: item.outputPath,
      type: item.type,
      allowUserInteractionFallback: allowUserInteractionFallback,
    );

    if (result.success) {
      final updatedItem = item.copyWith(
        exportStatus: ExportStatus.exported,
        exportedPath: result.exportedPath,
      );
      _observabilityService.info(
        'download_exported',
        context: {
          'id': updatedItem.id,
          'source': source,
          'exportedPath': result.exportedPath,
          'strategy': result.strategy,
          'stage': result.stage,
          'deviceManufacturer': result.diagnostics?['manufacturer'],
          'deviceModel': result.diagnostics?['model'],
          'sdkInt': result.diagnostics?['sdkInt'],
        },
      );
      return updatedItem;
    }

    DownloadItem updatedItem = item.copyWith(exportStatus: ExportStatus.failed);
    if (previousStatus == ExportStatus.exported && previousPath != null) {
      updatedItem = item.copyWith(
        exportStatus: previousStatus,
        exportedPath: previousPath,
      );
    }

    final diagnostics = result.diagnostics;
    final sdkRaw = diagnostics?['sdkInt'];
    final sdkInt = sdkRaw is int ? sdkRaw : int.tryParse('$sdkRaw');

    try {
      await _databaseService.insertExportFailureEvent(
        downloadId: updatedItem.id,
        title: updatedItem.title,
        source: source,
        stage: result.stage,
        strategy: result.strategy,
        manufacturer: diagnostics?['manufacturer']?.toString(),
        brand: diagnostics?['brand']?.toString(),
        model: diagnostics?['model']?.toString(),
        sdkInt: sdkInt,
        androidRelease: diagnostics?['androidRelease']?.toString(),
        mediaType: diagnostics?['mediaType']?.toString(),
        mimeType: diagnostics?['mimeType']?.toString(),
        errorMessage: result.error ?? 'Falha desconhecida na exportação',
      );
    } catch (e) {
      _observabilityService.warning(
        'download_export_failure_persist_failed',
        context: {
          'id': updatedItem.id,
          'source': source,
          'error': e.toString(),
        },
      );
    }

    _observabilityService.warning(
      'download_export_failed',
      context: {
        'id': updatedItem.id,
        'source': source,
        'error': result.error,
        'strategy': result.strategy,
        'stage': result.stage,
        'diagnostics': result.diagnostics,
      },
    );

    return updatedItem;
  }

  Future<void> deleteDownload(DownloadItem item) async {
    await _withLock(item.id, () async {
      await _databaseService.deleteDownload(item.id);

      // Apagar arquivo
      try {
        final file = File(item.outputPath);
        if (await file.exists()) {
          await file.delete();
        }
      } catch (e) {
        _observabilityService.warning(
          'delete_download_file_failed',
          context: {
            'id': item.id,
            'path': item.outputPath,
            'error': e.toString(),
          },
        );
      }

      // Apagar arquivo exportado publicamente (MediaStore/Downloads/Music) no Android
      if (item.exportedPath != null && item.exportedPath!.isNotEmpty) {
        try {
          await _storageService.deleteExportedFile(item.exportedPath!);
        } catch (e) {
          _observabilityService.warning(
            'delete_exported_file_failed',
            context: {
              'id': item.id,
              'exportedPath': item.exportedPath,
              'error': e.toString(),
            },
          );
        }
      }
    });
  }

  Future<MetadataBatchRepairResult> repairAudioMetadataBatch({
    void Function(int processed, int total)? onProgress,
  }) async {
    final allDownloads = await _databaseService.getAllDownloads();
    final audioCandidates = allDownloads
        .where((item) =>
            item.type == DownloadType.audio &&
            item.status == DownloadStatus.completed)
        .toList();

    if (audioCandidates.isEmpty) {
      onProgress?.call(0, 0);
      return const MetadataBatchRepairResult(
        totalCandidates: 0,
        repairedCount: 0,
        failedCount: 0,
        skippedCount: 0,
      );
    }

    var repaired = 0;
    var failed = 0;
    var skipped = 0;
    var processed = 0;

    for (final candidate in audioCandidates) {
      await _withLock(candidate.id, () async {
        var workingItem = candidate;
        try {
          if (workingItem.outputPath.trim().isEmpty) {
            skipped++;
            return;
          }

          final resolvedPath =
              await _resolveOutputPathAfterDownload(workingItem.outputPath);
          if (resolvedPath == null) {
            failed++;
            _observabilityService.warning(
              'metadata_batch_repair_missing_file',
              context: {
                'id': workingItem.id,
                'title': workingItem.title,
                'path': workingItem.outputPath,
              },
            );
            return;
          }

          if (resolvedPath != workingItem.outputPath) {
            final ext = _extractExtension(resolvedPath);
            workingItem = workingItem.copyWith(
              outputPath: resolvedPath,
              format: ext ?? workingItem.format,
            );
          }

          final file = File(workingItem.outputPath);
          if (!await file.exists()) {
            failed++;
            return;
          }

          workingItem = _applyMetadataFallbacks(
            workingItem,
            detectedTitle: _guessTitleFromPath(workingItem.outputPath),
          );
          workingItem = await _attachArtwork(workingItem);

          final rewriteResult = await _rewriteMetadata(
            filePath: workingItem.outputPath,
            title: workingItem.title,
            artist: workingItem.artist,
            album: workingItem.album,
            artworkUrl:
                _artworkManager.preferredEmbeddedArtworkUrl(workingItem),
          );

          final success = rewriteResult['success'] == true;
          if (!success) {
            failed++;
            _observabilityService.warning(
              'metadata_batch_repair_failed',
              context: {
                'id': workingItem.id,
                'title': workingItem.title,
                'path': workingItem.outputPath,
                'error': _simplifyError(
                  rewriteResult['error']?.toString() ??
                      'Falha ao regravar metadados',
                ),
              },
            );
            return;
          }

          workingItem = _applyMetadataFallbacks(
            workingItem,
            detectedTitle: rewriteResult['title']?.toString(),
            detectedArtist: rewriteResult['artist']?.toString(),
            detectedAlbum: rewriteResult['album']?.toString(),
          );
          workingItem = await _attachArtwork(workingItem);
          if (workingItem.fileSizeBytes == null) {
            workingItem =
                workingItem.copyWith(fileSizeBytes: await file.length());
          }

          await _databaseService.updateDownload(workingItem);
          _downloadProgressService.addUpdate(workingItem);
          repaired++;
        } catch (e) {
          failed++;
          _observabilityService.warning(
            'metadata_batch_repair_exception',
            context: {
              'id': workingItem.id,
              'title': workingItem.title,
              'path': workingItem.outputPath,
              'error': e.toString(),
            },
          );
        }
      });

      processed++;
      onProgress?.call(processed, audioCandidates.length);
    }

    _observabilityService.info(
      'metadata_batch_repair_finished',
      context: {
        'candidates': audioCandidates.length,
        'repaired': repaired,
        'failed': failed,
        'skipped': skipped,
      },
    );

    return MetadataBatchRepairResult(
      totalCandidates: audioCandidates.length,
      repairedCount: repaired,
      failedCount: failed,
      skippedCount: skipped,
    );
  }

  Future<ArtworkBatchApplyResult> addMissingArtworkBatch({
    void Function(int processed, int total)? onProgress,
  }) async {
    final allDownloads = await _databaseService.getAllDownloads();
    final candidates = allDownloads
        .where(
          (item) =>
              item.type == DownloadType.audio &&
              item.status == DownloadStatus.completed,
        )
        .toList();

    // Atualização em lote deve consultar capa mais recente, não só dados em cache.
    _clearArtworkLookupCaches(includeArtworkService: true);

    _observabilityService.info(
      'artwork_batch_apply_started',
      context: {'candidates': candidates.length},
    );

    if (candidates.isEmpty) {
      onProgress?.call(0, 0);
      return const ArtworkBatchApplyResult(
        totalCandidates: 0,
        updatedCount: 0,
        failedCount: 0,
        skippedCount: 0,
      );
    }

    var updated = 0;
    var failed = 0;
    var skipped = 0;
    var processed = 0;

    for (final candidate in candidates) {
      await _withLock(candidate.id, () async {
        var workingItem = candidate;
        try {
          workingItem = await _attachArtwork(workingItem, forceRefresh: true);

          final artworkUrl =
              _artworkManager.preferredEmbeddedArtworkUrl(workingItem);
          if (artworkUrl == null) {
            skipped++;
            return;
          }

          // O lote só conta como sucesso quando a capa for embutida no arquivo.
          final file = File(workingItem.outputPath);
          if (!await file.exists()) {
            failed++;
            _observabilityService.warning(
              'artwork_batch_apply_file_missing',
              context: {
                'id': workingItem.id,
                'title': workingItem.title,
                'path': workingItem.outputPath,
              },
            );
            return;
          }

          final rewriteResult = await _rewriteMetadata(
            filePath: workingItem.outputPath,
            title: workingItem.title,
            artist: workingItem.artist,
            album: workingItem.album,
            artworkUrl: artworkUrl,
          );

          if (rewriteResult['success'] != true) {
            failed++;
            _observabilityService.warning(
              'artwork_batch_apply_embed_failed',
              context: {
                'id': workingItem.id,
                'title': workingItem.title,
                'path': workingItem.outputPath,
                'error': _simplifyError(
                  rewriteResult['error']?.toString() ??
                      'Falha ao embutir capa no arquivo',
                ),
              },
            );
            return;
          }

          await _databaseService.updateDownload(workingItem);
          _downloadProgressService.addUpdate(workingItem);

          updated++;
        } catch (e) {
          _observabilityService.warning(
            'artwork_batch_apply_exception',
            context: {
              'id': workingItem.id,
              'title': workingItem.title,
              'path': workingItem.outputPath,
              'error': e.toString(),
            },
          );
        }
      });

      processed++;
      onProgress?.call(processed, candidates.length);
    }

    _observabilityService.info(
      'artwork_batch_apply_finished',
      context: {
        'candidates': candidates.length,
        'updated': updated,
        'failed': failed,
        'skipped': skipped,
      },
    );

    return ArtworkBatchApplyResult(
      totalCandidates: candidates.length,
      updatedCount: updated,
      failedCount: failed,
      skippedCount: skipped,
    );
  }

  @override
  Future<List<DownloadItem>> getAllDownloads() async {
    return _databaseService.getAllDownloads();
  }

  bool _hasArtwork(String? url) {
    if (url == null || url.trim().isEmpty) return false;
    return !MetadataUtils.isUnknownAppMetadata(url);
  }

  Future<Map<String, dynamic>> _downloadVideo({
    required String url,
    required String outputPath,
    required String type,
    required String format,
    required String quality,
    String? artist,
    String? album,
    String? artworkUrl,
  }) async {
    final override = _downloadVideoOverride;
    if (override != null) {
      return override(
        url: url,
        outputPath: outputPath,
        type: type,
        format: format,
        quality: quality,
        artist: artist,
        album: album,
        artworkUrl: artworkUrl,
      );
    }

    return _chaquoDownloadService.downloadVideo(
      url: url,
      outputPath: outputPath,
      type: type,
      format: format,
      quality: quality,
      artist: artist,
      album: album,
      artworkUrl: artworkUrl,
    );
  }

  void _showDownloadStarted(String id, String title) {
    final override = _showDownloadStartedOverride;
    if (override != null) {
      override(id, title);
      return;
    }
    _notificationService.showDownloadStarted(id, title);
  }

  void _showDownloadCompleted(String id, String title) {
    final override = _showDownloadCompletedOverride;
    if (override != null) {
      override(id, title);
      return;
    }
    _notificationService.showDownloadCompleted(id, title);
  }

  void _showDownloadFailed(String id, String title, String error) {
    final override = _showDownloadFailedOverride;
    if (override != null) {
      override(id, title, error);
      return;
    }
    _notificationService.showDownloadFailed(id, title, error);
  }

  Future<ExportResult> _exportToPublicCollection({
    required String sourcePath,
    required DownloadType type,
    bool allowUserInteractionFallback = false,
  }) async {
    final override = _exportToPublicCollectionOverride;
    if (override != null) {
      return override(
        sourcePath: sourcePath,
        type: type,
        allowUserInteractionFallback: allowUserInteractionFallback,
      );
    }

    return _storageService.exportToPublicCollection(
      sourcePath: sourcePath,
      type: type,
      allowUserInteractionFallback: allowUserInteractionFallback,
    );
  }

  Future<Map<String, dynamic>> _rewriteMetadata({
    required String filePath,
    required String title,
    String? artist,
    String? album,
    String? artworkUrl,
  }) async {
    final override = _rewriteMetadataOverride;
    if (override != null) {
      return override(
        filePath: filePath,
        title: title,
        artist: artist,
        album: album,
        artworkUrl: artworkUrl,
      );
    }

    return _chaquoDownloadService.rewriteMetadata(
      filePath: filePath,
      title: title,
      artist: artist,
      album: album,
      artworkUrl: artworkUrl,
    );
  }

  Future<String?> _getArtistImage(String artist) async {
    final override = _getArtistImageOverride;
    if (override != null) return override(artist);
    return _artworkCacheService.getArtistImage(artist);
  }

  Future<String?> _getArtistImageCached(String artist) async {
    final key = _normalizeForLookup(artist);
    return _artistArtworkLookup.resolve(
      key,
      () => _getArtistImage(artist),
    );
  }

  Future<String?> _getAlbumCover(String artist, String album) async {
    final override = _getAlbumCoverOverride;
    if (override != null) return override(artist, album);
    return _artworkCacheService.getAlbumCover(artist, album);
  }

  Future<String?> _getAlbumCoverCached(String artist, String album) async {
    final key = _composeLookupKey(artist, album);
    return _albumArtworkLookup.resolve(
      key,
      () => _getAlbumCover(artist, album),
    );
  }

  Future<String?> _getTrackCover(String artist, String title) async {
    final override = _getTrackCoverOverride;
    if (override != null) return override(artist, title);
    return _artworkCacheService.getTrackCover(artist, title);
  }

  Future<String?> _getTrackCoverCached(String artist, String title) async {
    final key = _composeLookupKey(artist, title);
    return _trackArtworkLookup.resolve(
      key,
      () => _getTrackCover(artist, title),
    );
  }

  String _composeLookupKey(String first, String second) {
    return '${_normalizeForLookup(first)}$_artworkKeySeparator${_normalizeForLookup(second)}';
  }

  String _normalizeForLookup(String? value) {
    if (value == null) {
      return '';
    }
    final normalized = MetadataUtils.normalizeMetadataText(value).trim();
    return normalized.toLowerCase();
  }

  String _normalizeForBatch(String? value) {
    return _normalizeForLookup(value);
  }

  String _normalizeArtistForBatch(String? value) {
    return _normalizeForLookup(value);
  }

  String _normalizeAlbumForBatch(String? value) {
    return _normalizeForLookup(value);
  }

  String? _guessTitleFromPath(String path) {
    return MetadataUtils.guessAppTitleFromPath(path);
  }

  String _simplifyError(String error) => DownloadErrorUtils.simplify(error);
}
