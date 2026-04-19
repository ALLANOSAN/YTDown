import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';

/// Serviço de caminhos e diretórios do app.
///
/// O Python é gerenciado pelo Chaquopy (build.gradle).
/// O FFmpeg é carregado via jniLibs (nativeLibraryDir).
/// Esse serviço cuida de caminhos de download e certificados SSL.
class BinaryService {
  BinaryService._();
  static final instance = BinaryService._();

  static const String _ffmpegLibraryName = 'libffmpeg_exe.so';
  static const String _androidFallbackDownloadsDir =
      '/storage/emulated/0/Download/YTDown';
  static const String _certificateAssetPath = 'assets/python/cacert.pem';

  late String _filesDir;
  late String _downloadsDir;
  late String _nativeLibraryPath;
  bool _ready = false;

  String get ffmpegPath => '$_nativeLibraryPath/$_ffmpegLibraryName';
  String get caCertPath => '$_filesDir/cacert.pem';
  String get filesDir => _filesDir;
  String get downloadsDir => _downloadsDir;
  String get nativeLibDir => _nativeLibraryPath;

  Future<void> initialize() async {
    if (_ready) {
      return;
    }

    await _resolveBasePaths();
    await _ensureCertificateAvailable();
    await _logFfmpegAvailability();

    _ready = true;
    debugPrint('✅ BinaryService inicializado.');
  }

  Future<void> _resolveBasePaths() async {
    final supportDir = await getApplicationSupportDirectory();
    _filesDir = supportDir.path;

    // Obtém o caminho real das bibliotecas nativas via MethodChannel
    _nativeLibraryPath = await NativeLibLoader.getNativeLibDir();
    debugPrint('📍 Native Lib Dir: $_nativeLibraryPath');

    _downloadsDir = await _resolveDownloadsPath();
  }

  Future<String> _resolveDownloadsPath() async {
    if (Platform.isAndroid) {
      return _resolveAndroidDownloadsPath();
    }

    final downloadDir = await getDownloadsDirectory() ??
        await getApplicationDocumentsDirectory();
    return '${downloadDir.path}/YTDown';
  }

  Future<String> _resolveAndroidDownloadsPath() async {
    final externalDir = await getExternalStorageDirectory();
    if (externalDir == null) {
      return _androidFallbackDownloadsDir;
    }

    final root = externalDir.path.split('/Android/data').first;
    return '$root/Download/YTDown';
  }

  Future<void> _ensureCertificateAvailable() async {
    final certFile = File(caCertPath);
    if (await certFile.exists()) {
      return;
    }

    await _extractAsset(_certificateAssetPath, caCertPath);
  }

  Future<void> _logFfmpegAvailability() async {
    final ffmpegFile = File(ffmpegPath);
    if (await ffmpegFile.exists()) {
      final size = await ffmpegFile.length();
      final canExec = await _checkExecutable(ffmpegPath);
      debugPrint(
          '✅ FFmpeg via jniLibs: $ffmpegPath ($size bytes, exec=$canExec)');
      return;
    }

    debugPrint('⚠️ FFmpeg NÃO encontrado em jniLibs: $ffmpegPath');
    debugPrint(
        '   → Downloads de vídeo usarão modo fallback (pré-muxado, máx 720p)');
    debugPrint(
        '   → Para qualidade 1080p+, coloque o binário ffmpeg arm64 em:');
    debugPrint('     android/app/src/main/jniLibs/arm64-v8a/libffmpeg_exe.so');
  }

  Future<bool> _checkExecutable(String path) async {
    try {
      final result = await Process.run('ls', ['-la', path]);
      return result.stdout.toString().contains('x');
    } catch (_) {
      return false;
    }
  }

  Future<void> _extractAsset(String assetPath, String destPath) async {
    final bytes = await rootBundle.load(assetPath);
    final file = File(destPath);
    await file.create(recursive: true);
    await file.writeAsBytes(bytes.buffer.asUint8List());
  }

  bool get isReady => _ready;
  bool get hasFfmpeg => File(ffmpegPath).existsSync();
}

/// Classe para obter o diretório de bibliotecas nativas via MethodChannel
class NativeLibLoader {
  static const platform = MethodChannel('com.example.ytdown/native_lib');

  static Future<String> getNativeLibDir() async {
    try {
      return await platform.invokeMethod('getNativeLibDir');
    } catch (e) {
      debugPrint('⚠️ Erro ao obter nativeLibDir: $e');
      return '';
    }
  }
}
