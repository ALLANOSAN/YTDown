import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:ytdown/models/download_item.dart';
import 'package:ytdown/services/database_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late DatabaseService dbService;
  const testDbName = 'ytdown_database_service_test.db';

  // Initialize ffi for testing SQflite
  setUpAll(() {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
    DatabaseService.databaseFileName = testDbName;
  });

  setUp(() async {
    dbService = DatabaseService.instance;
    await dbService.close();
    // Ponto crucial: deleta o banco de dados antigo a cada iteração do teste para ter db "novo"
    final dbPath = await getDatabasesPath();
    final path = '$dbPath/$testDbName';
    await databaseFactory.deleteDatabase(path);
  });

  group('DatabaseService - Playlists & N+1 Queries', () {
    test('getPlaylistsWithTrackCount should return correct count using JOIN',
        () async {
      // 1. Criar uma nova playlist
      final playlistId = await dbService.createPlaylist('Test Playlist',
          description: 'Playlist description');

      // 2. Inserir downloads de teste (tracks mock)
      final track1 = DownloadItem(
        id: 'vid123',
        url: 'https://youtube.com/watch?v=1',
        title: 'Song 1',
        type: DownloadType.audio,
        format: 'm4a',
        quality: 'medium',
        outputPath: '/mock/path/song1.m4a',
        status: DownloadStatus.completed,
      );

      final track2 = DownloadItem(
        id: 'vid456',
        url: 'https://youtube.com/watch?v=2',
        title: 'Song 2',
        type: DownloadType.audio,
        format: 'm4a',
        quality: 'medium',
        outputPath: '/mock/path/song2.m4a',
        status: DownloadStatus.completed,
      );

      await dbService.insertDownload(track1);
      await dbService.insertDownload(track2);

      // 3. Adicionar os tracks na playlist
      await dbService.addTrackToPlaylist(playlistId, track1.id);
      await dbService.addTrackToPlaylist(playlistId, track2.id);

      // 4. Executar a consulta que testamos
      final playlists = await dbService.getPlaylists();

      // 5. Asserts
      expect(playlists.length, 1);
      expect(playlists.first['id'], playlistId);
      expect(playlists.first['name'], 'Test Playlist');

      // Avaliar se o COUNT(*) resolveu corretamente no JOIN sem a necessidade de N subqueries
      expect(playlists.first['trackCount'], 2);
    });

    test('addTrackToPlaylist increments position property safely', () async {
      final playlistId = await dbService.createPlaylist('Order Test Playlist');

      final track1 = DownloadItem(
        id: 't1',
        url: 'u1',
        title: 'T1',
        type: DownloadType.audio,
        format: 'mp3',
        quality: 'high',
        outputPath: 'o1',
        status: DownloadStatus.completed,
      );
      final track2 = DownloadItem(
        id: 't2',
        url: 'u2',
        title: 'T2',
        type: DownloadType.audio,
        format: 'mp3',
        quality: 'high',
        outputPath: 'o2',
        status: DownloadStatus.completed,
      );

      await dbService.insertDownload(track1);
      await dbService.insertDownload(track2);

      // Add two tracks
      await dbService.addTrackToPlaylist(playlistId, track1.id);
      await dbService.addTrackToPlaylist(playlistId, track2.id);

      // Fetch from raw table to test positions
      final db = await dbService.database;
      final tracks = await db.query('playlist_tracks',
          where: 'playlistId = ?',
          whereArgs: [playlistId],
          orderBy: 'position ASC');

      expect(tracks.length, 2);
      expect(tracks[0]['trackId'], track1.id);
      expect(tracks[0]['position'], 0);
      expect(tracks[1]['trackId'], track2.id);
      expect(tracks[1]['position'], 1);
    });
  });

  group('DatabaseService - Library artist artwork fallback', () {
    test('getDistinctArtists uses albumImageUrl when artistImageUrl is empty',
        () async {
      final track = DownloadItem(
        id: 'artist-fallback-1',
        url: 'https://youtube.com/watch?v=artist-fallback-1',
        title: 'Track with album cover only',
        type: DownloadType.audio,
        format: 'm4a',
        quality: '192',
        outputPath: '/tmp/artist-fallback-1.m4a',
        status: DownloadStatus.completed,
        artist: 'Artist Fallback',
        album: 'Album Fallback',
        artistImageUrl: '',
        albumImageUrl: 'https://img.test/album-fallback.jpg',
      );

      await dbService.insertDownload(track);

      final artists = await dbService.getDistinctArtists();
      final artist =
          artists.singleWhere((item) => item['name'] == 'Artist Fallback');

      expect(artist['trackCount'], 1);
      expect(artist['imageUrl'], 'https://img.test/album-fallback.jpg');
    });

    test('getDistinctArtists prefers artistImageUrl over albumImageUrl',
        () async {
      final track = DownloadItem(
        id: 'artist-fallback-2',
        url: 'https://youtube.com/watch?v=artist-fallback-2',
        title: 'Track with artist cover',
        type: DownloadType.audio,
        format: 'm4a',
        quality: '192',
        outputPath: '/tmp/artist-fallback-2.m4a',
        status: DownloadStatus.completed,
        artist: 'Artist Preferred',
        album: 'Album Preferred',
        artistImageUrl: 'https://img.test/artist-preferred.jpg',
        albumImageUrl: 'https://img.test/album-preferred.jpg',
      );

      await dbService.insertDownload(track);

      final artists = await dbService.getDistinctArtists();
      final artist =
          artists.singleWhere((item) => item['name'] == 'Artist Preferred');

      expect(artist['trackCount'], 1);
      expect(artist['imageUrl'], 'https://img.test/artist-preferred.jpg');
    });

    test('getDistinctArtists ignores LastFM placeholder artist image',
        () async {
      final track = DownloadItem(
        id: 'artist-fallback-3',
        url: 'https://youtube.com/watch?v=artist-fallback-3',
        title: 'Track with LastFM placeholder artist image',
        type: DownloadType.audio,
        format: 'm4a',
        quality: '192',
        outputPath: '/tmp/artist-fallback-3.m4a',
        status: DownloadStatus.completed,
        artist: 'Artist Placeholder',
        album: 'Album Placeholder',
        artistImageUrl:
            'https://lastfm.freetls.fastly.net/i/u/300x300/2a96cbd8b46e442fc41c2b86b821562f.png',
        albumImageUrl: 'https://img.test/album-real.jpg',
      );

      await dbService.insertDownload(track);

      final artists = await dbService.getDistinctArtists();
      final artist =
          artists.singleWhere((item) => item['name'] == 'Artist Placeholder');

      expect(artist['trackCount'], 1);
      expect(artist['imageUrl'], 'https://img.test/album-real.jpg');
    });
  });
}
