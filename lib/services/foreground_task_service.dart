import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';

@pragma('vm:entry-point')
void startCallback() {
  FlutterForegroundTask.setTaskHandler(YTDownTaskHandler());
}

class YTDownTaskHandler extends TaskHandler {
  @override
  Future<void> onStart(DateTime timestamp, TaskStarter starter) async {
    debugPrint('Foreground Task Started: ${starter.name}');
  }

  @override
  void onRepeatEvent(DateTime timestamp) {
    // Mantém a task viva periodicaamente.
  }

  @override
  Future<void> onDestroy(DateTime timestamp, bool isTimeout) async {
    debugPrint('Foreground Task Destroyed. Timeout: $isTimeout');
  }

  @override
  void onReceiveData(Object data) {}

  @override
  void onNotificationButtonPressed(String id) {
    debugPrint('Foreground notification button pressed: $id');
  }

  @override
  void onNotificationPressed() {}

  @override
  void onNotificationDismissed() {}
}

class AppForegroundService {
  AppForegroundService._();
  static final instance = AppForegroundService._();

  bool _isInitialized = false;
  int _activeDownloads = 0;

  bool get _supportsForegroundService {
    if (kIsWeb) {
      return false;
    }
    return Platform.isAndroid || Platform.isIOS;
  }

  Future<void> init() async {
    if (!_supportsForegroundService) {
      return;
    }
    if (_isInitialized) {
      return;
    }

    FlutterForegroundTask.init(
      androidNotificationOptions: _buildAndroidNotificationOptions(),
      iosNotificationOptions: const IOSNotificationOptions(
        showNotification: true,
        playSound: false,
      ),
      foregroundTaskOptions: _buildForegroundTaskOptions(),
    );
    _isInitialized = true;
  }

  AndroidNotificationOptions _buildAndroidNotificationOptions() {
    return AndroidNotificationOptions(
      channelId: 'ytdown_foreground',
      channelName: 'Downloads Background',
      channelDescription: 'Mantém downloads rodando com tela apagada.',
      channelImportance: NotificationChannelImportance.LOW,
      priority: NotificationPriority.LOW,
    );
  }

  ForegroundTaskOptions _buildForegroundTaskOptions() {
    return ForegroundTaskOptions(
      eventAction: ForegroundTaskEventAction.repeat(5000),
      autoRunOnBoot: false,
      allowWakeLock: true,
      allowWifiLock: true,
    );
  }

  Future<void> requestPermissions() async {
    final NotificationPermission notificationPermissionStatus =
        await FlutterForegroundTask.checkNotificationPermission();
    if (notificationPermissionStatus != NotificationPermission.granted) {
      await FlutterForegroundTask.requestNotificationPermission();
    }
  }

  Future<void> updateCount(int count) async {
    if (!_supportsForegroundService) {
      return; // Apenas roda em mobile real
    }

    _activeDownloads = count;
    if (_activeDownloads <= 0) {
      if (await FlutterForegroundTask.isRunningService) {
        await FlutterForegroundTask.stopService();
      }
      return;
    }

    if (await FlutterForegroundTask.isRunningService) {
      FlutterForegroundTask.updateService(
        notificationTitle: 'Baixando e Convertendo...',
        notificationText:
            '$_activeDownloads arquivo(s) sendo processado(s) em background.',
      );
      return;
    }

    await init();
    await requestPermissions();
    await FlutterForegroundTask.startService(
      serviceId: 500,
      notificationTitle: 'Iniciando Downloads...',
      notificationText: 'Mantendo conexão viva.',
      callback: startCallback,
    );
  }

  Future<void> stop() async {
    if (!_supportsForegroundService) {
      return;
    }
    if (await FlutterForegroundTask.isRunningService) {
      await FlutterForegroundTask.stopService();
    }
  }
}
