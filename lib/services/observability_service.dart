import 'dart:convert';
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'database_service.dart';
import '../utils/common_utils.dart';

class DownloadFailureTelemetry {
  const DownloadFailureTelemetry({
    required this.total,
    required this.byReason,
    required this.byDay,
    this.lastError,
    this.lastTitle,
    this.lastReasonKey,
    this.lastOccurredAt,
  });

  const DownloadFailureTelemetry.empty()
      : total = 0,
        byReason = const {},
        byDay = const {},
        lastError = null,
        lastTitle = null,
        lastReasonKey = null,
        lastOccurredAt = null;

  final int total;
  final Map<String, int> byReason;
  final Map<String, int> byDay;
  final String? lastError;
  final String? lastTitle;
  final String? lastReasonKey;
  final DateTime? lastOccurredAt;
}

class ObservabilityService {
  ObservabilityService._();
  static final instance = ObservabilityService._();

  static const int _defaultFailureRetentionDays = 90;
  static const int _defaultFailureHistoryDays = 30;

  int _downloadFailuresTotal = 0;
  final Map<String, int> _downloadFailuresByReason = <String, int>{};
  final Map<String, int> _downloadFailuresByDay = <String, int>{};
  String? _lastFailureError;
  String? _lastFailureTitle;
  String? _lastFailureReasonKey;
  DateTime? _lastFailureAt;
  Future<void>? _initFuture;

  final _downloadFailureStreamController =
      StreamController<DownloadFailureTelemetry>.broadcast();

  Stream<DownloadFailureTelemetry> get downloadFailureStream =>
      _downloadFailureStreamController.stream;

  DownloadFailureTelemetry get currentDownloadFailureTelemetry =>
      _buildFailureTelemetry();

  void _emitFailureTelemetry() {
    if (_downloadFailureStreamController.isClosed) {
      return;
    }
    _downloadFailureStreamController.add(_buildFailureTelemetry());
  }

  Future<void> init() {
    _initFuture ??= _loadTelemetryFromDatabase();
    return _initFuture!;
  }

  void info(String event, {Map<String, Object?> context = const {}}) {
    _log(level: 'INFO', event: event, context: context);
  }

  void warning(String event, {Map<String, Object?> context = const {}}) {
    _log(level: 'WARN', event: event, context: context);
  }

  void error(String event, {Map<String, Object?> context = const {}}) {
    _log(level: 'ERROR', event: event, context: context);
  }

  void trackDownloadFailure({
    required String downloadId,
    required String title,
    required String source,
    required String errorMessage,
  }) {
    final now = DateTime.now();
    _downloadFailuresTotal += 1;
    final reasonKey = DownloadErrorUtils.normalizeReason(errorMessage);
    _downloadFailuresByReason.update(reasonKey, (value) => value + 1,
        ifAbsent: () => 1);
    final dayKey = AppDateUtils.toDayKey(now);
    _downloadFailuresByDay.update(dayKey, (value) => value + 1,
        ifAbsent: () => 1);
    _lastFailureError = errorMessage;
    _lastFailureTitle = title;
    _lastFailureReasonKey = reasonKey;
    _lastFailureAt = now;

    _emitFailureTelemetry();

    unawaited(_persistDownloadFailure(
      downloadId: downloadId,
      title: title,
      source: source,
      reasonKey: reasonKey,
      errorMessage: errorMessage,
      occurredAt: now,
    ));

    _log(
      level: 'ERROR',
      event: 'download_failed',
      context: {
        'downloadId': downloadId,
        'title': title,
        'source': source,
        'errorMessage': errorMessage,
        'reasonKey': reasonKey,
        'failureCountTotal': _downloadFailuresTotal,
        'failureCountReason': _downloadFailuresByReason[reasonKey],
      },
    );
  }

  Map<String, Object?> getDownloadFailureSnapshot() {
    return {
      'downloadFailuresTotal': _downloadFailuresTotal,
      'downloadFailuresByReason':
          Map<String, int>.from(_downloadFailuresByReason),
      'downloadFailuresByDay': Map<String, int>.from(_downloadFailuresByDay),
      'lastError': _lastFailureError,
      'lastTitle': _lastFailureTitle,
      'lastReasonKey': _lastFailureReasonKey,
      'lastOccurredAt': _lastFailureAt?.toIso8601String(),
    };
  }

  DownloadFailureTelemetry _buildFailureTelemetry() {
    return DownloadFailureTelemetry(
      total: _downloadFailuresTotal,
      byReason: Map<String, int>.from(_downloadFailuresByReason),
      byDay: Map<String, int>.from(_downloadFailuresByDay),
      lastError: _lastFailureError,
      lastTitle: _lastFailureTitle,
      lastReasonKey: _lastFailureReasonKey,
      lastOccurredAt: _lastFailureAt,
    );
  }

  Future<void> _loadTelemetryFromDatabase() async {
    try {
      final prunedCount = await DatabaseService.instance
          .pruneDownloadFailureEvents(keepDays: _defaultFailureRetentionDays);
      final total = await DatabaseService.instance.getDownloadFailureTotal();
      final byReason =
          await DatabaseService.instance.getDownloadFailureCountByReason();
      final byDay = await DatabaseService.instance
          .getDownloadFailureHistoryByDay(
              limitDays: _defaultFailureHistoryDays);
      final last = await DatabaseService.instance.getLastDownloadFailureEvent();

      _downloadFailuresTotal = total;
      _downloadFailuresByReason
        ..clear()
        ..addAll(byReason);
      _downloadFailuresByDay
        ..clear()
        ..addAll(byDay);

      _lastFailureError = last?['errorMessage'] as String?;
      _lastFailureTitle = last?['title'] as String?;
      _lastFailureReasonKey = last?['reasonKey'] as String?;

      final occurredAtMs = last?['occurredAt'] as int?;
      _lastFailureAt = occurredAtMs != null
          ? DateTime.fromMillisecondsSinceEpoch(occurredAtMs)
          : null;

      _emitFailureTelemetry();
      info(
        'download_failure_telemetry_loaded',
        context: {
          'prunedCount': prunedCount,
          'total': _downloadFailuresTotal,
          'historyDays': _downloadFailuresByDay.length,
        },
      );
    } catch (e) {
      warning(
        'download_failure_telemetry_load_failed',
        context: {'error': e.toString()},
      );
    }
  }

  Future<void> _persistDownloadFailure({
    required String downloadId,
    required String title,
    required String source,
    required String reasonKey,
    required String errorMessage,
    required DateTime occurredAt,
  }) async {
    try {
      await DatabaseService.instance.insertDownloadFailureEvent(
        downloadId: downloadId,
        title: title,
        source: source,
        reasonKey: reasonKey,
        errorMessage: errorMessage,
        occurredAt: occurredAt,
      );
    } catch (e) {
      warning(
        'download_failure_persist_failed',
        context: {
          'downloadId': downloadId,
          'error': e.toString(),
        },
      );
    }
  }

  void _log({
    required String level,
    required String event,
    required Map<String, Object?> context,
  }) {
    final payload = {
      'timestamp': DateTime.now().toIso8601String(),
      'level': level,
      'event': event,
      'context': context,
    };
    debugPrint(jsonEncode(payload));
  }

  /// Libera recursos do serviço
  void dispose() {
    if (_downloadFailureStreamController.isClosed) {
      return;
    }
    _downloadFailureStreamController.close();
  }
}
