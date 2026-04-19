import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:ytdown/models/download_item.dart';
import 'package:ytdown/services/artwork_cache_service.dart';
import 'package:ytdown/services/database_service.dart';
import 'package:ytdown/services/download_service.dart';
import 'package:ytdown/services/storage_service.dart';

DownloadItem _audioItem({
  required String id,
  required String outputPath,
  String? title,
  DownloadStatus status = DownloadStatus.completed,
  String? artist,
  String? album,
  String? artistImageUrl,
  String? albumImageUrl,
}) {
  return DownloadItem(
    id: id,
    url: 'https://youtube.com/watch?v=$id',
    title: title ?? 'Track $id',
    type: DownloadType.audio,
    format: 'm4a',
    quality: '192',
    outputPath: outputPath,
    status: status,
    artist: artist,
    album: album,
    artistImageUrl: artistImageUrl,
    albumImageUrl: albumImageUrl,
  );
}

Future<File> _createTempAudioFile({String extension = 'm4a'}) async {
  final dir = await Directory.systemTemp.createTemp('ytdown_batch_test_');
  final file = File('${dir.path}/track.$extension');
  await file.writeAsBytes(<int>[1, 2, 3, 4, 5]);
  return file;
}

Future<void> _deleteTempAudioFile(File file) async {
  final dir = file.parent;
  if (await dir.exists()) {
    await dir.delete(recursive: true);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late DatabaseService dbService;
  const testDbName = 'ytdown_download_service_batch_test.db';

  setUpAll(() {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
    DatabaseService.databaseFileName = testDbName;
  });

  setUp(() async {
    dbService = DatabaseService.instance;
    await dbService.close();
    DownloadService.instance.resetTestOverrides();
    ArtworkCacheService.instance.resetTestOverrides();
    ArtworkCacheService.instance.clear();

    final dbPath = await getDatabasesPath();
    final path = '$dbPath/$testDbName';
    await databaseFactory.deleteDatabase(path);
  });

  tearDown(() {
    DownloadService.instance.resetTestOverrides();
    ArtworkCacheService.instance.resetTestOverrides();
    ArtworkCacheService.instance.clear();
  });

  group('DownloadService batch operations', () {
    test('rewriteDownloadMetadata persists manual artwork URLs and embeds them',
        () async {
      final audioFile = await _createTempAudioFile();
      try {
        final item = _audioItem(
          id: 'manual-art-1',
          outputPath: audioFile.path,
          artist: 'Artist Manual',
          album: 'Album Manual',
          artistImageUrl: 'https://img.test/artist-old.jpg',
          albumImageUrl: null,
        );
        await dbService.insertDownload(item);

        String? rewriteArtworkUrl;

        DownloadService.instance.configureTestOverrides(
          getArtistImage: (_) async => null,
          getAlbumCover: (_, __) async => null,
          getTrackCover: (_, __) async => null,
          rewriteMetadata: ({
            required String filePath,
            required String title,
            String? artist,
            String? album,
            String? artworkUrl,
          }) async {
            rewriteArtworkUrl = artworkUrl;
            return <String, dynamic>{
              'success': true,
              'title': title,
              'artist': artist,
              'album': album,
            };
          },
        );

        final result = await DownloadService.instance.rewriteDownloadMetadata(
          downloadId: item.id,
          title: item.title,
          artist: item.artist,
          album: item.album,
          artistImageUrl: 'https://img.test/artist-manual.jpg',
          albumImageUrl: 'https://img.test/album-manual.jpg',
        );

        expect(result['success'], true);
        expect(rewriteArtworkUrl, 'https://img.test/album-manual.jpg');

        final all = await dbService.getAllDownloads();
        expect(all.length, 1);
        expect(all.first.artistImageUrl, 'https://img.test/artist-manual.jpg');
        expect(all.first.albumImageUrl, 'https://img.test/album-manual.jpg');
      } finally {
        await _deleteTempAudioFile(audioFile);
      }
    });

    test(
        'repairAudioMetadataBatch returns zero result when there are no candidates',
        () async {
      final progressEvents = <String>[];

      final result = await DownloadService.instance.repairAudioMetadataBatch(
        onProgress: (processed, total) {
          progressEvents.add('$processed/$total');
        },
      );

      expect(result.totalCandidates, 0);
      expect(result.repairedCount, 0);
      expect(result.failedCount, 0);
      expect(result.skippedCount, 0);
      expect(progressEvents, <String>['0/0']);
    });

    test('repairAudioMetadataBatch counts missing file as failed', () async {
      final missingPath =
          '/tmp/ytdown_missing_${DateTime.now().microsecondsSinceEpoch}/track.m4a';
      final item = _audioItem(id: 'm1', outputPath: missingPath);
      await dbService.insertDownload(item);

      final progressEvents = <String>[];
      final result = await DownloadService.instance.repairAudioMetadataBatch(
        onProgress: (processed, total) {
          progressEvents.add('$processed/$total');
        },
      );

      expect(result.totalCandidates, 1);
      expect(result.repairedCount, 0);
      expect(result.failedCount, 1);
      expect(result.skippedCount, 0);
      expect(progressEvents, <String>['1/1']);
    });

    test('repairAudioMetadataBatch skips candidate with blank outputPath',
        () async {
      final item = _audioItem(id: 's1', outputPath: '   ');
      await dbService.insertDownload(item);

      final result = await DownloadService.instance.repairAudioMetadataBatch();

      expect(result.totalCandidates, 1);
      expect(result.repairedCount, 0);
      expect(result.failedCount, 0);
      expect(result.skippedCount, 1);
    });

    test('addMissingArtworkBatch skips when artwork cannot be resolved',
        () async {
      final item = _audioItem(
        id: 'a1',
        outputPath: '/tmp/nonexistent.m4a',
        artist: null,
        album: null,
        artistImageUrl: null,
        albumImageUrl: null,
      );
      await dbService.insertDownload(item);

      final result = await DownloadService.instance.addMissingArtworkBatch();

      expect(result.totalCandidates, 1);
      expect(result.updatedCount, 0);
      expect(result.failedCount, 0);
      expect(result.skippedCount, 1);
    });

    test('repairAudioMetadataBatch repairs and persists metadata/artwork',
        () async {
      final audioFile = await _createTempAudioFile();
      try {
        final item = _audioItem(
          id: 'r1',
          outputPath: audioFile.path,
          artist: 'Artist X',
          album: 'Album X',
          artistImageUrl: null,
          albumImageUrl: null,
        );
        await dbService.insertDownload(item);

        String? rewriteArtworkUrl;
        var rewriteCalls = 0;

        DownloadService.instance.configureTestOverrides(
          getArtistImage: (_) async => 'https://img.test/artist-x.jpg',
          getAlbumCover: (_, __) async => 'https://img.test/album-x.jpg',
          getTrackCover: (_, __) async => null,
          rewriteMetadata: ({
            required String filePath,
            required String title,
            String? artist,
            String? album,
            String? artworkUrl,
          }) async {
            rewriteCalls++;
            rewriteArtworkUrl = artworkUrl;
            return <String, dynamic>{
              'success': true,
              'title': title,
              'artist': artist,
              'album': album,
            };
          },
        );

        final result =
            await DownloadService.instance.repairAudioMetadataBatch();

        expect(result.totalCandidates, 1);
        expect(result.repairedCount, 1);
        expect(result.failedCount, 0);
        expect(result.skippedCount, 0);
        expect(rewriteCalls, 1);
        expect(rewriteArtworkUrl, 'https://img.test/album-x.jpg');

        final all = await dbService.getAllDownloads();
        expect(all.length, 1);
        expect(all.first.artistImageUrl, 'https://img.test/artist-x.jpg');
        expect(all.first.albumImageUrl, 'https://img.test/album-x.jpg');
        expect(all.first.fileSizeBytes, 5);
      } finally {
        await _deleteTempAudioFile(audioFile);
      }
    });

    test('repairAudioMetadataBatch counts failed when rewrite fails', () async {
      final audioFile = await _createTempAudioFile();
      try {
        final item = _audioItem(
          id: 'rf1',
          outputPath: audioFile.path,
          artist: 'Artist Fail',
          album: 'Album Fail',
          artistImageUrl: null,
          albumImageUrl: null,
        );
        await dbService.insertDownload(item);

        DownloadService.instance.configureTestOverrides(
          getArtistImage: (_) async => 'https://img.test/artist-fail.jpg',
          getAlbumCover: (_, __) async => 'https://img.test/album-fail.jpg',
          getTrackCover: (_, __) async => null,
          rewriteMetadata: ({
            required String filePath,
            required String title,
            String? artist,
            String? album,
            String? artworkUrl,
          }) async {
            return <String, dynamic>{
              'success': false,
              'error': 'simulated rewrite fail',
            };
          },
        );

        final result =
            await DownloadService.instance.repairAudioMetadataBatch();

        expect(result.totalCandidates, 1);
        expect(result.repairedCount, 0);
        expect(result.failedCount, 1);
        expect(result.skippedCount, 0);

        // No caminho de falha do rewrite, o item não é persistido no DB.
        final all = await dbService.getAllDownloads();
        expect(all.length, 1);
        expect(all.first.artistImageUrl, isNull);
        expect(all.first.albumImageUrl, isNull);
      } finally {
        await _deleteTempAudioFile(audioFile);
      }
    });

    test(
        'repairAudioMetadataBatch uses track cover fallback when album is missing',
        () async {
      final audioFile = await _createTempAudioFile();
      try {
        final item = _audioItem(
          id: 'rt1',
          outputPath: audioFile.path,
          artist: 'Artist Track',
          album: 'Album Track',
          artistImageUrl: null,
          albumImageUrl: null,
        );
        await dbService.insertDownload(item);

        String? rewriteArtworkUrl;

        DownloadService.instance.configureTestOverrides(
          getArtistImage: (_) async => 'https://img.test/artist-track.jpg',
          getAlbumCover: (_, __) async => null,
          getTrackCover: (_, __) async => 'https://img.test/track-cover.jpg',
          rewriteMetadata: ({
            required String filePath,
            required String title,
            String? artist,
            String? album,
            String? artworkUrl,
          }) async {
            rewriteArtworkUrl = artworkUrl;
            return <String, dynamic>{
              'success': true,
              'title': title,
              'artist': artist,
              'album': album,
            };
          },
        );

        final result =
            await DownloadService.instance.repairAudioMetadataBatch();

        expect(result.totalCandidates, 1);
        expect(result.repairedCount, 1);
        expect(result.failedCount, 0);
        expect(result.skippedCount, 0);
        expect(rewriteArtworkUrl, 'https://img.test/track-cover.jpg');

        final all = await dbService.getAllDownloads();
        expect(all.length, 1);
        expect(all.first.artistImageUrl, 'https://img.test/artist-track.jpg');
        expect(all.first.albumImageUrl, 'https://img.test/track-cover.jpg');
      } finally {
        await _deleteTempAudioFile(audioFile);
      }
    });

    test('addMissingArtworkBatch updates and embeds when rewrite succeeds',
        () async {
      final audioFile = await _createTempAudioFile();
      try {
        final item = _audioItem(
          id: 'u1',
          outputPath: audioFile.path,
          artist: 'Artist Y',
          album: 'Album Y',
          artistImageUrl: null,
          albumImageUrl: null,
        );
        await dbService.insertDownload(item);

        var rewriteCalls = 0;

        DownloadService.instance.configureTestOverrides(
          getArtistImage: (_) async => 'https://img.test/artist-y.jpg',
          getAlbumCover: (_, __) async => 'https://img.test/album-y.jpg',
          getTrackCover: (_, __) async => null,
          rewriteMetadata: ({
            required String filePath,
            required String title,
            String? artist,
            String? album,
            String? artworkUrl,
          }) async {
            rewriteCalls++;
            return <String, dynamic>{'success': true};
          },
        );

        final result = await DownloadService.instance.addMissingArtworkBatch();

        expect(result.totalCandidates, 1);
        expect(result.updatedCount, 1);
        expect(result.failedCount, 0);
        expect(result.skippedCount, 0);
        expect(rewriteCalls, 1);

        final all = await dbService.getAllDownloads();
        expect(all.length, 1);
        expect(all.first.artistImageUrl, 'https://img.test/artist-y.jpg');
        expect(all.first.albumImageUrl, 'https://img.test/album-y.jpg');
      } finally {
        await _deleteTempAudioFile(audioFile);
      }
    });

    test(
        'addMissingArtworkBatch also refreshes items that already have artwork',
        () async {
      final audioFile = await _createTempAudioFile();
      try {
        final item = _audioItem(
          id: 'u2',
          outputPath: audioFile.path,
          artist: 'Artist Refresh',
          album: 'Album Refresh',
          artistImageUrl: 'https://img.test/artist-old.jpg',
          albumImageUrl: 'https://img.test/album-old.jpg',
        );
        await dbService.insertDownload(item);

        var rewriteCalls = 0;
        var artistLookups = 0;
        var albumLookups = 0;

        DownloadService.instance.configureTestOverrides(
          getArtistImage: (_) async {
            artistLookups++;
            return 'https://img.test/artist-new.jpg';
          },
          getAlbumCover: (_, __) async {
            albumLookups++;
            return 'https://img.test/album-new.jpg';
          },
          getTrackCover: (_, __) async => null,
          rewriteMetadata: ({
            required String filePath,
            required String title,
            String? artist,
            String? album,
            String? artworkUrl,
          }) async {
            rewriteCalls++;
            return <String, dynamic>{'success': true};
          },
        );

        final result = await DownloadService.instance.addMissingArtworkBatch();

        expect(result.totalCandidates, 1);
        expect(result.updatedCount, 1);
        expect(result.failedCount, 0);
        expect(result.skippedCount, 0);
        expect(rewriteCalls, 1);
        expect(artistLookups, 1);
        expect(albumLookups, 1);

        final all = await dbService.getAllDownloads();
        expect(all.length, 1);
        expect(all.first.artistImageUrl, 'https://img.test/artist-new.jpg');
        expect(all.first.albumImageUrl, 'https://img.test/album-new.jpg');
      } finally {
        await _deleteTempAudioFile(audioFile);
      }
    });

    test('addMissingArtworkBatch reuses artwork lookups for same artist/album',
        () async {
      final audioFileA = await _createTempAudioFile();
      final audioFileB = await _createTempAudioFile();
      try {
        final itemA = _audioItem(
          id: 'cache-a',
          outputPath: audioFileA.path,
          title: 'Shared Song',
          artist: 'Artist Cache',
          album: 'Album Cache',
          artistImageUrl: null,
          albumImageUrl: null,
        );
        final itemB = _audioItem(
          id: 'cache-b',
          outputPath: audioFileB.path,
          title: 'Shared Song',
          artist: 'Artist Cache',
          album: 'Album Cache',
          artistImageUrl: null,
          albumImageUrl: null,
        );
        await dbService.insertDownload(itemA);
        await dbService.insertDownload(itemB);

        var artistLookups = 0;
        var albumLookups = 0;
        var trackLookups = 0;

        DownloadService.instance.configureTestOverrides(
          getArtistImage: (_) async {
            artistLookups++;
            return 'https://img.test/artist-cache.jpg';
          },
          getAlbumCover: (_, __) async {
            albumLookups++;
            return 'https://img.test/album-cache.jpg';
          },
          getTrackCover: (_, __) async {
            trackLookups++;
            return null;
          },
          rewriteMetadata: ({
            required String filePath,
            required String title,
            String? artist,
            String? album,
            String? artworkUrl,
          }) async {
            return <String, dynamic>{'success': true};
          },
        );

        final result = await DownloadService.instance.addMissingArtworkBatch();

        expect(result.totalCandidates, 2);
        expect(result.updatedCount, 2);
        expect(result.failedCount, 0);
        expect(result.skippedCount, 0);
        expect(artistLookups, 1);
        expect(albumLookups, 1);
        expect(trackLookups, 0);

        final all = await dbService.getAllDownloads();
        final updatedA = all.singleWhere((d) => d.id == 'cache-a');
        final updatedB = all.singleWhere((d) => d.id == 'cache-b');
        expect(updatedA.artistImageUrl, 'https://img.test/artist-cache.jpg');
        expect(updatedA.albumImageUrl, 'https://img.test/album-cache.jpg');
        expect(updatedB.artistImageUrl, 'https://img.test/artist-cache.jpg');
        expect(updatedB.albumImageUrl, 'https://img.test/album-cache.jpg');
      } finally {
        await _deleteTempAudioFile(audioFileA);
        await _deleteTempAudioFile(audioFileB);
      }
    });

    test(
        'addMissingArtworkBatch handles two shared tracks and one different track',
        () async {
      final audioFileA = await _createTempAudioFile();
      final audioFileB = await _createTempAudioFile();
      final audioFileC = await _createTempAudioFile();
      try {
        final itemA = _audioItem(
          id: 'mixed-a',
          outputPath: audioFileA.path,
          title: 'Shared Song',
          artist: 'Artist Shared',
          album: 'Album Shared',
          artistImageUrl: null,
          albumImageUrl: null,
        );
        final itemB = _audioItem(
          id: 'mixed-b',
          outputPath: audioFileB.path,
          title: 'Shared Song 2',
          artist: 'Artist Shared',
          album: 'Album Shared',
          artistImageUrl: null,
          albumImageUrl: null,
        );
        final itemC = _audioItem(
          id: 'mixed-c',
          outputPath: audioFileC.path,
          title: 'Different Song',
          artist: 'Artist Different',
          album: 'Album Different',
          artistImageUrl: null,
          albumImageUrl: null,
        );
        await dbService.insertDownload(itemA);
        await dbService.insertDownload(itemB);
        await dbService.insertDownload(itemC);

        final artistLookupsByKey = <String, int>{};
        final albumLookupsByKey = <String, int>{};
        var trackLookups = 0;

        DownloadService.instance.configureTestOverrides(
          getArtistImage: (artist) async {
            artistLookupsByKey.update(artist, (value) => value + 1,
                ifAbsent: () => 1);
            final slug = artist.toLowerCase().replaceAll(' ', '-');
            return 'https://img.test/artist-$slug.jpg';
          },
          getAlbumCover: (artist, album) async {
            final key = '$artist|$album';
            albumLookupsByKey.update(key, (value) => value + 1,
                ifAbsent: () => 1);
            final artistSlug = artist.toLowerCase().replaceAll(' ', '-');
            final albumSlug = album.toLowerCase().replaceAll(' ', '-');
            return 'https://img.test/album-$artistSlug-$albumSlug.jpg';
          },
          getTrackCover: (_, __) async {
            trackLookups++;
            return null;
          },
          rewriteMetadata: ({
            required String filePath,
            required String title,
            String? artist,
            String? album,
            String? artworkUrl,
          }) async {
            return <String, dynamic>{'success': true};
          },
        );

        final result = await DownloadService.instance.addMissingArtworkBatch();

        expect(result.totalCandidates, 3);
        expect(result.updatedCount, 3);
        expect(result.failedCount, 0);
        expect(result.skippedCount, 0);

        expect(artistLookupsByKey['Artist Shared'], 1);
        expect(artistLookupsByKey['Artist Different'], 1);
        expect(albumLookupsByKey['Artist Shared|Album Shared'], 1);
        expect(albumLookupsByKey['Artist Different|Album Different'], 1);
        expect(trackLookups, 0);

        final all = await dbService.getAllDownloads();
        final updatedA = all.singleWhere((d) => d.id == 'mixed-a');
        final updatedB = all.singleWhere((d) => d.id == 'mixed-b');
        final updatedC = all.singleWhere((d) => d.id == 'mixed-c');

        expect(updatedA.artistImageUrl,
            'https://img.test/artist-artist-shared.jpg');
        expect(updatedA.albumImageUrl,
            'https://img.test/album-artist-shared-album-shared.jpg');
        expect(updatedB.artistImageUrl,
            'https://img.test/artist-artist-shared.jpg');
        expect(updatedB.albumImageUrl,
            'https://img.test/album-artist-shared-album-shared.jpg');
        expect(updatedC.artistImageUrl,
            'https://img.test/artist-artist-different.jpg');
        expect(updatedC.albumImageUrl,
            'https://img.test/album-artist-different-album-different.jpg');
      } finally {
        await _deleteTempAudioFile(audioFileA);
        await _deleteTempAudioFile(audioFileB);
        await _deleteTempAudioFile(audioFileC);
      }
    });

    test(
        'addMissingArtworkBatch reuses track-cover fallback lookup for same artist/title',
        () async {
      final audioFileA = await _createTempAudioFile();
      final audioFileB = await _createTempAudioFile();
      try {
        final itemA = _audioItem(
          id: 'track-cache-a',
          outputPath: audioFileA.path,
          title: 'Shared Track',
          artist: 'Artist Track Cache',
          album: 'YTDown',
          artistImageUrl: null,
          albumImageUrl: null,
        );
        final itemB = _audioItem(
          id: 'track-cache-b',
          outputPath: audioFileB.path,
          title: 'Shared Track',
          artist: 'Artist Track Cache',
          album: 'YTDown',
          artistImageUrl: null,
          albumImageUrl: null,
        );
        await dbService.insertDownload(itemA);
        await dbService.insertDownload(itemB);

        var artistLookups = 0;
        var albumLookups = 0;
        var trackLookups = 0;

        DownloadService.instance.configureTestOverrides(
          getArtistImage: (_) async {
            artistLookups++;
            return 'https://img.test/artist-track-cache.jpg';
          },
          getAlbumCover: (_, __) async {
            albumLookups++;
            return null;
          },
          getTrackCover: (_, __) async {
            trackLookups++;
            return 'https://img.test/track-cache.jpg';
          },
          rewriteMetadata: ({
            required String filePath,
            required String title,
            String? artist,
            String? album,
            String? artworkUrl,
          }) async {
            return <String, dynamic>{'success': true};
          },
        );

        final result = await DownloadService.instance.addMissingArtworkBatch();

        expect(result.totalCandidates, 2);
        expect(result.updatedCount, 2);
        expect(result.failedCount, 0);
        expect(result.skippedCount, 0);
        expect(artistLookups, 1);
        expect(albumLookups, 1);
        expect(trackLookups, 1);

        final all = await dbService.getAllDownloads();
        final updatedA = all.singleWhere((d) => d.id == 'track-cache-a');
        final updatedB = all.singleWhere((d) => d.id == 'track-cache-b');
        expect(updatedA.albumImageUrl, 'https://img.test/track-cache.jpg');
        expect(updatedB.albumImageUrl, 'https://img.test/track-cache.jpg');
      } finally {
        await _deleteTempAudioFile(audioFileA);
        await _deleteTempAudioFile(audioFileB);
      }
    });

    test(
        'addMissingArtworkBatch counts failed when embed rewrite fails and does not persist artwork urls',
        () async {
      final audioFile = await _createTempAudioFile();
      try {
        final item = _audioItem(
          id: 'f1',
          outputPath: audioFile.path,
          artist: 'Artist Z',
          album: 'Album Z',
          artistImageUrl: null,
          albumImageUrl: null,
        );
        await dbService.insertDownload(item);

        DownloadService.instance.configureTestOverrides(
          getArtistImage: (_) async => 'https://img.test/artist-z.jpg',
          getAlbumCover: (_, __) async => 'https://img.test/album-z.jpg',
          getTrackCover: (_, __) async => null,
          rewriteMetadata: ({
            required String filePath,
            required String title,
            String? artist,
            String? album,
            String? artworkUrl,
          }) async {
            return <String, dynamic>{
              'success': false,
              'error': 'simulated embed failure',
            };
          },
        );

        final result = await DownloadService.instance.addMissingArtworkBatch();

        expect(result.totalCandidates, 1);
        expect(result.updatedCount, 0);
        expect(result.failedCount, 1);
        expect(result.skippedCount, 0);

        final all = await dbService.getAllDownloads();
        expect(all.length, 1);
        expect(all.first.artistImageUrl, isNull);
        expect(all.first.albumImageUrl, isNull);
      } finally {
        await _deleteTempAudioFile(audioFile);
      }
    });

    test('exportDownload marks exported on successful export', () async {
      final audioFile = await _createTempAudioFile();
      try {
        final item = _audioItem(
          id: 'ex1',
          outputPath: audioFile.path,
          status: DownloadStatus.completed,
        );
        await dbService.insertDownload(item);

        DownloadService.instance.configureTestOverrides(
          exportToPublicCollection: ({
            required String sourcePath,
            required DownloadType type,
            String? displayName,
            bool allowUserInteractionFallback = false,
          }) async {
            return const ExportResult(
              success: true,
              exportedPath: '/public/audio/ex1.m4a',
              stage: 'primary',
              strategy: 'media_store',
            );
          },
        );

        await DownloadService.instance.exportDownload(item);

        final all = await dbService.getAllDownloads();
        final updated = all.singleWhere((d) => d.id == 'ex1');
        expect(updated.exportStatus, ExportStatus.exported);
        expect(updated.exportedPath, '/public/audio/ex1.m4a');

        final db = await dbService.database;
        final failures = await db.query(
          'export_failure_events',
          where: 'downloadId = ?',
          whereArgs: <Object?>['ex1'],
        );
        expect(failures, isEmpty);
      } finally {
        await _deleteTempAudioFile(audioFile);
      }
    });

    test('exportDownload marks failed and persists failure event', () async {
      final audioFile = await _createTempAudioFile();
      try {
        final item = _audioItem(
          id: 'ex2',
          outputPath: audioFile.path,
          status: DownloadStatus.completed,
        );
        await dbService.insertDownload(item);

        DownloadService.instance.configureTestOverrides(
          exportToPublicCollection: ({
            required String sourcePath,
            required DownloadType type,
            String? displayName,
            bool allowUserInteractionFallback = false,
          }) async {
            return const ExportResult(
              success: false,
              error: 'permission denied',
              stage: 'primary',
              strategy: 'media_store',
              diagnostics: <String, dynamic>{
                'manufacturer': 'Google',
                'brand': 'Pixel',
                'model': '8',
                'sdkInt': '34',
                'androidRelease': '14',
                'mediaType': 'audio',
                'mimeType': 'audio/mp4',
              },
            );
          },
        );

        await DownloadService.instance.exportDownload(item);

        final all = await dbService.getAllDownloads();
        final updated = all.singleWhere((d) => d.id == 'ex2');
        expect(updated.exportStatus, ExportStatus.failed);
        expect(updated.exportedPath, isNull);

        final db = await dbService.database;
        final failures = await db.query(
          'export_failure_events',
          where: 'downloadId = ?',
          whereArgs: <Object?>['ex2'],
        );

        expect(failures.length, 1);
        expect(failures.first['source'], 'manual');
        expect(failures.first['stage'], 'primary');
        expect(failures.first['strategy'], 'media_store');
        expect(failures.first['manufacturer'], 'Google');
        expect(failures.first['brand'], 'Pixel');
        expect(failures.first['model'], '8');
        expect(failures.first['sdkInt'], 34);
        expect(failures.first['androidRelease'], '14');
        expect(failures.first['mediaType'], 'audio');
        expect(failures.first['mimeType'], 'audio/mp4');
        expect(failures.first['errorMessage'], 'permission denied');
      } finally {
        await _deleteTempAudioFile(audioFile);
      }
    });

    test('exportDownload preserves previous exported state on failure',
        () async {
      final audioFile = await _createTempAudioFile();
      try {
        final item = _audioItem(
          id: 'ex3',
          outputPath: audioFile.path,
          status: DownloadStatus.completed,
        ).copyWith(
          exportStatus: ExportStatus.exported,
          exportedPath: '/public/audio/old.m4a',
        );
        await dbService.insertDownload(item);

        DownloadService.instance.configureTestOverrides(
          exportToPublicCollection: ({
            required String sourcePath,
            required DownloadType type,
            String? displayName,
            bool allowUserInteractionFallback = false,
          }) async {
            return const ExportResult(
              success: false,
              error: 'storage unavailable',
              stage: 'fallback',
              strategy: 'downloads',
            );
          },
        );

        await DownloadService.instance.exportDownload(item);

        final all = await dbService.getAllDownloads();
        final updated = all.singleWhere((d) => d.id == 'ex3');
        expect(updated.exportStatus, ExportStatus.exported);
        expect(updated.exportedPath, '/public/audio/old.m4a');

        final db = await dbService.database;
        final failures = await db.query(
          'export_failure_events',
          where: 'downloadId = ?',
          whereArgs: <Object?>['ex3'],
        );

        expect(failures.length, 1);
        expect(failures.first['source'], 'manual');
        expect(failures.first['stage'], 'fallback');
        expect(failures.first['strategy'], 'downloads');
        expect(failures.first['errorMessage'], 'storage unavailable');
      } finally {
        await _deleteTempAudioFile(audioFile);
      }
    });

    test('exportDownload ignores non-completed item', () async {
      final audioFile = await _createTempAudioFile();
      try {
        final item = _audioItem(
          id: 'ex4',
          outputPath: audioFile.path,
          status: DownloadStatus.downloading,
        );
        await dbService.insertDownload(item);

        var exportCalls = 0;
        DownloadService.instance.configureTestOverrides(
          exportToPublicCollection: ({
            required String sourcePath,
            required DownloadType type,
            String? displayName,
            bool allowUserInteractionFallback = false,
          }) async {
            exportCalls++;
            return const ExportResult(success: true, exportedPath: '/ignored');
          },
        );

        await DownloadService.instance.exportDownload(item);

        expect(exportCalls, 0);

        final all = await dbService.getAllDownloads();
        final updated = all.singleWhere((d) => d.id == 'ex4');
        expect(updated.exportStatus, ExportStatus.pending);
        expect(updated.exportedPath, isNull);

        final db = await dbService.database;
        final failures = await db.query(
          'export_failure_events',
          where: 'downloadId = ?',
          whereArgs: <Object?>['ex4'],
        );
        expect(failures, isEmpty);
      } finally {
        await _deleteTempAudioFile(audioFile);
      }
    });

    test('applyAutoExportPolicyForTest exports automatically when enabled',
        () async {
      await dbService.setAutoExportEnabled(true);

      var exportCalls = 0;
      bool? fallbackFlag;

      DownloadService.instance.configureTestOverrides(
        exportToPublicCollection: ({
          required String sourcePath,
          required DownloadType type,
          String? displayName,
          bool allowUserInteractionFallback = false,
        }) async {
          exportCalls++;
          fallbackFlag = allowUserInteractionFallback;
          return const ExportResult(
            success: true,
            exportedPath: '/public/audio/auto-success.m4a',
            stage: 'primary',
            strategy: 'media_store',
          );
        },
      );

      final item = _audioItem(
        id: 'auto1',
        outputPath: '/tmp/auto1.m4a',
        status: DownloadStatus.completed,
      );

      final updated =
          await DownloadService.instance.applyAutoExportPolicyForTest(item);

      expect(exportCalls, 1);
      expect(fallbackFlag, isFalse);
      expect(updated.exportStatus, ExportStatus.exported);
      expect(updated.exportedPath, '/public/audio/auto-success.m4a');

      final db = await dbService.database;
      final failures = await db.query(
        'export_failure_events',
        where: 'downloadId = ?',
        whereArgs: <Object?>['auto1'],
      );
      expect(failures, isEmpty);
    });

    test('applyAutoExportPolicyForTest skips when auto-export is disabled',
        () async {
      await dbService.setAutoExportEnabled(false);

      var exportCalls = 0;
      DownloadService.instance.configureTestOverrides(
        exportToPublicCollection: ({
          required String sourcePath,
          required DownloadType type,
          String? displayName,
          bool allowUserInteractionFallback = false,
        }) async {
          exportCalls++;
          return const ExportResult(
            success: true,
            exportedPath: '/public/audio/should-not-run.m4a',
          );
        },
      );

      final item = _audioItem(
        id: 'auto2',
        outputPath: '/tmp/auto2.m4a',
        status: DownloadStatus.completed,
      );

      final updated =
          await DownloadService.instance.applyAutoExportPolicyForTest(item);

      expect(exportCalls, 0);
      expect(updated.exportStatus, ExportStatus.pending);
      expect(updated.exportedPath, isNull);
    });

    test(
        'applyAutoExportPolicyForTest preserves exported state on failure and logs auto source',
        () async {
      await dbService.setAutoExportEnabled(true);

      DownloadService.instance.configureTestOverrides(
        exportToPublicCollection: ({
          required String sourcePath,
          required DownloadType type,
          String? displayName,
          bool allowUserInteractionFallback = false,
        }) async {
          return const ExportResult(
            success: false,
            error: 'auto export failed',
            stage: 'fallback',
            strategy: 'downloads',
            diagnostics: <String, dynamic>{
              'manufacturer': 'Samsung',
              'brand': 'Galaxy',
              'model': 'S24',
              'sdkInt': '35',
              'androidRelease': '15',
            },
          );
        },
      );

      final item = _audioItem(
        id: 'auto3',
        outputPath: '/tmp/auto3.m4a',
        status: DownloadStatus.completed,
      ).copyWith(
        exportStatus: ExportStatus.exported,
        exportedPath: '/public/audio/previous.m4a',
      );

      final updated =
          await DownloadService.instance.applyAutoExportPolicyForTest(item);

      expect(updated.exportStatus, ExportStatus.exported);
      expect(updated.exportedPath, '/public/audio/previous.m4a');

      final db = await dbService.database;
      final failures = await db.query(
        'export_failure_events',
        where: 'downloadId = ?',
        whereArgs: <Object?>['auto3'],
      );

      expect(failures.length, 1);
      expect(failures.first['source'], 'auto');
      expect(failures.first['stage'], 'fallback');
      expect(failures.first['strategy'], 'downloads');
      expect(failures.first['manufacturer'], 'Samsung');
      expect(failures.first['brand'], 'Galaxy');
      expect(failures.first['model'], 'S24');
      expect(failures.first['sdkInt'], 35);
      expect(failures.first['androidRelease'], '15');
      expect(failures.first['errorMessage'], 'auto export failed');
    });

    test('startDownload success path triggers auto-export when enabled',
        () async {
      await dbService.setAutoExportEnabled(true);

      final downloadsDir =
          await Directory.systemTemp.createTemp('ytdown_flow_success_');
      try {
        var startedCalls = 0;
        var completedCalls = 0;
        var failedCalls = 0;
        var rewriteCalls = 0;
        var exportCalls = 0;
        bool? exportFallbackFlag;

        DownloadService.instance.configureTestOverrides(
          getSandboxDownloadsDir: (_) async => downloadsDir,
          showDownloadStarted: (_, __) {
            startedCalls++;
          },
          showDownloadCompleted: (_, __) {
            completedCalls++;
          },
          showDownloadFailed: (_, __, ___) {
            failedCalls++;
          },
          getArtistImage: (_) async => null,
          getAlbumCover: (_, __) async => null,
          getTrackCover: (_, __) async => null,
          downloadVideo: ({
            required String url,
            required String outputPath,
            required String type,
            required String format,
            required String quality,
            String? artist,
            String? album,
            String? artworkUrl,
          }) async {
            final file = File(outputPath);
            await file.parent.create(recursive: true);
            await file.writeAsBytes(<int>[1, 2, 3, 4, 5, 6, 7]);
            return <String, dynamic>{
              'success': true,
              'filename': outputPath,
              'detected_title': 'Flow Song',
              'detected_artist': artist,
              'detected_album': album,
            };
          },
          rewriteMetadata: ({
            required String filePath,
            required String title,
            String? artist,
            String? album,
            String? artworkUrl,
          }) async {
            rewriteCalls++;
            return <String, dynamic>{
              'success': true,
              'title': title,
              'artist': artist,
              'album': album,
            };
          },
          exportToPublicCollection: ({
            required String sourcePath,
            required DownloadType type,
            String? displayName,
            bool allowUserInteractionFallback = false,
          }) async {
            exportCalls++;
            exportFallbackFlag = allowUserInteractionFallback;
            return const ExportResult(
              success: true,
              exportedPath: '/public/audio/flow-success.m4a',
              stage: 'primary',
              strategy: 'media_store',
            );
          },
        );

        await DownloadService.instance.startDownload(
          url: 'https://youtube.com/watch?v=flow-success',
          title: 'Flow Success',
          type: DownloadType.audio,
          format: 'm4a',
          quality: '192',
          artist: 'Flow Artist',
          album: 'Flow Album',
        );

        final all = await dbService.getAllDownloads();
        expect(all.length, 1);

        final item = all.first;
        expect(item.status, DownloadStatus.completed);
        expect(item.progress, 1.0);
        expect(item.exportStatus, ExportStatus.exported);
        expect(item.exportedPath, '/public/audio/flow-success.m4a');
        expect(item.fileSizeBytes, 7);

        expect(startedCalls, 1);
        expect(completedCalls, 1);
        expect(failedCalls, 0);
        expect(rewriteCalls, 0);
        expect(exportCalls, 1);
        expect(exportFallbackFlag, isFalse);
      } finally {
        if (await downloadsDir.exists()) {
          await downloadsDir.delete(recursive: true);
        }
      }
    });

    test('startDownload failure path does not trigger auto-export', () async {
      await dbService.setAutoExportEnabled(true);

      final downloadsDir =
          await Directory.systemTemp.createTemp('ytdown_flow_failure_');
      try {
        var startedCalls = 0;
        var completedCalls = 0;
        var failedCalls = 0;
        var exportCalls = 0;

        DownloadService.instance.configureTestOverrides(
          getSandboxDownloadsDir: (_) async => downloadsDir,
          showDownloadStarted: (_, __) {
            startedCalls++;
          },
          showDownloadCompleted: (_, __) {
            completedCalls++;
          },
          showDownloadFailed: (_, __, ___) {
            failedCalls++;
          },
          getArtistImage: (_) async => null,
          getAlbumCover: (_, __) async => null,
          getTrackCover: (_, __) async => null,
          downloadVideo: ({
            required String url,
            required String outputPath,
            required String type,
            required String format,
            required String quality,
            String? artist,
            String? album,
            String? artworkUrl,
          }) async {
            return <String, dynamic>{
              'success': false,
              'error': 'simulated download failure',
            };
          },
          exportToPublicCollection: ({
            required String sourcePath,
            required DownloadType type,
            String? displayName,
            bool allowUserInteractionFallback = false,
          }) async {
            exportCalls++;
            return const ExportResult(success: true, exportedPath: '/ignored');
          },
        );

        await DownloadService.instance.startDownload(
          url: 'https://youtube.com/watch?v=flow-failure',
          title: 'Flow Failure',
          type: DownloadType.audio,
          format: 'm4a',
          quality: '192',
          artist: 'Flow Artist',
          album: 'Flow Album',
        );

        final all = await dbService.getAllDownloads();
        expect(all.length, 1);

        final item = all.first;
        expect(item.status, DownloadStatus.failed);
        expect(item.errorMessage, contains('simulated download failure'));
        expect(item.exportStatus, ExportStatus.pending);
        expect(item.exportedPath, isNull);

        expect(startedCalls, 1);
        expect(completedCalls, 0);
        expect(failedCalls, 1);
        expect(exportCalls, 0);
      } finally {
        if (await downloadsDir.exists()) {
          await downloadsDir.delete(recursive: true);
        }
      }
    });

    test('rewriteDownloadMetadata updates metadata manually for audio item',
        () async {
      final audioFile = await _createTempAudioFile();
      try {
        final item = _audioItem(
          id: 'manual1',
          outputPath: audioFile.path,
          title: 'Old title',
          artist: 'Old artist',
          album: 'Old album',
          status: DownloadStatus.completed,
        );
        await dbService.insertDownload(item);

        var rewriteCalls = 0;

        DownloadService.instance.configureTestOverrides(
          getArtistImage: (_) async => null,
          getAlbumCover: (_, __) async => null,
          getTrackCover: (_, __) async => null,
          rewriteMetadata: ({
            required String filePath,
            required String title,
            String? artist,
            String? album,
            String? artworkUrl,
          }) async {
            rewriteCalls++;
            return <String, dynamic>{
              'success': true,
              'title': title,
              'artist': artist,
              'album': album,
            };
          },
        );

        final result = await DownloadService.instance.rewriteDownloadMetadata(
          downloadId: 'manual1',
          title: 'New title',
          artist: 'New artist',
          album: 'New album',
        );

        expect(result['success'], isTrue);
        expect(rewriteCalls, 1);

        final all = await dbService.getAllDownloads();
        final updated = all.singleWhere((d) => d.id == 'manual1');
        expect(updated.title, 'New title');
        expect(updated.artist, 'New artist');
        expect(updated.album, 'New album');
      } finally {
        await _deleteTempAudioFile(audioFile);
      }
    });

    test('rewriteDownloadMetadata returns error when item does not exist',
        () async {
      final result = await DownloadService.instance.rewriteDownloadMetadata(
        downloadId: 'missing-id',
        title: 'Any title',
        artist: 'Any artist',
        album: 'Any album',
      );

      expect(result['success'], isFalse);
      expect(result['stage'], 'load_download');
      expect(result['error'] as String, contains('não encontrado'));
    });

    test('rewriteArtistMetadataBatch updates all tracks from same artist',
        () async {
      final audioFileA = await _createTempAudioFile();
      final audioFileB = await _createTempAudioFile();
      final audioFileOther = await _createTempAudioFile();
      try {
        final itemA = _audioItem(
          id: 'batch-artist-a',
          outputPath: audioFileA.path,
          title: 'Song A',
          artist: 'Artist Batch',
          album: 'Album A',
          status: DownloadStatus.completed,
        );
        final itemB = _audioItem(
          id: 'batch-artist-b',
          outputPath: audioFileB.path,
          title: 'Song B',
          artist: 'artist batch',
          album: 'Album B',
          status: DownloadStatus.completed,
        );
        final itemOther = _audioItem(
          id: 'batch-artist-c',
          outputPath: audioFileOther.path,
          title: 'Song C',
          artist: 'Another Artist',
          album: 'Album C',
          status: DownloadStatus.completed,
        );

        await dbService.insertDownload(itemA);
        await dbService.insertDownload(itemB);
        await dbService.insertDownload(itemOther);

        var rewriteCalls = 0;
        DownloadService.instance.configureTestOverrides(
          getArtistImage: (_) async => null,
          getAlbumCover: (_, __) async => null,
          getTrackCover: (_, __) async => null,
          rewriteMetadata: ({
            required String filePath,
            required String title,
            String? artist,
            String? album,
            String? artworkUrl,
          }) async {
            rewriteCalls++;
            return <String, dynamic>{
              'success': true,
              'title': title,
              'artist': artist,
              'album': album,
            };
          },
        );

        final result =
            await DownloadService.instance.rewriteArtistMetadataBatch(
          currentArtist: 'Artist Batch',
          newArtist: 'Artist Renamed',
        );

        expect(result['success'], isTrue);
        expect(result['totalCandidates'], 2);
        expect(result['updatedCount'], 2);
        expect(result['failedCount'], 0);
        expect(rewriteCalls, 2);

        final all = await dbService.getAllDownloads();
        final updatedA = all.singleWhere((d) => d.id == 'batch-artist-a');
        final updatedB = all.singleWhere((d) => d.id == 'batch-artist-b');
        final untouched = all.singleWhere((d) => d.id == 'batch-artist-c');

        expect(updatedA.artist, 'Artist Renamed');
        expect(updatedB.artist, 'Artist Renamed');
        expect(untouched.artist, 'Another Artist');
      } finally {
        await _deleteTempAudioFile(audioFileA);
        await _deleteTempAudioFile(audioFileB);
        await _deleteTempAudioFile(audioFileOther);
      }
    });

    test('rewriteArtistMetadataBatch applies manual artist image to all tracks',
        () async {
      final audioFileA = await _createTempAudioFile();
      final audioFileB = await _createTempAudioFile();
      try {
        final itemA = _audioItem(
          id: 'batch-artist-img-a',
          outputPath: audioFileA.path,
          title: 'Song A',
          artist: 'Artist Image Batch',
          album: 'Album A',
          status: DownloadStatus.completed,
          artistImageUrl: 'https://img.test/artist-old.jpg',
        );
        final itemB = _audioItem(
          id: 'batch-artist-img-b',
          outputPath: audioFileB.path,
          title: 'Song B',
          artist: 'artist image batch',
          album: 'Album B',
          status: DownloadStatus.completed,
          artistImageUrl: null,
        );

        await dbService.insertDownload(itemA);
        await dbService.insertDownload(itemB);

        final rewriteArtworkUrls = <String?>[];
        DownloadService.instance.configureTestOverrides(
          getArtistImage: (_) async => null,
          getAlbumCover: (_, __) async => null,
          getTrackCover: (_, __) async => null,
          rewriteMetadata: ({
            required String filePath,
            required String title,
            String? artist,
            String? album,
            String? artworkUrl,
          }) async {
            rewriteArtworkUrls.add(artworkUrl);
            return <String, dynamic>{
              'success': true,
              'title': title,
              'artist': artist,
              'album': album,
            };
          },
        );

        final result =
            await DownloadService.instance.rewriteArtistMetadataBatch(
          currentArtist: 'Artist Image Batch',
          newArtist: 'Artist Image Batch Renamed',
          newArtistImageUrl: 'https://img.test/artist-manual-batch.jpg',
        );

        expect(result['success'], isTrue);
        expect(result['totalCandidates'], 2);
        expect(result['updatedCount'], 2);
        expect(result['failedCount'], 0);
        expect(rewriteArtworkUrls,
            everyElement('https://img.test/artist-manual-batch.jpg'));

        final all = await dbService.getAllDownloads();
        final updatedA = all.singleWhere((d) => d.id == 'batch-artist-img-a');
        final updatedB = all.singleWhere((d) => d.id == 'batch-artist-img-b');

        expect(updatedA.artist, 'Artist Image Batch Renamed');
        expect(updatedB.artist, 'Artist Image Batch Renamed');
        expect(updatedA.artistImageUrl,
            'https://img.test/artist-manual-batch.jpg');
        expect(updatedB.artistImageUrl,
            'https://img.test/artist-manual-batch.jpg');
      } finally {
        await _deleteTempAudioFile(audioFileA);
        await _deleteTempAudioFile(audioFileB);
      }
    });

    test('rewriteAlbumMetadataBatch updates all tracks from same album',
        () async {
      final audioFileA = await _createTempAudioFile();
      final audioFileB = await _createTempAudioFile();
      final audioFileOther = await _createTempAudioFile();
      try {
        final itemA = _audioItem(
          id: 'batch-album-a',
          outputPath: audioFileA.path,
          title: 'Album Song A',
          artist: 'Artist A',
          album: 'Album Batch',
          status: DownloadStatus.completed,
          albumImageUrl: 'https://img.test/album-old.jpg',
        );
        final itemB = _audioItem(
          id: 'batch-album-b',
          outputPath: audioFileB.path,
          title: 'Album Song B',
          artist: 'Artist B',
          album: 'album batch',
          status: DownloadStatus.completed,
          albumImageUrl: null,
        );
        final itemOther = _audioItem(
          id: 'batch-album-c',
          outputPath: audioFileOther.path,
          title: 'Other Album Song',
          artist: 'Artist C',
          album: 'Other Album',
          status: DownloadStatus.completed,
        );

        await dbService.insertDownload(itemA);
        await dbService.insertDownload(itemB);
        await dbService.insertDownload(itemOther);

        final rewriteArtworkUrls = <String?>[];
        DownloadService.instance.configureTestOverrides(
          getArtistImage: (_) async => null,
          getAlbumCover: (_, __) async => null,
          getTrackCover: (_, __) async => null,
          rewriteMetadata: ({
            required String filePath,
            required String title,
            String? artist,
            String? album,
            String? artworkUrl,
          }) async {
            rewriteArtworkUrls.add(artworkUrl);
            return <String, dynamic>{
              'success': true,
              'title': title,
              'artist': artist,
              'album': album,
            };
          },
        );

        final result = await DownloadService.instance.rewriteAlbumMetadataBatch(
          currentAlbum: 'Album Batch',
          newAlbum: 'Album Renamed',
          newAlbumImageUrl: 'https://img.test/album-manual-batch.jpg',
        );

        expect(result['success'], isTrue);
        expect(result['totalCandidates'], 2);
        expect(result['updatedCount'], 2);
        expect(result['failedCount'], 0);
        expect(rewriteArtworkUrls,
            everyElement('https://img.test/album-manual-batch.jpg'));

        final all = await dbService.getAllDownloads();
        final updatedA = all.singleWhere((d) => d.id == 'batch-album-a');
        final updatedB = all.singleWhere((d) => d.id == 'batch-album-b');
        final untouched = all.singleWhere((d) => d.id == 'batch-album-c');

        expect(updatedA.album, 'Album Renamed');
        expect(updatedB.album, 'Album Renamed');
        expect(untouched.album, 'Other Album');
        expect(
            updatedA.albumImageUrl, 'https://img.test/album-manual-batch.jpg');
        expect(
            updatedB.albumImageUrl, 'https://img.test/album-manual-batch.jpg');
      } finally {
        await _deleteTempAudioFile(audioFileA);
        await _deleteTempAudioFile(audioFileB);
        await _deleteTempAudioFile(audioFileOther);
      }
    });

    test(
        'rewriteArtistMetadataBatch rewrites even when new artist matches current artist',
        () async {
      final audioFileA = await _createTempAudioFile();
      final audioFileB = await _createTempAudioFile();
      try {
        final itemA = _audioItem(
          id: 'batch-same-a',
          outputPath: audioFileA.path,
          title: 'Song Same A',
          artist: 'Artist Same',
          album: 'Album A',
          status: DownloadStatus.completed,
        );
        final itemB = _audioItem(
          id: 'batch-same-b',
          outputPath: audioFileB.path,
          title: 'Song Same B',
          artist: 'artist same',
          album: 'Album B',
          status: DownloadStatus.completed,
        );

        await dbService.insertDownload(itemA);
        await dbService.insertDownload(itemB);

        var rewriteCalls = 0;
        DownloadService.instance.configureTestOverrides(
          getArtistImage: (_) async => null,
          getAlbumCover: (_, __) async => null,
          getTrackCover: (_, __) async => null,
          rewriteMetadata: ({
            required String filePath,
            required String title,
            String? artist,
            String? album,
            String? artworkUrl,
          }) async {
            rewriteCalls++;
            return <String, dynamic>{
              'success': true,
              'title': title,
              'artist': artist,
              'album': album,
            };
          },
        );

        final result =
            await DownloadService.instance.rewriteArtistMetadataBatch(
          currentArtist: 'Artist Same',
          newArtist: 'Artist Same',
        );

        expect(result['success'], isTrue);
        expect(result['totalCandidates'], 2);
        expect(result['updatedCount'], 2);
        expect(result['failedCount'], 0);
        expect(result['skippedCount'], 0);
        expect(rewriteCalls, 2);
      } finally {
        await _deleteTempAudioFile(audioFileA);
        await _deleteTempAudioFile(audioFileB);
      }
    });

    test('rewriteArtistMetadataBatch returns validation error for blank artist',
        () async {
      final result = await DownloadService.instance.rewriteArtistMetadataBatch(
        currentArtist: '   ',
        newArtist: 'Any Artist',
      );

      expect(result['success'], isFalse);
      expect(result['stage'], 'validate_current_artist');
    });

    test('rewriteAlbumMetadataBatch returns validation error for blank album',
        () async {
      final result = await DownloadService.instance.rewriteAlbumMetadataBatch(
        currentAlbum: '   ',
        newAlbum: 'Any Album',
      );

      expect(result['success'], isFalse);
      expect(result['stage'], 'validate_current_album');
    });
  });
}
