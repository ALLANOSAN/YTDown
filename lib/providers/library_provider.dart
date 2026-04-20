import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/download_item.dart';
import '../services/library_service.dart';
import '../services/file_system_scanner_service.dart';
import 'download_provider.dart';

final libraryServiceProvider = Provider<LibraryService>((ref) {
  return LibraryService.instance;
});

class LibraryState {
  final List<DownloadItem> audios;
  final List<Map<String, dynamic>> artists;
  final List<Map<String, dynamic>> albums;
  final String searchQuery;
  final bool isScanning;
  final bool initialScanComplete;

  LibraryState({
    required this.audios,
    required this.artists,
    required this.albums,
    this.searchQuery = '',
    this.isScanning = false,
    this.initialScanComplete = false,
  });

  LibraryState copyWith({
    List<DownloadItem>? audios,
    List<Map<String, dynamic>>? artists,
    List<Map<String, dynamic>>? albums,
    String? searchQuery,
    bool? isScanning,
    bool? initialScanComplete,
  }) {
    return LibraryState(
      audios: audios ?? this.audios,
      artists: artists ?? this.artists,
      albums: albums ?? this.albums,
      searchQuery: searchQuery ?? this.searchQuery,
      isScanning: isScanning ?? this.isScanning,
      initialScanComplete: initialScanComplete ?? this.initialScanComplete,
    );
  }
}

class LibraryNotifier extends AsyncNotifier<LibraryState> {
  bool _isSoftRefreshing = false;

  String get _currentSearchQuery => state.value?.searchQuery ?? '';

  bool get _isInitialScanComplete => state.value?.initialScanComplete ?? false;

  void _markScanningInCurrentState() {
    final current = state.value;
    if (current == null) {
      return;
    }
    state = AsyncData(current.copyWith(isScanning: true));
  }

  bool _shouldRefreshFromDownloadUpdate(DownloadItem? item) {
    if (item == null) {
      return true;
    }
    return item.status == DownloadStatus.completed;
  }

  Future<LibraryState> _loadLibraryContent({
    required LibraryService service,
    required String query,
    required bool scanComplete,
  }) async {
    final audios =
        query.isEmpty ? await service.getSongs() : await service.search(query);
    final artists = await service.getArtistsWithMetadata(query: query);
    final albums = await service.getAlbumsWithMetadata(query: query);

    return LibraryState(
      audios: audios,
      artists: artists,
      albums: albums,
      searchQuery: query,
      isScanning: false,
      initialScanComplete: scanComplete,
    );
  }

  @override
  Future<LibraryState> build() async {
    ref.listen<AsyncValue<DownloadItem?>>(downloadUpdatesProvider,
        (previous, next) {
      if (next.hasValue && _shouldRefreshFromDownloadUpdate(next.value)) {
        unawaited(_softRefreshFromDownloadUpdate());
      }
    });

    return _loadData();
  }

  Future<void> _softRefreshFromDownloadUpdate() async {
    if (_isSoftRefreshing) return;

    _isSoftRefreshing = true;
    try {
      final nextState = await _loadData(forceScan: false);
      if (ref.mounted) {
        state = AsyncData(nextState);
      }
    } catch (e, stack) {
      if (ref.mounted) {
        state = AsyncError(e, stack);
      }
    } finally {
      _isSoftRefreshing = false;
    }
  }

  Future<LibraryState> _loadData({bool forceScan = true}) async {
    var scanComplete = _isInitialScanComplete;
    final query = _currentSearchQuery;

    if (forceScan || !scanComplete) {
      _markScanningInCurrentState();
      final scannerService = FileSystemScannerService.instance;

      final result = await scannerService.performFullScan();
      if (result.hasOrphans) {
        debugPrint(
            '✅ ${result.filesRegistered} arquivos órfãos encontrados e registrados!');
      }
      scanComplete = true;
    }

    final service = ref.read(libraryServiceProvider);

    return _loadLibraryContent(
      service: service,
      query: query,
      scanComplete: scanComplete,
    );
  }

  Future<void> refresh() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() => _loadData(forceScan: true));
  }

  Future<void> setSearchQuery(String query) async {
    if (!state.hasValue) return;

    final currentState = state.value!;
    if (currentState.searchQuery == query) return;

    state = AsyncData(currentState.copyWith(
      searchQuery: query,
    ));

    final nextState = await _loadData(forceScan: false);
    if (ref.mounted) {
      state = AsyncData(nextState);
    }
  }
}

final libraryProvider =
    AsyncNotifierProvider<LibraryNotifier, LibraryState>(() {
  return LibraryNotifier();
});
