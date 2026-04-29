import 'dart:io';

import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import '../models/download_item.dart';

class ExportResult {
  const ExportResult({
    required this.success,
    this.exportedPath,
    this.error,
    this.stage,
    this.strategy,
    this.diagnostics,
  });

  final bool success;
  final String? exportedPath;
  final String? error;
  final String? stage;
  final String? strategy;
  final Map<String, dynamic>? diagnostics;
}

class StorageService {
  StorageService._();
  static final instance = StorageService._();

  static const _storageChannel = MethodChannel('com.example.ytdown/storage');

  Future<Directory> getSandboxDownloadsDir(DownloadType type) async {
    final appDir = await getApplicationDocumentsDirectory();
    final folder = type == DownloadType.audio ? 'Audios' : 'Videos';
    final dir = Directory(p.join(appDir.path, 'YTDown', folder));
    if (!await dir.exists()) {
      await dir.create(recursive: true);
    }
    return dir;
  }

  Future<ExportResult> exportToPublicCollection({
    required String sourcePath,
    required DownloadType type,
    String? displayName,
    bool allowUserInteractionFallback = false,
  }) async {
    final file = File(sourcePath);
    if (!await file.exists()) {
      return const ExportResult(
        success: false,
        error: 'Arquivo não encontrado para exportação',
      );
    }

    final fileName = _resolveDisplayName(
      sourcePath: sourcePath,
      displayName: displayName,
    );

    final mimeType = _guessMimeType(fileName, type);

    if (Platform.isAndroid) {
      return _invokeStorageChannel(
        method: 'exportToPublicCollection',
        arguments: {
          'sourcePath': sourcePath,
          'displayName': fileName,
          'mediaType': _resolveMediaType(type),
          'mimeType': mimeType,
          'allowUserInteractionFallback': allowUserInteractionFallback,
        },
      );
    }

    return _exportFileOutsideAndroid(
      file: file,
      type: type,
      fileName: fileName,
    );
  }

  Future<ExportResult> syncEditedFileToExported({
    required String sourcePath,
    required String exportedPath,
  }) async {
    final sourceFile = File(sourcePath);
    if (!await sourceFile.exists()) {
      return const ExportResult(
        success: false,
        error: 'Arquivo editado não encontrado para sincronização externa',
      );
    }

    if (Platform.isAndroid) {
      return _invokeStorageChannel(
        method: 'syncEditedExportedFile',
        arguments: {
          'sourcePath': sourcePath,
          'exportedPath': exportedPath,
        },
        fallbackExportedPath: exportedPath,
      );
    }

    if (exportedPath.startsWith('content://')) {
      return const ExportResult(
        success: false,
        error:
            'Sincronização com URI de conteúdo não suportada nesta plataforma',
      );
    }

    try {
      final targetFile = File(exportedPath);
      await targetFile.parent.create(recursive: true);
      if (await targetFile.exists()) {
        await targetFile.delete();
      }
      await sourceFile.copy(targetFile.path);
      return ExportResult(success: true, exportedPath: targetFile.path);
    } catch (e) {
      return ExportResult(success: false, error: e.toString());
    }
  }

  Future<bool> deleteExportedFile(String exportedPath) async {
    if (Platform.isAndroid) {
      try {
        final dynamic success = await _storageChannel.invokeMethod(
          'deleteExportedFile',
          {'exportedPath': exportedPath},
        );
        return success == true;
      } catch (e) {
        return false;
      }
    }

    if (exportedPath.startsWith('content://')) {
      return false;
    }

    try {
      final file = File(exportedPath);
      if (await file.exists()) {
        await file.delete();
      }
      return true;
    } catch (e) {
      return false;
    }
  }

  String _resolveDisplayName({
    required String sourcePath,
    required String? displayName,
  }) {
    if (displayName == null || displayName.trim().isEmpty) {
      return p.basename(sourcePath);
    }
    return displayName;
  }

  String _resolveMediaType(DownloadType type) {
    return type == DownloadType.audio ? 'audio' : 'video';
  }

  Future<ExportResult> _exportFileOutsideAndroid({
    required File file,
    required DownloadType type,
    required String fileName,
  }) async {
    try {
      final downloadsDir = await getDownloadsDirectory() ??
          await getApplicationDocumentsDirectory();
      final folder =
          type == DownloadType.audio ? 'YTDown/Audios' : 'YTDown/Videos';
      final targetDir = Directory(p.join(downloadsDir.path, folder));
      if (!await targetDir.exists()) {
        await targetDir.create(recursive: true);
      }

      final targetPath = p.join(targetDir.path, fileName);
      await file.copy(targetPath);

      return ExportResult(success: true, exportedPath: targetPath);
    } catch (e) {
      return ExportResult(success: false, error: e.toString());
    }
  }

  Future<ExportResult> _invokeStorageChannel({
    required String method,
    required Map<String, dynamic> arguments,
    String? fallbackExportedPath,
  }) async {
    try {
      final dynamic raw = await _storageChannel.invokeMethod(method, arguments);
      final map = _asStringDynamicMap(raw);
      return _buildExportResultFromMap(
        map,
        fallbackExportedPath: fallbackExportedPath,
      );
    } catch (e) {
      return ExportResult(success: false, error: e.toString());
    }
  }

  Map<String, dynamic> _asStringDynamicMap(dynamic raw) {
    if (raw is Map) {
      return Map<String, dynamic>.from(raw);
    }
    return <String, dynamic>{};
  }

  ExportResult _buildExportResultFromMap(
    Map<String, dynamic> map, {
    String? fallbackExportedPath,
  }) {
    final success = map['success'] == true;
    final exportedPath = map['exportedPath']?.toString() ??
        map['contentUri']?.toString() ??
        fallbackExportedPath;
    final diagnosticsRaw = map['diagnostics'];
    final diagnostics = diagnosticsRaw is Map
        ? Map<String, dynamic>.from(diagnosticsRaw)
        : null;

    return ExportResult(
      success: success,
      exportedPath: exportedPath,
      error: success ? null : map['error']?.toString(),
      stage: map['stage']?.toString(),
      strategy: map['strategy']?.toString(),
      diagnostics: diagnostics,
    );
  }

  String _guessMimeType(String fileName, DownloadType type) {
    final ext = p.extension(fileName).toLowerCase();
    switch (ext) {
      case '.mp3':
        return 'audio/mpeg';
      case '.m4a':
        return 'audio/mp4';
      case '.aac':
        return 'audio/aac';
      case '.ogg':
        return 'audio/ogg';
      case '.opus':
        return 'audio/ogg';
      case '.wav':
        return 'audio/wav';
      case '.flac':
        return 'audio/flac';
      case '.mp4':
        return 'video/mp4';
      case '.mkv':
        return 'video/x-matroska';
      case '.webm':
        return 'video/webm';
      default:
        return type == DownloadType.audio ? 'audio/*' : 'video/*';
    }
  }
}
