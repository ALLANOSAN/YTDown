import 'dart:async';
import 'dart:convert';
import 'dart:io' show Platform;
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import '../utils/logger.dart';
import 'observability_service.dart';

/// Serviço que usa Chaquo Python SDK para executar yt-dlp
/// FFmpeg é localizado automaticamente via jniLibs (Android 10+ SELinux safe)
class ChaquoDownloadService {
  ChaquoDownloadService._();
  static final instance = ChaquoDownloadService._();

  static const platform = MethodChannel('com.example.ytdown/chaquo');
  static const String _runtimeUnavailableGenericError =
      'Runtime Python indisponível. Verifique se o Chaquo está configurado corretamente.';
  static const String _runtimeUnavailableDownloadError =
      'Runtime Python indisponível. O download não pode ser realizado sem o Chaquo.';
  static const String _runtimeUnavailableRewriteError =
      'Runtime Python indisponível. O reparo de metadados requer Chaquo.';

  static bool _allowMethodChannelInFlutterTest = false;
  bool _initialized = false;
  bool _available = false;
  String? _nativeLibDir;
  bool _ytDlpSyncStarted = false;

  /// Indicates whether the Chaquo/Python runtime is available.
  bool get isAvailable => _available;

  /// Inicializa o Python Chaquo
  Future<void> initialize() async {
    if (_initialized || _available) return;

    // Sem runtime Android real (ex.: flutter test em Linux/macOS/Windows),
    // não há plugin Chaquo registrado para este MethodChannel.
    // Permitimos opt-in explícito apenas para testes com MockMethodCallHandler.
    final isAndroidRuntime = _isAndroidRuntime;
    if (!isAndroidRuntime && !_allowMethodChannelInFlutterTest) {
      _available = false;
      _initialized = true;
      return;
    }

    try {
      LocalLogger.debug(
          '🔵 [ChaquoDownloadService] Inicializando Python Chaquo...');
      await platform.invokeMethod('initialize');

      // Obter diretório de bibliotecas nativas (onde o ffmpeg está)
      _nativeLibDir = await platform.invokeMethod<String>('getNativeLibDir');
      LocalLogger.debug(
          '🔵 [ChaquoDownloadService] NativeLibDir: $_nativeLibDir');

      _initialized = true;
      _available = true;
      LocalLogger.debug(
          '✅ [ChaquoDownloadService] Python Chaquo inicializado!');

      if (!_ytDlpSyncStarted) {
        _ytDlpSyncStarted = true;
        unawaited(_checkAndUpdateYtDlpInBackground());
      }
    } on MissingPluginException catch (e) {
      LocalLogger.debug(
          '⚠️ [ChaquoDownloadService] Plugin Chaquo não disponível. O recurso de download via Python está indisponível. Detalhes: $e');
      _markUnavailable();
    } catch (e) {
      LocalLogger.debug('❌ [ChaquoDownloadService] Erro ao inicializar: $e');
      _markUnavailable();
    }
  }

  bool get _isAndroidRuntime => !kIsWeb && Platform.isAndroid;

  void _markUnavailable() {
    _available = false;
    _initialized = false;
  }

  Future<Map<String, dynamic>> _invokeJsonMethod({
    required String method,
    required String nullResponseError,
    Map<String, dynamic> arguments = const <String, dynamic>{},
    Duration? timeout,
    TimeoutException Function()? onTimeout,
  }) async {
    try {
      var invocation = platform.invokeMethod<dynamic>(method, arguments);
      if (timeout != null) {
        invocation = invocation.timeout(timeout, onTimeout: onTimeout);
      }

      final raw = await invocation;
      return _decodeResponse(raw, nullResponseError);
    } catch (e) {
      return {
        'success': false,
        'error': e.toString(),
      };
    }
  }

  Map<String, dynamic> _decodeResponse(dynamic raw, String nullResponseError) {
    if (raw == null) {
      return {
        'success': false,
        'error': nullResponseError,
      };
    }

    if (raw is Map) {
      return Map<String, dynamic>.from(raw);
    }

    if (raw is String) {
      try {
        final dynamic decoded = jsonDecode(raw);
        if (decoded is Map) {
          return Map<String, dynamic>.from(decoded);
        }
      } catch (_) {
        // fall through to invalid payload
      }
    }

    return {
      'success': false,
      'error': 'Resposta inválida do método nativo',
    };
  }

  Map<String, dynamic> _runtimeUnavailableResult(String message) {
    return {
      'success': false,
      'error': message,
    };
  }

  Future<Map<String, dynamic>> checkYtDlpUpdate({
    bool forceRemote = true,
  }) async {
    await initialize();
    if (!_available) {
      return _runtimeUnavailableResult(_runtimeUnavailableGenericError);
    }

    return _invokeJsonMethod(
      method: 'checkYtDlpUpdate',
      arguments: {'forceRemote': forceRemote},
      nullResponseError: 'Resposta nula ao checar atualização do yt-dlp',
    );
  }

  Future<Map<String, dynamic>> updateYtDlpIfNeeded({bool force = false}) async {
    await initialize();
    if (!_available) {
      return _runtimeUnavailableResult(_runtimeUnavailableGenericError);
    }

    return _invokeJsonMethod(
      method: 'updateYtDlpIfNeeded',
      arguments: {'force': force},
      nullResponseError: 'Resposta nula ao atualizar yt-dlp',
    );
  }

  Future<void> _checkAndUpdateYtDlpInBackground() async {
    try {
      final check = await checkYtDlpUpdate(forceRemote: false);
      if (check['success'] != true) {
        ObservabilityService.instance.warning(
          'yt_dlp_update_check_failed',
          context: {'error': check['error'] as Object?},
        );
        return;
      }

      final currentVersion = check['current_version']?.toString();
      final latestVersion = check['latest_version']?.toString();
      final updateAvailable = check['update_available'] == true;

      ObservabilityService.instance.info(
        'yt_dlp_update_check',
        context: {
          'currentVersion': currentVersion,
          'latestVersion': latestVersion,
          'updateAvailable': updateAvailable,
        },
      );

      if (!updateAvailable) return;

      final updateResult = await updateYtDlpIfNeeded();
      final success = updateResult['success'] == true;
      final updated = updateResult['updated'] == true;

      if (!success) {
        ObservabilityService.instance.warning(
          'yt_dlp_update_runtime_failed',
          context: {'error': updateResult['error'] as Object?},
        );
        return;
      }

      if (!updated) {
        return;
      }

      ObservabilityService.instance.info(
        'yt_dlp_updated_runtime',
        context: {
          'currentVersion': updateResult['current_version'] as Object?,
          'latestVersion': updateResult['latest_version'] as Object?,
        },
      );
    } catch (e) {
      ObservabilityService.instance.warning(
        'yt_dlp_update_background_exception',
        context: {'error': e.toString()},
      );
    }
  }

  /// Verifica status do FFmpeg
  Future<String?> checkFfmpeg() async {
    try {
      return await platform.invokeMethod<String>('checkFfmpeg');
    } catch (e) {
      LocalLogger.debug(
          '⚠️ [ChaquoDownloadService] Erro ao verificar ffmpeg: $e');
      return null;
    }
  }

  /// Obtém informações do vídeo
  Future<Map<String, dynamic>> fetchVideoInfo(String url) async {
    await initialize();
    if (!_available) {
      throw Exception(_runtimeUnavailableDownloadError);
    }

    try {
      LocalLogger.debug('🔵 [ChaquoDownloadService] Buscando info: $url');

      final result =
          await platform.invokeMethod('fetchVideoInfo', {'url': url}).timeout(
        const Duration(seconds: 30),
        onTimeout: () => throw TimeoutException(
            'Operação demorou demais para responder (timeout)'),
      );

      final response = _parseResponseAsMap(result);
      if (response['success'] == true) {
        LocalLogger.debug(
            '✅ [ChaquoDownloadService] Sucesso: ${response['data']['title']}');
        return response['data'] as Map<String, dynamic>;
      }

      throw Exception(response['error'] ?? 'Erro desconhecido');
    } on TimeoutException catch (e) {
      ObservabilityService.instance
          .warning('fetch_timeout', context: {'url': url});
      LocalLogger.debug('❌ [ChaquoDownloadService] Timeout: $e');
      throw Exception(
          'Serviço de extração não respondeu a tempo. Verifique sua conexão.');
    } catch (e) {
      LocalLogger.debug('❌ [ChaquoDownloadService] Erro: $e');
      rethrow;
    }
  }

  Map<String, dynamic> _parseResponseAsMap(dynamic result) {
    return _decodeResponse(
        result, 'Resposta inválida ao buscar informações do vídeo');
  }

  /// Inicia download (passa nativeLibDir para Python encontrar o ffmpeg)
  Future<Map<String, dynamic>> downloadVideo({
    required String url,
    required String outputPath,
    required String type,
    required String format,
    required String quality,
    String? artist,
    String? album,
    String? artworkUrl,
  }) async {
    await initialize();
    if (!_available) {
      LocalLogger.debug(
          '⚠️ [ChaquoDownloadService] Download bloqueado: runtime Python indisponível.');
      return _runtimeUnavailableResult(_runtimeUnavailableDownloadError);
    }

    LocalLogger.debug('🔵 [ChaquoDownloadService] Download: $url');
    LocalLogger.debug('🔵 [ChaquoDownloadService] FFmpeg dir: $_nativeLibDir');

    final response = await _invokeJsonMethod(
      method: 'downloadVideo',
      arguments: {
        'url': url,
        'outputPath': outputPath,
        'type': type,
        'format': format,
        'quality': quality,
        'artist': _trimOrNull(artist),
        'album': _trimOrNull(album),
        'artworkUrl': _trimOrNull(artworkUrl),
      },
      nullResponseError: 'Resultado nulo retornado pelo runtime Python',
      timeout: const Duration(minutes: 60),
      onTimeout: () => TimeoutException(
        'Timeout estourado: O download durou muito tempo (mais de 60 min)',
      ),
    );

    if (response['success'] != true) {
      LocalLogger.debug(
          '❌ [ChaquoDownloadService] Erro no download: ${response['error']}');
      return response;
    }

    final mode = response['mode'] ?? 'unknown';
    final ffmpegUsed = response['ffmpeg_used'] ?? false;
    LocalLogger.debug(
        '✅ [ChaquoDownloadService] Modo: $mode, FFmpeg: $ffmpegUsed');

    return response;
  }

  Future<Map<String, dynamic>> rewriteMetadata({
    required String filePath,
    required String title,
    String? artist,
    String? album,
    String? artworkUrl,
  }) async {
    await initialize();
    if (!_available) {
      return _runtimeUnavailableResult(_runtimeUnavailableRewriteError);
    }

    return _invokeJsonMethod(
      method: 'rewriteMetadata',
      arguments: {
        'filePath': filePath,
        'title': title,
        'artist': _trimOrNull(artist),
        'album': _trimOrNull(album),
        'artworkUrl': _trimOrNull(artworkUrl),
      },
      nullResponseError: 'Resposta nula ao regravar metadados',
    );
  }

  /// Re-scaneia múltiplos arquivos no MediaStore em lote
  /// Isso é crucial para edição em lote - garante que TODAS as faixas sejam atualizadas
  Future<Map<String, dynamic>> batchRescanFiles(List<String> paths) async {
    await initialize();
    if (!_available) {
      return _runtimeUnavailableResult('Runtime Python indisponível');
    }

    return _invokeJsonMethod(
      method: 'batchRescanFiles',
      arguments: {'paths': paths},
      nullResponseError: 'Resposta nula ao reescanear arquivos',
    );
  }

  bool get isInitialized => _initialized;

  String? _trimOrNull(String? value) {
    final trimmed = value?.trim();
    if (trimmed == null || trimmed.isEmpty) {
      return null;
    }
    return trimmed;
  }

  String? get nativeLibDir => _nativeLibDir;

  @visibleForTesting
  void resetForTests({
    bool disableBackgroundSync = true,
    bool allowMethodChannelInTests = false,
  }) {
    _initialized = false;
    _available = false;
    _nativeLibDir = null;
    _ytDlpSyncStarted = disableBackgroundSync;
    _allowMethodChannelInFlutterTest = allowMethodChannelInTests;
  }
}
