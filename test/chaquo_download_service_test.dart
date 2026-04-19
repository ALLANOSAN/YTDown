import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ytdown/services/chaquo_download_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final service = ChaquoDownloadService.instance;
  const channel = ChaquoDownloadService.platform;

  Future<void> setChannelHandler(
    Future<dynamic> Function(MethodCall call)? handler,
  ) async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, handler);
  }

  setUp(() {
    service.resetForTests();
  });

  tearDown(() async {
    await setChannelHandler(null);
    service.resetForTests();
  });

  test('initialize marks runtime available and stores native lib dir',
      () async {
    service.resetForTests(allowMethodChannelInTests: true);

    await setChannelHandler((MethodCall call) async {
      switch (call.method) {
        case 'initialize':
          return null;
        case 'getNativeLibDir':
          return '/tmp/ytdown-native';
        default:
          return null;
      }
    });

    await service.initialize();

    expect(service.isInitialized, isTrue);
    expect(service.isAvailable, isTrue);
    expect(service.nativeLibDir, '/tmp/ytdown-native');
  });

  test('checkYtDlpUpdate returns unavailable error when plugin is missing',
      () async {
    final result = await service.checkYtDlpUpdate(forceRemote: false);

    expect(result['success'], isFalse);
    expect(
      result['error'] as String,
      contains('Runtime Python indispon'),
    );
  });

  test('downloadVideo keeps structured error payload from python', () async {
    service.resetForTests(allowMethodChannelInTests: true);

    await setChannelHandler((MethodCall call) async {
      switch (call.method) {
        case 'initialize':
          return null;
        case 'getNativeLibDir':
          return '/tmp/ytdown-native';
        case 'downloadVideo':
          return jsonEncode(<String, dynamic>{
            'success': false,
            'error': 'network timeout',
            'stage': 'network_request',
            'retryable': true,
            'platformCode': 'PYTHON_BRIDGE_ERROR',
          });
        default:
          return null;
      }
    });

    final result = await service.downloadVideo(
      url: 'https://youtube.com/watch?v=abc123',
      outputPath: '/tmp',
      type: 'audio',
      format: 'm4a',
      quality: '192',
    );

    expect(result['success'], isFalse);
    expect(result['error'], 'network timeout');
    expect(result['stage'], 'network_request');
    expect(result['retryable'], isTrue);
    expect(result['platformCode'], 'PYTHON_BRIDGE_ERROR');
  });

  test('downloadVideo returns failure map on platform exception', () async {
    service.resetForTests(allowMethodChannelInTests: true);

    await setChannelHandler((MethodCall call) async {
      switch (call.method) {
        case 'initialize':
          return null;
        case 'getNativeLibDir':
          return '/tmp/ytdown-native';
        case 'downloadVideo':
          throw PlatformException(
            code: 'PYTHON_BRIDGE_ERROR',
            message: 'bridge invocation failed',
          );
        default:
          return null;
      }
    });

    final result = await service.downloadVideo(
      url: 'https://youtube.com/watch?v=abc123',
      outputPath: '/tmp',
      type: 'audio',
      format: 'm4a',
      quality: '192',
    );

    expect(result['success'], isFalse);
    expect(result['error'] as String, contains('PlatformException'));
    expect(result['error'] as String, contains('PYTHON_BRIDGE_ERROR'));
  });

  test('rewriteMetadata returns clear error when method returns null',
      () async {
    service.resetForTests(allowMethodChannelInTests: true);

    await setChannelHandler((MethodCall call) async {
      switch (call.method) {
        case 'initialize':
          return null;
        case 'getNativeLibDir':
          return '/tmp/ytdown-native';
        case 'rewriteMetadata':
          return null;
        default:
          return null;
      }
    });

    final result = await service.rewriteMetadata(
      filePath: '/tmp/audio.m4a',
      title: 'Song',
      artist: 'Artist',
      album: 'Album',
      artworkUrl: null,
    );

    expect(result['success'], isFalse);
    expect(result['error'], 'Resposta nula ao regravar metadados');
  });
}
