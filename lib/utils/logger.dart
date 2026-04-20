import 'package:flutter/foundation.dart';
import 'package:logger/logger.dart';
import 'package:firebase_crashlytics/firebase_crashlytics.dart';

class LocalLogger {
  LocalLogger._();

  static final Logger _logger = Logger(
    printer: PrettyPrinter(
      methodCount: 0,
      errorMethodCount: 5,
      lineLength: 80,
      colors: true,
      printEmojis: true,
      dateTimeFormat: DateTimeFormat.none,
    ),
  );

  static String _sanitize(String message) {
    if (!kReleaseMode) return message;

    // Filtra URLs sensíveis
    String sanitized = message.replaceAll(
        RegExp(r'https?:\/\/[a-zA-Z0-9\-\.]+\.[a-zA-Z]{2,3}(\/\S*)?'),
        '[URL_REMOVIDA]');
    // Filtra paths de sistema (arquivos baixados, diretórios e ffmpeg)
    sanitized = sanitized.replaceAll(
        RegExp(
            r'\/data\/user\/\d+\/[a-zA-Z0-9\-\.]+(\/\S*)?|\/storage\/emulated\/\d+(\/\S*)?'),
        '[PATH_SISTEMA_REMOVIDO]');

    return sanitized;
  }

  static void _logReleaseMessage(String level, String message) {
    if (!kReleaseMode) return;
    final crashlytics = FirebaseCrashlytics.instance;
    crashlytics.log('[$level] ${_sanitize(message)}');
  }

  static void _recordReleaseError(
    String message,
    dynamic error,
    StackTrace? stackTrace, {
    required bool fatal,
  }) {
    if (!kReleaseMode) return;

    final crashlytics = FirebaseCrashlytics.instance;
    crashlytics.recordError(
      error ?? Exception(_sanitize(message)),
      stackTrace,
      reason: _sanitize(message),
      fatal: fatal,
    );
  }

  static void initialize() {
    if (kReleaseMode) {
      // Registrar no Crashlytics que ele foi iniciado
      final crashlytics = FirebaseCrashlytics.instance;
      crashlytics.log('Logger inicializado.');
    }
  }

  static void trace(String message, [dynamic error, StackTrace? stackTrace]) {
    if (!kReleaseMode) _logger.t(message, error: error, stackTrace: stackTrace);
  }

  static void debug(String message, [dynamic error, StackTrace? stackTrace]) {
    if (!kReleaseMode) _logger.d(message, error: error, stackTrace: stackTrace);
    _logReleaseMessage('DEBUG', message);
  }

  static void info(String message, [dynamic error, StackTrace? stackTrace]) {
    if (!kReleaseMode) _logger.i(message, error: error, stackTrace: stackTrace);
    _logReleaseMessage('INFO', message);
  }

  static void warning(String message, [dynamic error, StackTrace? stackTrace]) {
    _logger.w(message, error: error, stackTrace: stackTrace);
    _logReleaseMessage('WARNING', message);
    if (error == null) return;

    _recordReleaseError(
      message,
      error,
      stackTrace,
      fatal: false,
    );
  }

  static void error(String message, [dynamic error, StackTrace? stackTrace]) {
    _logger.e(message, error: error, stackTrace: stackTrace);
    _recordReleaseError(
      message,
      error,
      stackTrace,
      fatal: true,
    );
  }
}
