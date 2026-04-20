import 'dart:async';

import 'package:flutter/material.dart';
import 'package:just_audio_background/just_audio_background.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_crashlytics/firebase_crashlytics.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';
import 'screens/home_screen.dart';
import 'theme/app_theme.dart';
import 'services/notification_service.dart';
import 'services/observability_service.dart';
import 'services/download_service.dart';
import 'services/player_service.dart';
import 'utils/logger.dart';

Future<void> _initializeBackgroundAudio() async {
  await JustAudioBackground.init(
    androidNotificationChannelId: 'com.example.ytdown.channel.audio',
    androidNotificationChannelName: 'Audio playback',
    androidNotificationOngoing: true,
  );
}

void main() async {
  runZonedGuarded(_bootstrapApplication, (error, stack) {
    LocalLogger.error('SENTINELA DART: ERRO FATAL NO BOOT', error, stack);
  });
}

Future<void> _bootstrapApplication() async {
  WidgetsFlutterBinding.ensureInitialized();
  FlutterForegroundTask.initCommunicationPort();

  await _initializeFirebaseAndLogger();
  await _initializeCoreServices();
  _runApplication();
}

Future<void> _initializeFirebaseAndLogger() async {
  try {
    await Firebase.initializeApp();
    final crashlytics = FirebaseCrashlytics.instance;
    FlutterError.onError = crashlytics.recordFlutterFatalError;

    LocalLogger.initialize();
    LocalLogger.info('✅ Firebase inicializado com sucesso.');
  } catch (error) {
    debugPrint('⚠️ Erro Firebase Init (Falta arquivo de config?): $error');
  }
}

Future<void> _initializeCoreServices() async {
  final notificationService = NotificationService.instance;
  final observabilityService = ObservabilityService.instance;

  await notificationService.init();
  await observabilityService.init();

  try {
    await _initializeBackgroundAudio();
    LocalLogger.info('✅ JustAudioBackground inicializado com sucesso!');
  } catch (error, stackTrace) {
    LocalLogger.error(
      '❌ Erro ao inicializar JustAudioBackground',
      error,
      stackTrace,
    );
  }
}

void _runApplication() {
  runApp(
    const ProviderScope(
      child: _AppLifecycleManager(child: YTDownApp()),
    ),
  );
}

// Widget para gerenciar dispose de serviços no app lifecycle
class _AppLifecycleManager extends StatefulWidget {
  final Widget child;
  const _AppLifecycleManager({required this.child});

  @override
  State<_AppLifecycleManager> createState() => _AppLifecycleManagerState();
}

class _AppLifecycleManagerState extends State<_AppLifecycleManager>
    with WidgetsBindingObserver {
  bool _disposed = false;

  @override
  void initState() {
    super.initState();
    final widgetsBinding = WidgetsBinding.instance;
    widgetsBinding.addObserver(this);
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.detached) {
      _disposeServices();
    }
  }

  @override
  void dispose() {
    _disposeServices();
    final widgetsBinding = WidgetsBinding.instance;
    widgetsBinding.removeObserver(this);
    super.dispose();
  }

  void _disposeServices() {
    if (_disposed) return;
    _disposed = true;

    debugPrint('🧹 Liberando recursos do app...');

    final downloadService = DownloadService.instance;
    final notificationService = NotificationService.instance;
    final observabilityService = ObservabilityService.instance;
    final playerService = PlayerService.instance;

    downloadService.dispose();
    notificationService.dispose();
    observabilityService.dispose();
    unawaited(playerService.dispose());
  }

  @override
  Widget build(BuildContext context) => widget.child;
}

class YTDownApp extends StatelessWidget {
  final GlobalKey<NavigatorState>? navigatorKey;
  const YTDownApp({super.key, this.navigatorKey});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'YTDown',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark,
      navigatorKey: navigatorKey,
      home: const HomeScreen(),
    );
  }
}
