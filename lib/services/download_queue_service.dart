import 'dart:async';
import '../utils/task_queue.dart';

class DownloadQueueService {
  DownloadQueueService._();
  static final instance = DownloadQueueService._();

  final TaskQueue queue = TaskQueue(maxConcurrent: 3);
  final Map<String, Completer<void>> _locks = {};

  int get totalActiveTasks => queue.totalActive;

  Future<T> add<T>(Future<T> Function() task) {
    return queue.add(task);
  }

  Future<T> withLock<T>(String id, Future<T> Function() action) async {
    while (true) {
      final existingLock = _locks[id];
      if (existingLock == null) {
        break;
      }
      await existingLock.future;
    }

    final lock = _acquireLock(id);
    try {
      return await action();
    } finally {
      _releaseLock(id, lock);
    }
  }

  Completer<void> _acquireLock(String id) {
    final lock = Completer<void>();
    _locks[id] = lock;
    return lock;
  }

  void _releaseLock(String id, Completer<void> lock) {
    if (_locks[id] == lock) {
      _locks.remove(id);
    }
    if (!lock.isCompleted) {
      lock.complete();
    }
  }

  void dispose() {
    for (final lock in _locks.values) {
      if (!lock.isCompleted) {
        lock.complete();
      }
    }
    _locks.clear();
  }
}
