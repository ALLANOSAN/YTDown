import 'dart:async';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'observability_service.dart';

class NotificationService {
  NotificationService._();
  static final instance = NotificationService._();

  static const String _channelId = 'ytdown_downloads';
  static const String _channelName = 'Downloads';
  static const String _channelDescription =
      'Notificações de download do YTDown';
  static const int _maxTitleLength = 50;

  static final RegExp _sensitivePathPattern = RegExp(
    r'\/data\/user\/\d+\/[a-zA-Z0-9\-\.]+(\/\S*)?|\/storage\/emulated\/\d+(\/\S*)?',
  );

  static const AndroidNotificationChannel _downloadChannel =
      AndroidNotificationChannel(
    _channelId,
    _channelName,
    description: _channelDescription,
    importance: Importance.low,
    showBadge: true,
  );

  final _plugin = FlutterLocalNotificationsPlugin();
  final _progressStream = StreamController<Map<int, int>>.broadcast();

  Stream<Map<int, int>> get progressStream => _progressStream.stream;

  /// Sanitiza o título subtraindo links ou delimitando o tamanho p/ não vazar paths acidentalmente
  String _sanitizeTitle(String rawTitle) {
    if (rawTitle.isEmpty) {
      return rawTitle;
    }

    final clean = rawTitle.replaceAll(_sensitivePathPattern, '.../arquivo');
    if (clean.length <= _maxTitleLength) {
      return clean;
    }

    return '${clean.substring(0, _maxTitleLength - 3)}...';
  }

  NotificationDetails _buildDownloadNotificationDetails({
    required Importance importance,
    required Priority priority,
    bool ongoing = false,
    bool showProgress = false,
    int? progress,
    bool indeterminate = false,
  }) {
    return NotificationDetails(
      android: AndroidNotificationDetails(
        _channelId,
        _channelName,
        channelDescription: _channelDescription,
        importance: importance,
        priority: priority,
        ongoing: ongoing,
        showProgress: showProgress,
        maxProgress: 100,
        progress: progress ?? 0,
        indeterminate: indeterminate,
        icon: '@mipmap/ic_launcher',
      ),
    );
  }

  void _emitProgress(int notificationId, int progress) {
    if (_progressStream.isClosed) {
      return;
    }
    _progressStream.add({notificationId: progress});
  }

  /// Libera recursos do serviço
  void dispose() {
    if (_progressStream.isClosed) {
      return;
    }
    _progressStream.close();
  }

  Future<void> init() async {
    const android = AndroidInitializationSettings('@mipmap/ic_launcher');
    const settings = InitializationSettings(android: android);
    await _plugin.initialize(settings: settings);

    final androidPlugin = _plugin.resolvePlatformSpecificImplementation<
        AndroidFlutterLocalNotificationsPlugin>();
    final androidPermissionGranted =
        await androidPlugin?.requestNotificationsPermission();
    ObservabilityService.instance.info(
      'notification_permission_checked',
      context: {
        'androidPermissionGranted': androidPermissionGranted,
      },
    );

    await _plugin
        .resolvePlatformSpecificImplementation<
            AndroidFlutterLocalNotificationsPlugin>()
        ?.createNotificationChannel(_downloadChannel);

    ObservabilityService.instance.info(
      'notification_channel_ready',
      context: {
        'channelId': _channelId,
      },
    );
  }

  void showDownloadStarted(String id, String title) {
    final notificationId = id.hashCode;
    final safeTitle = _sanitizeTitle(title);
    _plugin.show(
      id: notificationId,
      title: '⬇️ Download iniciado',
      body: safeTitle,
      notificationDetails: _buildDownloadNotificationDetails(
        importance: Importance.low,
        priority: Priority.low,
        ongoing: true,
        showProgress: true,
        progress: 0,
        indeterminate: true,
      ),
    );
  }

  void showDownloadProgress(String id, String title, int progress) {
    final notificationId = id.hashCode;
    final safeTitle = _sanitizeTitle(title);
    _plugin.show(
      id: notificationId,
      title: '⬇️ $progress%',
      body: safeTitle,
      notificationDetails: _buildDownloadNotificationDetails(
        importance: Importance.low,
        priority: Priority.low,
        ongoing: true,
        showProgress: true,
        progress: progress,
      ),
    );

    _emitProgress(notificationId, progress);
  }

  void showDownloadCompleted(String id, String title) {
    final notificationId = id.hashCode;
    final safeTitle = _sanitizeTitle(title);
    _plugin.show(
      id: notificationId,
      title: '✅ Download concluído!',
      body: safeTitle,
      notificationDetails: _buildDownloadNotificationDetails(
        importance: Importance.high,
        priority: Priority.high,
      ),
    );

    _emitProgress(notificationId, 100);
  }

  void showDownloadFailed(String id, String title, String error) {
    final notificationId = id.hashCode;
    final safeTitle = _sanitizeTitle(title);
    _plugin.show(
      id: notificationId,
      title: '❌ Falha no download',
      body: '$safeTitle\n$error',
      notificationDetails: _buildDownloadNotificationDetails(
        importance: Importance.high,
        priority: Priority.high,
      ),
    );
  }

  Future<void> cancelNotification(String id) async {
    final notificationId = id.hashCode;
    await _plugin.cancel(id: notificationId);
    _emitProgress(notificationId, -1);
  }
}
