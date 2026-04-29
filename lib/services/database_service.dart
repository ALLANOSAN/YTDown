import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';
import 'package:flutter/foundation.dart';
import '../models/download_item.dart';
import '../utils/common_utils.dart';

class DatabaseService {
  DatabaseService._();
  static final instance = DatabaseService._();

  static const String _defaultDatabaseFileName = 'ytdown.db';
  static const int _databaseVersion = 9;
  static const int _recentSearchHistoryLimit = 10;

  static String _databaseFileName = _defaultDatabaseFileName;

  @visibleForTesting
  static String get databaseFileName => _databaseFileName;

  @visibleForTesting
  static set databaseFileName(String value) {
    final normalized = value.trim();
    _databaseFileName =
        normalized.isEmpty ? _defaultDatabaseFileName : normalized;
  }

  static const String _downloadsTable = 'downloads';
  static const String _downloadFailureEventsTable = 'download_failure_events';
  static const String _exportFailureEventsTable = 'export_failure_events';
  static const String _settingsTable = 'app_settings';
  static const String _searchHistoryTable = 'search_history';
  static const String _favoritesTable = 'favorites';
  static const String _playlistsTable = 'playlists';
  static const String _playlistTracksTable = 'playlist_tracks';
  static const String _autoExportSettingKey = 'auto_export_completed_downloads';

  Database? _database;

  List<DownloadItem> _mapDownloads(List<Map<String, dynamic>> rows) {
    return rows.map((row) => DownloadItem.fromMap(row)).toList();
  }

  /// Fecha a conexão ativa com o banco para permitir isolamento em testes.
  Future<void> close() async {
    final db = _database;
    _database = null;
    if (db != null && db.isOpen) {
      await db.close();
    }
  }

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _initDB();
    return _database!;
  }

  Future<Database> _initDB() async {
    final dbPath = await getDatabasesPath();
    final path = join(dbPath, _databaseFileName);

    return await openDatabase(
      path,
      version: _databaseVersion,
      onCreate: (db, version) async {
        await _createDownloadsTable(db);
        await _createDownloadFailureEventsTable(db);
        await _createExportFailureEventsTable(db);
        await _createSettingsTable(db);
        await _createSearchHistoryTable(db);
        await _createFavoritesTable(db);
        await _createPlaylistTables(db);
      },
      onUpgrade: (db, oldVersion, newVersion) async {
        if (oldVersion < 2) {
          await _createDownloadFailureEventsTable(db);
        }
        if (oldVersion < 3) {
          await _ensureDownloadExportColumns(db);
          await _createSettingsTable(db);
        }
        if (oldVersion < 4) {
          await _createExportFailureEventsTable(db);
        }
        if (oldVersion < 5) {
          await _ensureLibraryColumns(db);
        }
        if (oldVersion < 6) {
          await _createSearchHistoryTable(db);
          await _createFavoritesTable(db);
        }
        if (oldVersion < 7) {
          await _createPlaylistTables(db);
        }
        if (oldVersion < 8) {
          await db.execute(
              'CREATE INDEX IF NOT EXISTS idx_search_query ON $_searchHistoryTable(query)');
          await db.execute(
              'CREATE INDEX IF NOT EXISTS idx_favorites_url ON $_favoritesTable(url)');
        }
        if (oldVersion < 9) {
          // Fase 1: Performance - Criação de Índices Otimizados
          await db.execute(
              'CREATE INDEX IF NOT EXISTS idx_downloads_artist ON $_downloadsTable(artist)');
          await db.execute(
              'CREATE INDEX IF NOT EXISTS idx_downloads_album ON $_downloadsTable(album)');
          await db.execute(
              'CREATE INDEX IF NOT EXISTS idx_downloads_type_status ON $_downloadsTable(type, status)');
          await db.execute(
              'CREATE INDEX IF NOT EXISTS idx_downloads_title ON $_downloadsTable(title)');
        }
      },
    );
  }

  Future<void> _createDownloadsTable(Database db) async {
    await db.execute('''
      CREATE TABLE IF NOT EXISTS $_downloadsTable (
        id TEXT PRIMARY KEY,
        url TEXT,
        title TEXT,
        thumbnail TEXT,
        type INTEGER,
        format TEXT,
        quality TEXT,
        outputPath TEXT,
        status INTEGER,
        progress REAL,
        errorMessage TEXT,
        createdAt INTEGER,
        fileSizeBytes INTEGER,
        exportedPath TEXT,
        exportStatus TEXT DEFAULT 'pending',
        artist TEXT,
        album TEXT,
        artistImageUrl TEXT,
        albumImageUrl TEXT
      )
    ''');
  }

  Future<void> _createSettingsTable(Database db) async {
    await db.execute('''
      CREATE TABLE IF NOT EXISTS $_settingsTable (
        key TEXT PRIMARY KEY,
        value TEXT
      )
    ''');
  }

  Future<void> _ensureDownloadExportColumns(Database db) async {
    final columns = await db.rawQuery('PRAGMA table_info($_downloadsTable)');
    final hasExportedPath = columns.any((c) => c['name'] == 'exportedPath');
    final hasExportStatus = columns.any((c) => c['name'] == 'exportStatus');

    if (!hasExportedPath) {
      await db
          .execute('ALTER TABLE $_downloadsTable ADD COLUMN exportedPath TEXT');
    }
    if (!hasExportStatus) {
      await db.execute(
          "ALTER TABLE $_downloadsTable ADD COLUMN exportStatus TEXT DEFAULT 'pending'");
    }
  }

  Future<void> _ensureLibraryColumns(Database db) async {
    final columns = await db.rawQuery('PRAGMA table_info($_downloadsTable)');
    final hasArtist = columns.any((c) => c['name'] == 'artist');
    final hasAlbum = columns.any((c) => c['name'] == 'album');
    final hasArtistImg = columns.any((c) => c['name'] == 'artistImageUrl');
    final hasAlbumImg = columns.any((c) => c['name'] == 'albumImageUrl');

    if (!hasArtist) {
      await db.execute('ALTER TABLE $_downloadsTable ADD COLUMN artist TEXT');
    }
    if (!hasAlbum) {
      await db.execute('ALTER TABLE $_downloadsTable ADD COLUMN album TEXT');
    }
    if (!hasArtistImg) {
      await db.execute(
          'ALTER TABLE $_downloadsTable ADD COLUMN artistImageUrl TEXT');
    }
    if (!hasAlbumImg) {
      await db.execute(
          'ALTER TABLE $_downloadsTable ADD COLUMN albumImageUrl TEXT');
    }
  }

  Future<void> _createSearchHistoryTable(Database db) async {
    await db.execute('''
      CREATE TABLE IF NOT EXISTS $_searchHistoryTable (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        query TEXT UNIQUE,
        createdAt INTEGER
      )
    ''');
    await db.execute(
        'CREATE INDEX IF NOT EXISTS idx_search_query ON $_searchHistoryTable(query)');
  }

  Future<void> _createFavoritesTable(Database db) async {
    await db.execute('''
      CREATE TABLE IF NOT EXISTS $_favoritesTable (
        id TEXT PRIMARY KEY,
        title TEXT,
        thumbnail TEXT,
        url TEXT,
        type TEXT,
        createdAt INTEGER
      )
    ''');
    await db.execute(
        'CREATE INDEX IF NOT EXISTS idx_favorites_url ON $_favoritesTable(url)');
  }

  Future<void> _createPlaylistTables(Database db) async {
    await db.execute('''
      CREATE TABLE IF NOT EXISTS $_playlistsTable (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        description TEXT,
        thumbnail TEXT,
        createdAt INTEGER
      )
    ''');

    await db.execute('''
      CREATE TABLE IF NOT EXISTS $_playlistTracksTable (
        playlistId TEXT,
        trackId TEXT,
        position INTEGER,
        addedAt INTEGER,
        PRIMARY KEY (playlistId, trackId),
        FOREIGN KEY (playlistId) REFERENCES playlists(id) ON DELETE CASCADE,
        FOREIGN KEY (trackId) REFERENCES downloads(id) ON DELETE CASCADE
      )
    ''');
  }

  Future<void> _createDownloadFailureEventsTable(Database db) async {
    await db.execute('''
      CREATE TABLE IF NOT EXISTS $_downloadFailureEventsTable (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        downloadId TEXT NOT NULL,
        title TEXT,
        source TEXT,
        reasonKey TEXT,
        errorMessage TEXT,
        occurredAt INTEGER NOT NULL,
        dayKey TEXT NOT NULL
      )
    ''');

    await db.execute('''
      CREATE INDEX IF NOT EXISTS idx_failure_dayKey
      ON $_downloadFailureEventsTable(dayKey)
    ''');

    await db.execute('''
      CREATE INDEX IF NOT EXISTS idx_failure_occurredAt
      ON $_downloadFailureEventsTable(occurredAt)
    ''');

    await db.execute('''
      CREATE INDEX IF NOT EXISTS idx_failure_reasonKey
      ON $_downloadFailureEventsTable(reasonKey)
    ''');
  }

  Future<void> _createExportFailureEventsTable(Database db) async {
    await db.execute('''
      CREATE TABLE IF NOT EXISTS $_exportFailureEventsTable (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        downloadId TEXT,
        title TEXT,
        source TEXT,
        stage TEXT,
        strategy TEXT,
        manufacturer TEXT,
        brand TEXT,
        model TEXT,
        sdkInt INTEGER,
        androidRelease TEXT,
        mediaType TEXT,
        mimeType TEXT,
        errorMessage TEXT,
        occurredAt INTEGER NOT NULL,
        dayKey TEXT NOT NULL
      )
    ''');

    await db.execute('''
      CREATE INDEX IF NOT EXISTS idx_export_failure_occurredAt
      ON $_exportFailureEventsTable(occurredAt)
    ''');

    await db.execute('''
      CREATE INDEX IF NOT EXISTS idx_export_failure_device
      ON $_exportFailureEventsTable(manufacturer, model, sdkInt)
    ''');
  }

  Future<void> insertDownload(DownloadItem item) async {
    final db = await database;
    await db.insert(_downloadsTable, item.toMap(),
        conflictAlgorithm: ConflictAlgorithm.replace);
  }

  Future<void> updateDownload(DownloadItem item) async {
    final db = await database;
    await db.update(_downloadsTable, item.toMap(),
        where: 'id = ?', whereArgs: [item.id]);
  }

  Future<List<DownloadItem>> getAllDownloads() async {
    final db = await database;
    final List<Map<String, dynamic>> maps =
        await db.query(_downloadsTable, orderBy: 'createdAt DESC');
    return _mapDownloads(maps);
  }

  // --- FASE 2: Métodos de Query Otimizados para Performance O(log n) ---

  Future<List<Map<String, dynamic>>> getDistinctArtists(
      {String query = ''}) async {
    final db = await database;
    final whereClause = query.isEmpty
        ? "artist IS NOT NULL AND artist != ''"
        : "artist IS NOT NULL AND artist != '' AND artist LIKE ?";
    final args = query.isEmpty ? [] : ['%$query%'];

    final result = await db.rawQuery('''
      SELECT artist as name, 
             COUNT(id) as trackCount, 
             COALESCE(
               MAX(
                 CASE
                   WHEN artistImageUrl IS NULL THEN NULL
                   WHEN artistImageUrl = '' THEN NULL
                   WHEN LOWER(artistImageUrl) LIKE '%2a96cbd8b46e442fc41c2b86b821562f%' THEN NULL
                   ELSE artistImageUrl
                 END
               ),
               MAX(
                 CASE
                   WHEN albumImageUrl IS NULL THEN NULL
                   WHEN albumImageUrl = '' THEN NULL
                   WHEN LOWER(albumImageUrl) LIKE '%2a96cbd8b46e442fc41c2b86b821562f%' THEN NULL
                   ELSE albumImageUrl
                 END
               ),
               MAX(NULLIF(thumbnail, ''))
             ) as imageUrl 
      FROM $_downloadsTable 
      WHERE type = ${DownloadType.audio.index} 
        AND status = ${DownloadStatus.completed.index}
        AND $whereClause
      GROUP BY artist 
      ORDER BY artist COLLATE NOCASE ASC
    ''', args);
    return result;
  }

  Future<List<Map<String, dynamic>>> getDistinctAlbums(
      {String query = ''}) async {
    final db = await database;
    final whereClause = query.isEmpty
        ? "album IS NOT NULL AND album != ''"
        : "album IS NOT NULL AND album != '' AND album LIKE ?";
    final args = query.isEmpty ? [] : ['%$query%'];

    final result = await db.rawQuery('''
      SELECT album as name, 
             MAX(artist) as subtitle,
             COUNT(id) as trackCount, 
             MAX(albumImageUrl) as imageUrl 
      FROM $_downloadsTable 
      WHERE type = ${DownloadType.audio.index} 
        AND status = ${DownloadStatus.completed.index}
        AND $whereClause
      GROUP BY album 
      ORDER BY album COLLATE NOCASE ASC
    ''', args);
    return result;
  }

  Future<List<DownloadItem>> searchLibrary(String query) async {
    if (query.trim().isEmpty) return [];

    final db = await database;
    final likeQuery = '%${query.trim()}%';

    final List<Map<String, dynamic>> maps = await db.query(_downloadsTable,
        where:
            'type = ? AND status = ? AND (title LIKE ? OR artist LIKE ? OR album LIKE ?)',
        whereArgs: [
          DownloadType.audio.index,
          DownloadStatus.completed.index,
          likeQuery,
          likeQuery,
          likeQuery
        ],
        orderBy: 'createdAt DESC');
    return _mapDownloads(maps);
  }

  Future<List<DownloadItem>> getLibraryByArtist(String artist) async {
    final db = await database;
    final List<Map<String, dynamic>> maps = await db.query(_downloadsTable,
        where: 'type = ? AND status = ? AND artist = ?',
        whereArgs: [
          DownloadType.audio.index,
          DownloadStatus.completed.index,
          artist
        ],
        orderBy: 'album COLLATE NOCASE ASC, title COLLATE NOCASE ASC');
    return _mapDownloads(maps);
  }

  Future<List<DownloadItem>> getLibraryByAlbum(String album) async {
    final db = await database;
    final List<Map<String, dynamic>> maps = await db.query(_downloadsTable,
        where: 'type = ? AND status = ? AND album = ?',
        whereArgs: [
          DownloadType.audio.index,
          DownloadStatus.completed.index,
          album
        ],
        orderBy: 'title COLLATE NOCASE ASC');
    return _mapDownloads(maps);
  }

  Future<List<DownloadItem>> getLibraryAudios() async {
    final db = await database;
    final List<Map<String, dynamic>> maps = await db.query(_downloadsTable,
        where: 'type = ? AND status = ?',
        whereArgs: [DownloadType.audio.index, DownloadStatus.completed.index],
        orderBy: 'createdAt DESC');
    return _mapDownloads(maps);
  }
  // --- FIM DA FASE 2 ---

  Future<void> deleteDownload(String id) async {
    final db = await database;
    await db.delete(_downloadsTable, where: 'id = ?', whereArgs: [id]);
  }

  Future<void> insertDownloadFailureEvent({
    required String downloadId,
    required String title,
    required String source,
    required String reasonKey,
    required String errorMessage,
    DateTime? occurredAt,
  }) async {
    final db = await database;
    final when = occurredAt ?? DateTime.now();
    await db.insert(
      _downloadFailureEventsTable,
      {
        'downloadId': downloadId,
        'title': title,
        'source': source,
        'reasonKey': reasonKey,
        'errorMessage': errorMessage,
        'occurredAt': when.millisecondsSinceEpoch,
        'dayKey': _toDayKey(when),
      },
      conflictAlgorithm: ConflictAlgorithm.abort,
    );
  }

  Future<void> insertExportFailureEvent({
    required String downloadId,
    required String title,
    required String source,
    String? stage,
    String? strategy,
    String? manufacturer,
    String? brand,
    String? model,
    int? sdkInt,
    String? androidRelease,
    String? mediaType,
    String? mimeType,
    required String errorMessage,
    DateTime? occurredAt,
  }) async {
    final db = await database;
    final when = occurredAt ?? DateTime.now();
    await db.insert(
      _exportFailureEventsTable,
      {
        'downloadId': downloadId,
        'title': title,
        'source': source,
        'stage': stage,
        'strategy': strategy,
        'manufacturer': manufacturer,
        'brand': brand,
        'model': model,
        'sdkInt': sdkInt,
        'androidRelease': androidRelease,
        'mediaType': mediaType,
        'mimeType': mimeType,
        'errorMessage': errorMessage,
        'occurredAt': when.millisecondsSinceEpoch,
        'dayKey': _toDayKey(when),
      },
      conflictAlgorithm: ConflictAlgorithm.abort,
    );
  }

  Future<List<Map<String, dynamic>>> getExportFailureBreakdownByDevice({
    int limit = 20,
  }) async {
    final db = await database;
    final rows = await db.rawQuery(
      '''
      SELECT manufacturer, brand, model, sdkInt, androidRelease, COUNT(*) AS total
      FROM $_exportFailureEventsTable
      GROUP BY manufacturer, brand, model, sdkInt, androidRelease
      ORDER BY total DESC
      LIMIT ?
      ''',
      [limit],
    );

    return rows.map((row) => Map<String, dynamic>.from(row)).toList();
  }

  Future<int> getDownloadFailureTotal() async {
    final db = await database;
    final result = await db.rawQuery(
      'SELECT COUNT(*) as total FROM $_downloadFailureEventsTable',
    );
    return (result.first['total'] as int?) ?? 0;
  }

  Future<Map<String, int>> getDownloadFailureCountByReason() async {
    final db = await database;
    final rows = await db.rawQuery('''
      SELECT reasonKey, COUNT(*) AS total
      FROM $_downloadFailureEventsTable
      GROUP BY reasonKey
      ORDER BY total DESC
    ''');

    final result = <String, int>{};
    for (final row in rows) {
      final key = (row['reasonKey'] as String?) ?? 'unknown';
      final value = (row['total'] as int?) ?? 0;
      result[key] = value;
    }
    return result;
  }

  Future<Map<String, int>> getDownloadFailureHistoryByDay({
    int limitDays = 14,
  }) async {
    final db = await database;
    final rows = await db.rawQuery(
      '''
      SELECT dayKey, COUNT(*) AS total
      FROM $_downloadFailureEventsTable
      GROUP BY dayKey
      ORDER BY dayKey DESC
      LIMIT ?
      ''',
      [limitDays],
    );

    final result = <String, int>{};
    for (final row in rows) {
      final key = (row['dayKey'] as String?) ?? 'unknown';
      final value = (row['total'] as int?) ?? 0;
      result[key] = value;
    }
    return result;
  }

  Future<Map<String, dynamic>?> getLastDownloadFailureEvent() async {
    final db = await database;
    final rows = await db.query(
      _downloadFailureEventsTable,
      orderBy: 'occurredAt DESC',
      limit: 1,
    );
    if (rows.isEmpty) return null;
    return rows.first;
  }

  Future<int> pruneDownloadFailureEvents({int keepDays = 90}) async {
    final db = await database;
    final cutoff = DateTime.now()
        .subtract(Duration(days: keepDays))
        .millisecondsSinceEpoch;
    return db.delete(
      _downloadFailureEventsTable,
      where: 'occurredAt < ?',
      whereArgs: [cutoff],
    );
  }

  Future<bool> getAutoExportEnabled() async {
    final db = await database;
    final rows = await db.query(
      _settingsTable,
      where: 'key = ?',
      whereArgs: [_autoExportSettingKey],
      limit: 1,
    );

    if (rows.isEmpty) return false;
    final raw = rows.first['value']?.toString().toLowerCase();
    return raw == '1' || raw == 'true';
  }

  Future<void> setAutoExportEnabled(bool value) async {
    final db = await database;
    await db.insert(
      _settingsTable,
      {
        'key': _autoExportSettingKey,
        'value': value ? '1' : '0',
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  String _toDayKey(DateTime date) => AppDateUtils.toDayKey(date);

  // --- Search History ---

  Future<void> insertSearchQuery(String query) async {
    final normalizedQuery = query.trim();
    if (normalizedQuery.isEmpty) {
      return;
    }

    final db = await database;

    // Remove duplicatas anteriores da mesma query para trazer ao topo
    await db.delete(_searchHistoryTable,
        where: 'query = ?', whereArgs: [normalizedQuery]);

    await db.insert(_searchHistoryTable, {
      'query': normalizedQuery,
      'createdAt': DateTime.now().millisecondsSinceEpoch,
    });

    // Manter apenas os últimos registros recentes
    final rows = await db.query(_searchHistoryTable, orderBy: 'createdAt DESC');
    if (rows.length > _recentSearchHistoryLimit) {
      final idsToDelete =
          rows.skip(_recentSearchHistoryLimit).map((e) => e['id']).toList();
      await db.delete(_searchHistoryTable,
          where: 'id IN (${idsToDelete.map((_) => '?').join(',')})',
          whereArgs: idsToDelete);
    }
  }

  Future<List<String>> getRecentSearches() async {
    final db = await database;
    final rows = await db.query(_searchHistoryTable,
        orderBy: 'createdAt DESC', limit: _recentSearchHistoryLimit);
    return rows.map((e) => e['query'] as String).toList();
  }

  Future<void> deleteSearchQuery(String query) async {
    final db = await database;
    await db
        .delete(_searchHistoryTable, where: 'query = ?', whereArgs: [query]);
  }

  // --- Favorites ---

  Future<void> toggleFavorite({
    String? id,
    required String title,
    String? thumbnail,
    required String url,
    required String type,
  }) async {
    final db = await database;
    final effectiveId = id ?? url;
    final existing = await db
        .query(_favoritesTable, where: 'id = ?', whereArgs: [effectiveId]);

    if (existing.isNotEmpty) {
      await db
          .delete(_favoritesTable, where: 'id = ?', whereArgs: [effectiveId]);
      return;
    }

    await db.insert(_favoritesTable, {
      'id': effectiveId,
      'title': title,
      'thumbnail': thumbnail,
      'url': url,
      'type': type,
      'createdAt': DateTime.now().millisecondsSinceEpoch,
    });
  }

  Future<bool> isFavorite(String id) async {
    final db = await database;
    final rows =
        await db.query(_favoritesTable, where: 'id = ?', whereArgs: [id]);
    return rows.isNotEmpty;
  }

  Future<List<Map<String, dynamic>>> getFavorites() async {
    final db = await database;
    return await db.query(_favoritesTable, orderBy: 'createdAt DESC');
  }

  // --- Playlists ---

  Future<String> createPlaylist(String name, {String? description}) async {
    final db = await database;
    final id = DateTime.now().millisecondsSinceEpoch.toString();
    await db.insert(_playlistsTable, {
      'id': id,
      'name': name,
      'description': description,
      'createdAt': DateTime.now().millisecondsSinceEpoch,
    });
    return id;
  }

  Future<void> updatePlaylist(String id,
      {String? name, String? description, String? thumbnail}) async {
    final db = await database;
    final Map<String, dynamic> data = {};
    if (name != null) data['name'] = name;
    if (description != null) data['description'] = description;
    if (thumbnail != null) data['thumbnail'] = thumbnail;

    if (data.isNotEmpty) {
      await db.update(_playlistsTable, data, where: 'id = ?', whereArgs: [id]);
    }
  }

  Future<void> deletePlaylist(String id) async {
    final db = await database;
    await db.delete(_playlistsTable, where: 'id = ?', whereArgs: [id]);
    await db
        .delete(_playlistTracksTable, where: 'playlistId = ?', whereArgs: [id]);
  }

  Future<List<Map<String, dynamic>>> getPlaylists() async {
    final db = await database;
    // Otimizado: LEFT JOIN + GROUP BY em vez de subconsulta correlacionada N+1
    return await db.rawQuery('''
      SELECT p.*, COUNT(pt.trackId) as trackCount
      FROM $_playlistsTable p
      LEFT JOIN $_playlistTracksTable pt ON pt.playlistId = p.id
      GROUP BY p.id
      ORDER BY p.createdAt DESC
    ''');
  }

  Future<void> addTrackToPlaylist(String playlistId, String trackId) async {
    final db = await database;

    // Pega a última posição
    final result = await db.rawQuery(
      'SELECT MAX(position) as maxPos FROM $_playlistTracksTable WHERE playlistId = ?',
      [playlistId],
    );
    final maxPos = (result.first['maxPos'] as int?) ?? -1;

    await db.insert(
        _playlistTracksTable,
        {
          'playlistId': playlistId,
          'trackId': trackId,
          'position': maxPos + 1,
          'addedAt': DateTime.now().millisecondsSinceEpoch,
        },
        conflictAlgorithm: ConflictAlgorithm.ignore);
  }

  Future<void> removeTrackFromPlaylist(
      String playlistId, String trackId) async {
    final db = await database;
    await db.delete(_playlistTracksTable,
        where: 'playlistId = ? AND trackId = ?',
        whereArgs: [playlistId, trackId]);
  }

  Future<List<DownloadItem>> getPlaylistTracks(String playlistId) async {
    final db = await database;
    final List<Map<String, dynamic>> maps = await db.rawQuery('''
      SELECT d.* 
      FROM downloads d
      JOIN $_playlistTracksTable pt ON d.id = pt.trackId
      WHERE pt.playlistId = ?
      ORDER BY pt.position ASC
    ''', [playlistId]);

    return _mapDownloads(maps);
  }
}
