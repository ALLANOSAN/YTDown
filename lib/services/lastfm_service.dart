import 'dart:convert';
import 'package:http/http.dart' as http;
import '../services/observability_service.dart';

class LastFmService {
  LastFmService._();
  static final instance = LastFmService._();

  // API key do LastFM - fornecida via .secrets.json no build
  // Nunca hardcoded no código fonte
  static const String _apiKey = String.fromEnvironment('LASTFM_API_KEY');
  static const String _baseUrl = 'https://ws.audioscrobbler.com/2.0/';
  static const String _lastFmHost = 'ws.audioscrobbler.com';
  // Last.fm recomenda User-Agent identificável em todas as requisições.
  static const Map<String, String> _defaultHeaders = {
    'User-Agent': 'YTDown/1.0 (Android; Flutter; LastFmMetadataClient)',
    'Accept': 'application/json',
  };

  // Cache LRU simples para evitar requests redundantes (máx 100 entradas)
  static const int _maxCacheSize = 100;
  static const String _lastFmPlaceholderHash =
      '2a96cbd8b46e442fc41c2b86b821562f';
  final Map<String, String?> _cache = {};

  String _artistCacheKey(String artist) {
    return 'artist:${artist.toLowerCase()}';
  }

  String _albumCacheKey(String artist, String album) {
    return 'album:${artist.toLowerCase()}:${album.toLowerCase()}';
  }

  String _trackCacheKey(String artist, String title) {
    return 'track:${artist.toLowerCase()}:${title.toLowerCase()}';
  }

  Future<String?> _resolveCachedLookup(
    String cacheKey,
    Future<String?> Function() resolver,
  ) async {
    if (_cache.containsKey(cacheKey)) {
      return _cache[cacheKey];
    }

    final value = await resolver();
    _putCache(cacheKey, value);
    return value;
  }

  void clearCache() {
    _cache.clear();
  }

  String _sanitizeLookupValue(String value) {
    var normalized = value.trim();
    normalized = normalized.replaceAll(RegExp(r'[_]+'), ' ');
    normalized = normalized.replaceAll(
      RegExp(r'[_-][0-9a-f]{6,}$', caseSensitive: false),
      '',
    );
    normalized = normalized.replaceAll(RegExp(r'\s+'), ' ').trim();
    return normalized;
  }

  bool get _hasLastFmKey => _apiKey.isNotEmpty;

  Future<Map<String, dynamic>?> _fetchLastFmJsonSafe({
    required String method,
    required Map<String, String> params,
    required Map<String, Object?> logContext,
  }) async {
    if (!_hasLastFmKey) return null;

    try {
      return await _fetchLastFmJson(
        method: method,
        params: params,
        logContext: logContext,
      );
    } catch (e) {
      ObservabilityService.instance.error('lastfm_lookup_error', context: {
        'error': e.toString(),
        ...logContext,
      });
      return null;
    }
  }

  /// Busca a capa do álbum (com cache)
  Future<String?> getAlbumCover(String artist, String album) async {
    final cleanArtist = _sanitizeLookupValue(artist);
    final cleanAlbum = _sanitizeLookupValue(album);
    if (cleanArtist.isEmpty || cleanAlbum.isEmpty || cleanAlbum == 'YTDown') {
      return null;
    }

    final cacheKey = _albumCacheKey(cleanArtist, cleanAlbum);
    return _resolveCachedLookup(cacheKey, () async {
      final data = await _fetchLastFmJsonSafe(
        method: 'album.getinfo',
        params: {
          'artist': cleanArtist,
          'album': cleanAlbum,
          'autocorrect': '1',
        },
        logContext: {
          'artist': cleanArtist,
          'album': cleanAlbum,
        },
      );

      final albumData = data?['album'];
      if (albumData is Map<String, dynamic>) {
        final result = _getBestImage(albumData['image'] as List<dynamic>?);
        if (result != null) return result;
      }

      return _getAlbumCoverFallback(cleanArtist, cleanAlbum);
    });
  }

  /// Busca a imagem do artista (com cache)
  Future<String?> getArtistImage(String artist) async {
    final cleanArtist = _sanitizeLookupValue(artist);
    if (cleanArtist.isEmpty) return null;

    final cacheKey = _artistCacheKey(cleanArtist);
    return _resolveCachedLookup(cacheKey, () async {
      final data = await _fetchLastFmJsonSafe(
        method: 'artist.getinfo',
        params: {
          'artist': cleanArtist,
          'autocorrect': '1',
        },
        logContext: {
          'artist': cleanArtist,
        },
      );

      final artistData = data?['artist'];
      if (artistData is Map<String, dynamic>) {
        final result = _getBestImage(artistData['image'] as List<dynamic>?);
        if (result != null) return result;
      }

      return _getArtistImageFallback(cleanArtist);
    });
  }

  /// Busca capa por faixa (quando album e desconhecido/indisponivel)
  Future<String?> getTrackCover(String artist, String title) async {
    final cleanArtist = _sanitizeLookupValue(artist);
    final cleanTitle = _sanitizeLookupValue(title);
    if (cleanArtist.isEmpty || cleanTitle.isEmpty) {
      return null;
    }

    final cacheKey = _trackCacheKey(cleanArtist, cleanTitle);
    return _resolveCachedLookup(cacheKey, () async {
      final data = await _fetchLastFmJsonSafe(
        method: 'track.getInfo',
        params: {
          'artist': cleanArtist,
          'track': cleanTitle,
          'autocorrect': '1',
        },
        logContext: {
          'artist': cleanArtist,
          'title': cleanTitle,
        },
      );

      final track = data?['track'] as Map<String, dynamic>?;
      final album = track?['album'];
      if (album is Map<String, dynamic>) {
        final result = _getBestImage(album['image'] as List<dynamic>?);
        if (result != null && result.isNotEmpty) {
          return result;
        }
      }

      return _getTrackCoverFallback(cleanArtist, cleanTitle);
    });
  }

  Future<String?> _getArtistImageFallback(String artist) async {
    final deezer = await _fetchDeezerArtistImage(artist);
    if (deezer != null) return deezer;

    return await _fetchItunesArtistImage(artist);
  }

  Future<String?> _getAlbumCoverFallback(String artist, String album) async {
    final itunes = await _fetchItunesAlbumCover(artist, album);
    if (itunes != null) return itunes;

    return await _fetchDeezerAlbumCover(artist, album);
  }

  Future<String?> _getTrackCoverFallback(String artist, String title) async {
    final deezer = await _fetchDeezerTrackCover(artist, title);
    if (deezer != null) return deezer;

    return await _fetchItunesTrackCover(artist, title);
  }

  Future<String?> _fetchDeezerArtistImage(String artist) async {
    final data = await _fetchJson(
      Uri.parse(
        'https://api.deezer.com/search/artist?q=${Uri.encodeComponent(artist)}&limit=10',
      ),
    );
    final artists = _extractResults(data, key: 'data');
    if (artists.isEmpty) return null;

    for (final result in artists) {
      final sameArtist = _looselyMatches(result['name']?.toString(), artist);
      if (!sameArtist) continue;

      final image = _extractDeezerArtistImage(result);
      if (image != null) return image;
    }

    for (final result in artists) {
      final image = _extractDeezerArtistImage(result);
      if (image != null) return image;
    }

    return null;
  }

  Future<String?> _fetchDeezerAlbumCover(String artist, String album) async {
    final results = await _fetchDeezerTrackResults('$artist $album');
    if (results.isEmpty) return null;

    for (final result in results) {
      final sameArtist = _looselyMatches(_deezerArtistName(result), artist);
      final sameAlbum = _looselyMatches(_deezerAlbumTitle(result), album);
      if (!sameArtist || !sameAlbum) continue;

      final cover = _extractDeezerAlbumCover(result);
      if (cover != null) return cover;
    }

    for (final result in results) {
      final sameAlbum = _looselyMatches(_deezerAlbumTitle(result), album);
      if (!sameAlbum) continue;

      final cover = _extractDeezerAlbumCover(result);
      if (cover != null) return cover;
    }

    for (final result in results) {
      final cover = _extractDeezerAlbumCover(result);
      if (cover != null) return cover;
    }

    return null;
  }

  Future<String?> _fetchDeezerTrackCover(String artist, String title) async {
    final results = await _fetchDeezerTrackResults('$artist $title');
    if (results.isEmpty) return null;

    for (final result in results) {
      final sameArtist = _looselyMatches(_deezerArtistName(result), artist);
      final sameTrack = _looselyMatches(_deezerTrackTitle(result), title);
      if (!sameArtist || !sameTrack) continue;

      final cover = _extractDeezerAlbumCover(result);
      if (cover != null) return cover;
    }

    for (final result in results) {
      final sameArtist = _looselyMatches(_deezerArtistName(result), artist);
      final sameAlbum = _looselyMatches(_deezerAlbumTitle(result), title);
      if (!sameArtist || !sameAlbum) continue;

      final cover = _extractDeezerAlbumCover(result);
      if (cover != null) return cover;
    }

    for (final result in results) {
      final sameArtist = _looselyMatches(_deezerArtistName(result), artist);
      if (!sameArtist) continue;

      final cover = _extractDeezerAlbumCover(result);
      if (cover != null) return cover;
    }

    for (final result in results) {
      final cover = _extractDeezerAlbumCover(result);
      if (cover != null) return cover;
    }

    return null;
  }

  Future<List<Map<String, dynamic>>> _fetchDeezerTrackResults(
      String query) async {
    final data = await _fetchJson(
      Uri.parse(
        'https://api.deezer.com/search?q=${Uri.encodeComponent(query)}&limit=12',
      ),
    );
    return _extractResults(data, key: 'data');
  }

  Future<String?> _fetchItunesArtistImage(String artist) async {
    final direct = await _fetchItunesArtworkFromSearch(
      term: artist,
      entity: 'musicArtist',
      limit: 8,
      matcher: (result) =>
          _looselyMatches(result['artistName']?.toString(), artist),
    );
    if (direct != null) return direct;

    return _fetchItunesArtworkFromSearch(
      term: artist,
      entity: 'song',
      limit: 12,
      matcher: (result) =>
          _looselyMatches(result['artistName']?.toString(), artist),
    );
  }

  Future<String?> _fetchItunesAlbumCover(String artist, String album) async {
    final term = '$artist $album';
    return _fetchItunesArtworkFromSearch(
      term: term,
      entity: 'album',
      limit: 12,
      matcher: (result) {
        final sameArtist =
            _looselyMatches(result['artistName']?.toString(), artist);
        final sameAlbum = _looselyMatches(
              result['collectionName']?.toString(),
              album,
            ) ||
            _looselyMatches(result['trackName']?.toString(), album);
        return sameArtist && sameAlbum;
      },
    );
  }

  Future<String?> _fetchItunesTrackCover(String artist, String title) async {
    final term = '$artist $title';
    return _fetchItunesArtworkFromSearch(
      term: term,
      entity: 'song',
      limit: 12,
      matcher: (result) {
        final sameArtist =
            _looselyMatches(result['artistName']?.toString(), artist);
        final sameTrack =
            _looselyMatches(result['trackName']?.toString(), title) ||
                _looselyMatches(result['collectionName']?.toString(), title);
        return sameArtist && sameTrack;
      },
    );
  }

  Future<String?> _fetchItunesArtworkFromSearch({
    required String term,
    required String entity,
    required int limit,
    required bool Function(Map<String, dynamic> result) matcher,
  }) async {
    final data = await _fetchJson(
      Uri.parse(
        'https://itunes.apple.com/search?term=${Uri.encodeComponent(term)}&media=music&entity=$entity&limit=$limit',
      ),
    );
    if (data == null) return null;

    final list = data['results'];
    if (list is! List || list.isEmpty) return null;

    final results = list
        .whereType<Map>()
        .map((entry) => Map<String, dynamic>.from(entry))
        .toList();

    for (final result in results.where(matcher)) {
      final art = _extractItunesArtwork(result);
      if (art != null) return art;
    }

    for (final result in results) {
      final art = _extractItunesArtwork(result);
      if (art != null) return art;
    }

    return null;
  }

  Future<Map<String, dynamic>?> _fetchJson(Uri url) async {
    try {
      final response = await http.get(url, headers: _defaultHeaders);
      if (response.statusCode != 200) return null;

      final decoded = json.decode(response.body);
      if (decoded is Map<String, dynamic>) return decoded;
      if (decoded is Map) return Map<String, dynamic>.from(decoded);
      return null;
    } catch (_) {
      return null;
    }
  }

  Uri _buildLastFmUri(
    String method,
    Map<String, String> params,
  ) {
    return Uri.parse(_baseUrl).replace(
      queryParameters: {
        'method': method,
        'api_key': _apiKey,
        'format': 'json',
        ...params,
      },
    );
  }

  Future<Map<String, dynamic>?> _fetchLastFmJson({
    required String method,
    required Map<String, String> params,
    Map<String, dynamic>? logContext,
  }) async {
    final url = _buildLastFmUri(method, params);
    final response = await http.get(url, headers: _defaultHeaders);

    if (response.statusCode != 200) {
      ObservabilityService.instance.warning(
        'lastfm_http_error',
        context: {
          'method': method,
          'statusCode': response.statusCode,
          ...?logContext,
        },
      );
      return null;
    }

    final decoded = json.decode(response.body);
    if (decoded is! Map) {
      return null;
    }

    final data = Map<String, dynamic>.from(decoded);
    if (url.host == _lastFmHost && data['error'] != null) {
      ObservabilityService.instance.warning(
        'lastfm_api_error',
        context: {
          'method': method,
          'code': data['error'],
          'message': data['message'],
          ...?logContext,
        },
      );
      return null;
    }

    return data;
  }

  List<Map<String, dynamic>> _extractResults(
    Map<String, dynamic>? data, {
    required String key,
  }) {
    if (data == null) return const [];
    final list = data[key];
    if (list is! List || list.isEmpty) return const [];

    return list
        .whereType<Map>()
        .map((entry) => Map<String, dynamic>.from(entry))
        .toList();
  }

  String? _nonEmpty(Object? value) {
    final text = value?.toString().trim();
    if (text == null || text.isEmpty) return null;
    return text;
  }

  String? _sanitizeArtworkUrl(String? value) {
    final url = _nonEmpty(value);
    if (url == null) return null;
    if (_isPlaceholderArtworkUrl(url)) return null;
    return url;
  }

  bool _isPlaceholderArtworkUrl(String url) {
    return url.toLowerCase().contains(_lastFmPlaceholderHash);
  }

  String? _extractItunesArtwork(Map<String, dynamic> result) {
    final raw = _nonEmpty(result['artworkUrl100']) ??
        _nonEmpty(result['artworkUrl60']) ??
        _nonEmpty(result['artworkUrl30']);
    if (raw == null) return null;
    return _upgradeItunesArtwork(raw);
  }

  String? _extractDeezerArtistImage(Map<String, dynamic> result) {
    return _nonEmpty(result['picture_xl']) ??
        _nonEmpty(result['picture_big']) ??
        _nonEmpty(result['picture_medium']) ??
        _nonEmpty(result['picture']);
  }

  String? _extractDeezerAlbumCover(Map<String, dynamic> result) {
    final albumObj = result['album'];
    if (albumObj is! Map) return null;
    final album = Map<String, dynamic>.from(albumObj);

    return _nonEmpty(album['cover_xl']) ??
        _nonEmpty(album['cover_big']) ??
        _nonEmpty(album['cover_medium']) ??
        _nonEmpty(album['cover']);
  }

  String? _deezerArtistName(Map<String, dynamic> result) {
    final artistObj = result['artist'];
    if (artistObj is Map) {
      return _nonEmpty(artistObj['name']);
    }
    return _nonEmpty(result['artistName']) ?? _nonEmpty(result['artist']);
  }

  String? _deezerAlbumTitle(Map<String, dynamic> result) {
    final albumObj = result['album'];
    if (albumObj is Map) {
      return _nonEmpty(albumObj['title']);
    }
    return _nonEmpty(result['albumTitle']) ?? _nonEmpty(result['album']);
  }

  String? _deezerTrackTitle(Map<String, dynamic> result) {
    return _nonEmpty(result['title']) ?? _nonEmpty(result['trackName']);
  }

  String _upgradeItunesArtwork(String url) {
    var upgraded = url;
    upgraded = upgraded.replaceAll(
      RegExp(r'\d{2,4}x\d{2,4}bb'),
      '1000x1000bb',
    );
    upgraded = upgraded.replaceAll(
      RegExp(r'\d{2,4}x\d{2,4}-75'),
      '1000x1000-75',
    );
    return upgraded;
  }

  bool _looselyMatches(String? source, String target) {
    final normalizedSource = _normalizeCompare(source);
    final normalizedTarget = _normalizeCompare(target);
    if (normalizedSource.isEmpty || normalizedTarget.isEmpty) {
      return false;
    }
    return normalizedSource == normalizedTarget ||
        normalizedSource.contains(normalizedTarget) ||
        normalizedTarget.contains(normalizedSource);
  }

  String _normalizeCompare(String? value) {
    final text = _nonEmpty(value) ?? '';
    var normalized = text.toLowerCase();
    normalized = normalized.replaceAll(RegExp(r'[^a-z0-9\s]'), ' ');
    normalized = normalized.replaceAll(RegExp(r'\s+'), ' ').trim();
    return normalized;
  }

  void _putCache(String key, String? value) {
    // Evicção simples: remove a entrada mais antiga se no limite
    if (_cache.length >= _maxCacheSize) {
      _cache.remove(_cache.keys.first);
    }
    _cache[key] = value;
  }

  String? _getBestImage(List<dynamic>? images) {
    if (images == null || images.isEmpty) return null;

    // Tenta encontrar a maior disponível (ordem de preferência)
    for (final size in ['extralarge', 'large', 'medium']) {
      for (final img in images) {
        if (img is Map<String, dynamic> && img['size'] == size) {
          final candidate = _sanitizeArtworkUrl(img['#text']?.toString());
          if (candidate != null) {
            return candidate;
          }
        }
      }
    }

    // Fallback para a última da lista
    final last = images.last;
    return last is Map<String, dynamic>
        ? _sanitizeArtworkUrl(last['#text']?.toString())
        : null;
  }
}
