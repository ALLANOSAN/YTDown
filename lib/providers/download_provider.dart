import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/download_item.dart';
import '../services/download_feed_service.dart';
import '../services/download_service.dart';
import '../services/download_progress_service.dart';
import '../services/notification_service.dart';

const int _notificationProgressMin = 0;
const int _notificationProgressMax = 100;

double _toFractionalProgress(int progress) {
  return progress / _notificationProgressMax;
}

final downloadServiceProvider = Provider<DownloadFeedService>((ref) {
  return DownloadService.instance;
});

class DownloadsNotifier extends AsyncNotifier<List<DownloadItem>> {
  Future<void>? _reloadInFlight;

  @override
  Future<List<DownloadItem>> build() async {
    // Load initial data
    final items = await ref.read(downloadServiceProvider).getAllDownloads();

    // Listen to updates (like new items or status changes)
    ref.listen<AsyncValue<DownloadItem?>>(
      downloadUpdatesProvider,
      (previous, next) {
        if (next.hasValue && next.value != null) {
          _updateItemInList(next.value!);
          return;
        }
        if (next.hasValue && next.value == null) {
          // Null event indicates deletion; refresh list without resetting provider lifecycle.
          unawaited(_reloadAllDownloads());
        }
      },
    );

    return items;
  }

  Future<void> _reloadAllDownloads() async {
    final inFlight = _reloadInFlight;
    if (inFlight != null) {
      await inFlight;
      return;
    }

    final reloadTask = () async {
      final refreshed =
          await ref.read(downloadServiceProvider).getAllDownloads();
      state = AsyncData(refreshed);
    }();

    _reloadInFlight = reloadTask;
    try {
      await reloadTask;
    } finally {
      if (identical(_reloadInFlight, reloadTask)) {
        _reloadInFlight = null;
      }
    }
  }

  void _updateItemInList(DownloadItem updatedItem) {
    if (!state.hasValue) return;

    final currentItems = state.value!;
    final index = currentItems.indexWhere((item) => item.id == updatedItem.id);

    if (index >= 0) {
      if (_isSemanticallyEqual(currentItems[index], updatedItem)) return;
      final newItems = List<DownloadItem>.from(currentItems);
      newItems[index] = updatedItem;
      state = AsyncData(newItems);
      return;
    }
    // New item, insert at top
    final newItems = [updatedItem, ...currentItems];
    state = AsyncData(newItems);
  }

  bool _isSemanticallyEqual(DownloadItem current, DownloadItem next) {
    return current.id == next.id &&
        current.url == next.url &&
        current.title == next.title &&
        current.thumbnail == next.thumbnail &&
        current.type == next.type &&
        current.format == next.format &&
        current.quality == next.quality &&
        current.outputPath == next.outputPath &&
        current.status == next.status &&
        current.progress == next.progress &&
        current.errorMessage == next.errorMessage &&
        current.createdAt == next.createdAt &&
        current.fileSizeBytes == next.fileSizeBytes &&
        current.exportedPath == next.exportedPath &&
        current.exportStatus == next.exportStatus &&
        current.artist == next.artist &&
        current.album == next.album &&
        current.artistImageUrl == next.artistImageUrl &&
        current.albumImageUrl == next.albumImageUrl;
  }
}

final downloadsProvider =
    AsyncNotifierProvider<DownloadsNotifier, List<DownloadItem>>(() {
  return DownloadsNotifier();
});

final downloadUpdatesProvider = StreamProvider<DownloadItem?>((ref) {
  return DownloadProgressService.instance.updates;
});

final downloadProgressProvider = StreamProvider<Map<int, int>>((ref) {
  return NotificationService.instance.progressStream;
});

final itemProgressProvider = Provider.family<double, DownloadItem>((ref, item) {
  if (item.status == DownloadStatus.completed) return 1.0;
  if (item.status != DownloadStatus.downloading) return item.progress;

  final progressByNotificationId = ref.watch(downloadProgressProvider).value;
  if (progressByNotificationId == null) {
    return item.progress;
  }

  final notificationId = item.id.hashCode;
  final notificationProgress = progressByNotificationId[notificationId];
  if (notificationProgress == null) {
    return item.progress;
  }

  if (notificationProgress >= _notificationProgressMin &&
      notificationProgress <= _notificationProgressMax) {
    return _toFractionalProgress(notificationProgress);
  }

  return item.progress;
});
