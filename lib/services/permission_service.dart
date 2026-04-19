import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';

class PermissionService {
  PermissionService._();
  static final instance = PermissionService._();

  static const int _scopedStorageApiLevel = 29;
  static const int _fallbackSdkInt = 21;
  static const MethodChannel _platformChannel =
      MethodChannel('com.example.ytdown/platform_info');

  /// Solicita permissões de armazenamento adequadas à versão do Android
  Future<bool> requestStoragePermission(BuildContext context) async {
    if (!Platform.isAndroid) {
      return true;
    }

    // Android 10+ usa scoped storage e não precisa de permissão global.
    final sdkInt = await _getSdkInt();
    if (sdkInt >= _scopedStorageApiLevel) {
      return true;
    }

    // Android 9 e abaixo — precisa de WRITE_EXTERNAL_STORAGE
    final status = await Permission.storage.request();
    if (status.isPermanentlyDenied && context.mounted) {
      _showSettingsDialog(context);
      return false;
    }

    return status.isGranted;
  }

  /// Solicita permissão de notificações (Android 13+)
  Future<bool> requestNotificationPermission() async {
    final status = await Permission.notification.request();
    return status.isGranted;
  }

  /// Solicita todas as permissões necessárias de uma vez no startup
  Future<void> requestAllPermissions(BuildContext context) async {
    await requestStoragePermission(context);
    await requestNotificationPermission();
  }

  /// Verifica se as permissões de armazenamento estão concedidas
  Future<bool> hasStoragePermission() async {
    if (!Platform.isAndroid) {
      return true;
    }

    final sdkInt = await _getSdkInt();
    if (sdkInt >= _scopedStorageApiLevel) {
      return true;
    }

    return Permission.storage.isGranted;
  }

  Future<int> _getSdkInt() async {
    try {
      final sdkInt = await _platformChannel.invokeMethod<int>('getSdkInt');
      return sdkInt ?? _fallbackSdkInt;
    } catch (_) {
      // Fallback: assume API 21 (Android 5.0)
      return _fallbackSdkInt;
    }
  }

  void _showSettingsDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: const Color(0xFF1A1A1A),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const Text(
          'Permissão necessária',
          style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700),
        ),
        content: const Text(
          'O YTDown precisa de permissão de armazenamento para salvar os downloads. '
          'Habilite nas configurações do app.',
          style: TextStyle(color: Color(0xFF9E9E9E), fontSize: 14),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancelar',
                style: TextStyle(color: Color(0xFF9E9E9E))),
          ),
          TextButton(
            onPressed: () {
              Navigator.pop(context);
              openAppSettings();
            },
            child: const Text(
              'Abrir configurações',
              style: TextStyle(
                  color: Color(0xFF60A5FA), fontWeight: FontWeight.w600),
            ),
          ),
        ],
      ),
    );
  }
}
