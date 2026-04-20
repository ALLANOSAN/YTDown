import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ytdown/models/download_item.dart';
import 'package:ytdown/providers/download_provider.dart';
import 'package:ytdown/services/download_feed_service.dart';

class _FakeDownloadService implements DownloadFeedService {
  _FakeDownloadService({
    required List<DownloadItem> initialItems,
    required Stream<DownloadItem?> updatesStream,
  })  : _items = List<DownloadItem>.from(initialItems),
        _updatesStream = updatesStream;

  List<DownloadItem> _items;
  final Stream<DownloadItem?> _updatesStream;
  int getAllDownloadsCallCount = 0;
  Completer<void>? _blockGetAllDownloads;

  void setItems(List<DownloadItem> items) {
    _items = List<DownloadItem>.from(items);
  }

  void setGetAllDownloadsBlock(Completer<void>? blocker) {
    _blockGetAllDownloads = blocker;
  }

  @override
  Stream<DownloadItem?> updates() => _updatesStream;

  @override
  Future<List<DownloadItem>> getAllDownloads() async {
    getAllDownloadsCallCount++;
    final blocker = _blockGetAllDownloads;
    if (blocker != null) {
      await blocker.future;
    }
    return List<DownloadItem>.from(_items);
  }
}

DownloadItem _item({
  required String id,
  required String title,
  DownloadStatus status = DownloadStatus.queued,
  double progress = 0.0,
}) {
  return DownloadItem(
    id: id,
    url: 'https://youtube.com/watch?v=$id',
    title: title,
    type: DownloadType.audio,
    format: 'm4a',
    quality: '192',
    outputPath: '/tmp/$id.m4a',
    status: status,
    progress: progress,
  );
}

Future<void> _pumpQueue() async {
  await Future<void>.delayed(Duration.zero);
  await Future<void>.delayed(Duration.zero);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  group('DownloadsNotifier', () {
    late StreamController<DownloadItem?> updatesController;
    late StreamController<Map<int, int>> progressController;
    late _FakeDownloadService fakeService;
    late ProviderContainer container;

    setUp(() async {
      updatesController = StreamController<DownloadItem?>.broadcast();
      progressController = StreamController<Map<int, int>>.broadcast();

      fakeService = _FakeDownloadService(
        initialItems: <DownloadItem>[
          _item(id: 'a1', title: 'Track A', status: DownloadStatus.downloading),
        ],
        updatesStream: updatesController.stream,
      );

      container = ProviderContainer(
        overrides: [
          downloadServiceProvider.overrideWithValue(fakeService),
          downloadUpdatesProvider.overrideWith(
            (ref) => updatesController.stream,
          ),
          downloadProgressProvider.overrideWith(
            (ref) => progressController.stream,
          ),
        ],
      );

      await container.read(downloadsProvider.future);
    });

    tearDown(() async {
      container.dispose();
      await updatesController.close();
      await progressController.close();
    });

    test('atualiza item existente quando recebe update stream', () async {
      final updated = _item(
        id: 'a1',
        title: 'Track A Remastered',
        status: DownloadStatus.completed,
        progress: 1.0,
      );

      updatesController.add(updated);
      await _pumpQueue();

      final items = container.read(downloadsProvider).value!;
      expect(items.length, 1);
      expect(items.first.id, 'a1');
      expect(items.first.title, 'Track A Remastered');
      expect(items.first.status, DownloadStatus.completed);
      expect(items.first.progress, 1.0);
    });

    test('insere novo item no topo quando recebe update de id novo', () async {
      final incoming = _item(id: 'b2', title: 'Track B');

      updatesController.add(incoming);
      await _pumpQueue();

      final items = container.read(downloadsProvider).value!;
      expect(items.length, 2);
      expect(items.first.id, 'b2');
      expect(items[1].id, 'a1');
    });

    test('nao emite estado para update idempotente', () async {
      final before = container.read(downloadsProvider).value!;

      final duplicate = _item(
        id: 'a1',
        title: 'Track A',
        status: DownloadStatus.downloading,
        progress: 0.0,
      );

      updatesController.add(duplicate);
      await _pumpQueue();

      final after = container.read(downloadsProvider).value!;
      expect(identical(before, after), isTrue);
    });

    test('evento null no stream dispara recarga da lista', () async {
      fakeService.setItems(<DownloadItem>[
        _item(id: 'c3', title: 'Track C', status: DownloadStatus.completed),
      ]);

      updatesController.add(null);
      await _pumpQueue();

      final items = container.read(downloadsProvider).value!;
      expect(items.length, 1);
      expect(items.first.id, 'c3');
      expect(items.first.title, 'Track C');
    });

    test('coalesce de recarga evita getAllDownloads concorrente', () async {
      final initialCalls = fakeService.getAllDownloadsCallCount;
      final blocker = Completer<void>();
      fakeService.setGetAllDownloadsBlock(blocker);
      fakeService.setItems(<DownloadItem>[
        _item(id: 'r1', title: 'Reloaded', status: DownloadStatus.completed),
      ]);

      updatesController.add(null);
      updatesController.add(null);
      updatesController.add(null);
      await _pumpQueue();

      expect(fakeService.getAllDownloadsCallCount, initialCalls + 1);

      blocker.complete();
      await _pumpQueue();
      fakeService.setGetAllDownloadsBlock(null);

      final items = container.read(downloadsProvider).value!;
      expect(items.length, 1);
      expect(items.first.id, 'r1');
      expect(items.first.title, 'Reloaded');
    });
  });
}
