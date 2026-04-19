import 'dart:async';
import '../models/download_item.dart';
import '../utils/metadata_utils.dart';
import 'artwork_cache_service.dart';

/// Gerencia o cache e as buscas de artes de áudio.
/// Aplicando Regra 4 (First-class collections) e Regra 8 (Redução de estado).
class ArtworkManager {
  final Map<String, String?> _artistImageCache = {};
  final Map<String, String?> _albumCoverCache = {};
  final Map<String, String?> _trackCoverCache = {};

  final Map<String, Future<String?>> _artistInFlight = {};
  final Map<String, Future<String?>> _albumInFlight = {};
  final Map<String, Future<String?>> _trackInFlight = {};

  static const String _keySeparator = '|';

  void clear({bool includeService = false}) {
    _artistImageCache.clear();
    _albumCoverCache.clear();
    _trackCoverCache.clear();
    _artistInFlight.clear();
    _albumInFlight.clear();
    _trackInFlight.clear();

    if (includeService) {
      ArtworkCacheService.instance.clear();
    }
  }

  Future<String?> getArtistImage(
      String artist, Future<String?> Function(String) loader) async {
    final key = _normalizeForLookup(artist);
    return _resolveCachedLookup(
      key: key,
      cache: _artistImageCache,
      inFlight: _artistInFlight,
      loader: () => loader(artist),
    );
  }

  Future<String?> getAlbumCover(String artist, String album,
      Future<String?> Function(String, String) loader) async {
    final key = _composeLookupKey(artist, album);
    return _resolveCachedLookup(
      key: key,
      cache: _albumCoverCache,
      inFlight: _albumInFlight,
      loader: () => loader(artist, album),
    );
  }

  Future<String?> getTrackCover(String artist, String title,
      Future<String?> Function(String, String) loader) async {
    final key = _composeLookupKey(artist, title);
    return _resolveCachedLookup(
      key: key,
      cache: _trackCoverCache,
      inFlight: _trackInFlight,
      loader: () => loader(artist, title),
    );
  }

  Future<String?> _resolveCachedLookup({
    required String key,
    required Map<String, String?> cache,
    required Map<String, Future<String?>> inFlight,
    required Future<String?> Function() loader,
  }) async {
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

  String _composeLookupKey(String first, String second) {
    return '${_normalizeForLookup(first)}$_keySeparator${_normalizeForLookup(second)}';
  }

  String _normalizeForLookup(String? value) {
    if (value == null) {
      return '';
    }
    final normalized = MetadataUtils.normalizeMetadataText(value).trim();
    return normalized.toLowerCase();
  }

  String? preferredEmbeddedArtworkUrl(DownloadItem item) {
    final albumImage = _normalizeArtworkSource(item.albumImageUrl);
    if (albumImage != null) return albumImage;
    return _normalizeArtworkSource(item.artistImageUrl);
  }

  String? _normalizeArtworkSource(String? source) {
    final trimmed = source?.trim();
    if (trimmed == null || trimmed.isEmpty) return null;
    if (MetadataUtils.isUnknownAppMetadata(trimmed)) return null;

    final uri = Uri.tryParse(trimmed);
    if (uri != null && uri.hasScheme) return trimmed;
    if (trimmed.startsWith('/')) return trimmed;
    return null;
  }
}
