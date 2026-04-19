import 'dart:async';
import 'dart:collection';

class TaskQueue {
  final int maxConcurrent;
  int _activeCount = 0;
  final Queue<_QueuedTask<dynamic>> _pendingTasks =
      Queue<_QueuedTask<dynamic>>();

  int get activeCount => _activeCount;
  int get queueLength => _pendingTasks.length;
  int get totalActive => _activeCount + _pendingTasks.length;

  TaskQueue({this.maxConcurrent = 3}) {
    if (maxConcurrent <= 0) {
      throw ArgumentError.value(
        maxConcurrent,
        'maxConcurrent',
        'maxConcurrent must be greater than zero',
      );
    }
  }

  Future<T> add<T>(Future<T> Function() task) {
    final completer = Completer<T>();
    _pendingTasks.add(_QueuedTask<T>(task, completer));
    _processQueue();
    return completer.future;
  }

  void _processQueue() {
    while (_activeCount < maxConcurrent && _pendingTasks.isNotEmpty) {
      final nextTask = _pendingTasks.removeFirst();
      unawaited(_runTask(nextTask));
    }
  }

  Future<void> _runTask(_QueuedTask<dynamic> queuedTask) async {
    _activeCount++;

    try {
      final result = await queuedTask.task();
      queuedTask.completer.complete(result);
    } catch (e, st) {
      queuedTask.completer.completeError(e, st);
    } finally {
      _activeCount--;
      _processQueue();
    }
  }
}

class _QueuedTask<T> {
  final Future<dynamic> Function() task;
  final Completer<dynamic> completer;

  _QueuedTask(this.task, this.completer);
}
