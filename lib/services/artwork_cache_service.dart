import 'dart:async';
import '../utils/common_utils.dart';
import '../utils/lru_cache.dart';
import 'lastfm_service.dart';

class ArtworkCacheService {
  ArtworkCacheService._();
  static final instance = ArtworkCacheService._();

  static const int capacity = 256;
  static const String _pairKeySeparator = '::';

  final _artistCache = LruCache<String, String?>(capacity);
  final _albumCache = LruCache<String, String?>(capacity);
  final _trackCache = LruCache<String, String?>(capacity);

  final _artistInFlight = <String, Future<String?>>{};
  final _albumInFlight = <String, Future<String?>>{};
  final _trackInFlight = <String, Future<String?>>{};

  // Funções de injeção para testes (overrides)
  Future<String?> Function(String)? _getArtistImageOverride;
  Future<String?> Function(String, String)? _getAlbumCoverOverride;
  Future<String?> Function(String, String)? _getTrackCoverOverride;

  void configureTestOverrides({
    Future<String?> Function(String)? getArtistImage,
    Future<String?> Function(String, String)? getAlbumCover,
    Future<String?> Function(String, String)? getTrackCover,
  }) {
    _getArtistImageOverride = getArtistImage;
    _getAlbumCoverOverride = getAlbumCover;
    _getTrackCoverOverride = getTrackCover;
  }

  void resetTestOverrides() {
    _getArtistImageOverride = null;
    _getAlbumCoverOverride = null;
    _getTrackCoverOverride = null;
    clear();
  }

  String _normalizeKeyPart(String raw) => raw.trim().toLowerCase();

  String _buildPairKey(String first, String second) {
    return '${_normalizeKeyPart(first)}$_pairKeySeparator${_normalizeKeyPart(second)}';
  }

  Future<String?> _loadWithCache({
    required String key,
    required LruCache<String, String?> cache,
    required Map<String, Future<String?>> inFlight,
    required Future<String?> Function() loader,
  }) async {
    if (cache.containsKey(key)) {
      return cache.get(key);
    }

    final existingTask = inFlight[key];
    if (existingTask != null) {
      return existingTask;
    }

    final task = loader();
    inFlight[key] = task;

    try {
      final value = await task;
      cache.put(key, value);
      return value;
    } finally {
      if (inFlight[key] == task) {
        inFlight.remove(key);
      }
    }
  }

  Future<String?> getArtistImage(String artist) async {
    if (!CommonUtils.hasText(artist)) return null;

    final key = _normalizeKeyPart(artist);
    final lastFmService = LastFmService.instance;
    return _loadWithCache(
      key: key,
      cache: _artistCache,
      inFlight: _artistInFlight,
      loader: () {
        if (_getArtistImageOverride != null) {
          return _getArtistImageOverride!(artist);
        }
        return lastFmService.getArtistImage(artist);
      },
    );
  }

  Future<String?> getAlbumCover(String artist, String album) async {
    if (!CommonUtils.hasText(artist) || !CommonUtils.hasText(album)) {
      return null;
    }

    final key = _buildPairKey(artist, album);
    final lastFmService = LastFmService.instance;
    return _loadWithCache(
      key: key,
      cache: _albumCache,
      inFlight: _albumInFlight,
      loader: () {
        if (_getAlbumCoverOverride != null) {
          return _getAlbumCoverOverride!(artist, album);
        }
        return lastFmService.getAlbumCover(artist, album);
      },
    );
  }

  Future<String?> getTrackCover(String artist, String title) async {
    if (!CommonUtils.hasText(artist) || !CommonUtils.hasText(title)) {
      return null;
    }

    final key = _buildPairKey(artist, title);
    final lastFmService = LastFmService.instance;
    return _loadWithCache(
      key: key,
      cache: _trackCache,
      inFlight: _trackInFlight,
      loader: () {
        if (_getTrackCoverOverride != null) {
          return _getTrackCoverOverride!(artist, title);
        }
        return lastFmService.getTrackCover(artist, title);
      },
    );
  }

  void clear({bool includeUpstream = true}) {
    _artistCache.clear();
    _albumCache.clear();
    _trackCache.clear();
    _artistInFlight.clear();
    _albumInFlight.clear();
    _trackInFlight.clear();
    if (includeUpstream) {
      final lastFmService = LastFmService.instance;
      lastFmService.clearCache();
    }
  }
}
