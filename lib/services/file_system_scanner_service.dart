import 'dart:io';
import 'dart:convert';
import 'package:crypto/crypto.dart';
import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as p;
import '../models/download_item.dart';
import '../services/storage_service.dart';
import '../services/database_service.dart';

/// Serviço que escaneia o filesystem em busca de arquivos de áudio
/// que não estão registrados no banco de dados e os adiciona automaticamente.
class FileSystemScannerService {
  FileSystemScannerService._();
  static final instance = FileSystemScannerService._();

  static const int _yieldBatchSize = 20;
  static const Duration _yieldDuration = Duration(milliseconds: 16);
  static final RegExp _trailingHexSuffixPattern =
      RegExp(r'[_-][0-9a-f]{6,}$', caseSensitive: false);
  static final RegExp _trailingUuidSuffixPattern = RegExp(
    r'[_-][0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
    caseSensitive: false,
  );

  // Extensões de áudio suportadas
  static const _audioExtensions = {
    '.mp3',
    '.m4a',
    '.aac',
    '.ogg',
    '.opus',
    '.wav',
    '.flac',
    '.webm',
    '.mp4'
  };

  /// Escaneia o diretório de downloads de áudio e retorna arquivos encontrados
  Future<List<File>> scanAudioFiles() async {
    try {
      final audioDir = await StorageService.instance
          .getSandboxDownloadsDir(DownloadType.audio);

      if (!await audioDir.exists()) {
        debugPrint('📂 Diretório de áudio não encontrado: ${audioDir.path}');
        return [];
      }

      final files = <File>[];
      await for (final entity in audioDir.list(recursive: true)) {
        if (entity is File && _isAudioFile(entity)) {
          files.add(entity);
        }
      }

      debugPrint(
          '🔍 Encontrados ${files.length} arquivos de áudio no filesystem');
      return files;
    } catch (e) {
      debugPrint('❌ Erro ao escanear filesystem: $e');
      return [];
    }
  }

  /// Verifica se um arquivo é um arquivo de áudio baseado na extensão
  bool _isAudioFile(File file) {
    final ext = p.extension(file.path).toLowerCase();
    return _audioExtensions.contains(ext);
  }

  /// Encontra arquivos órfãos (existem no filesystem mas não no banco)
  Future<List<File>> findOrphanFiles() async {
    final files = await scanAudioFiles();
    final dbItems = await DatabaseService.instance.getAllDownloads();
    final dbPaths = dbItems.map((item) => item.outputPath).toSet();

    final orphanFiles =
        files.where((file) => !dbPaths.contains(file.path)).toList();

    debugPrint('🔍 Encontrados ${orphanFiles.length} arquivos órfãos');
    return orphanFiles;
  }

  /// Registra arquivos órfãos no banco de dados como DownloadItem
  Future<int> registerOrphanFiles() async {
    final orphanFiles = await findOrphanFiles();
    final registered = await _registerOrphanFiles(orphanFiles);

    if (registered > 0) {
      debugPrint('🎉 Total de arquivos órfãos registrados: $registered');
    }

    return registered;
  }

  /// Cria um DownloadItem a partir de um arquivo
  Future<DownloadItem> _createDownloadItemFromFile(File file) async {
    final fileName = p.basenameWithoutExtension(file.path);
    final ext = p.extension(file.path).toLowerCase();

    // Tenta extrair informações do nome do arquivo
    // Formato esperado: safeTitle_timestamp ou apenas safeTitle
    final parts = fileName.split('_');
    String title = fileName;

    // Se tiver underscore, tenta separar o título
    if (parts.length > 1 && parts.last.length == 13) {
      // Último parte parece ser timestamp (13 dígitos)
      title = parts.sublist(0, parts.length - 1).join(' ');
    }

    // Remove sufixos comuns de identificador (hex/uuid) no final do nome.
    title = title.replaceAll(_trailingHexSuffixPattern, '');
    title = title.replaceAll(_trailingUuidSuffixPattern, '');

    // Limpa o título (substitui underscores e hífens por espaços)
    title = title.replaceAll('_', ' ').replaceAll('-', ' ').trim();

    if (title.toLowerCase() == 'videoplayback') {
      title = 'Faixa sem título';
    }

    // Capitaliza primeira letra de cada palavra
    title = _toTitleCase(title);

    final fileSize = await file.length();
    final stat = await file.stat();

    final String pathHash =
        sha256.convert(utf8.encode(file.path)).toString().substring(0, 12);

    return DownloadItem(
      id: 'orphan_$pathHash',
      url: '',
      title: title,
      type: DownloadType.audio,
      format: ext.substring(1), // Remove o ponto
      quality: '128',
      outputPath: file.path,
      status: DownloadStatus.completed,
      progress: 100.0,
      createdAt: stat.modified,
      fileSizeBytes: fileSize,
      artist: 'Desconhecido',
      album: 'YTDown',
    );
  }

  /// Executa o scan completo: encontra e registra arquivos órfãos
  Future<ScanResult> performFullScan() async {
    debugPrint('🚀 Iniciando scan completo do filesystem...');

    final startTime = DateTime.now();

    // Otimização: scan apenas UMA vez
    final files = await scanAudioFiles();
    final dbItems = await DatabaseService.instance.getAllDownloads();
    final dbPaths = dbItems.map((item) => item.outputPath).toSet();
    final orphanFiles =
        files.where((file) => !dbPaths.contains(file.path)).toList();

    final registered = await _registerOrphanFiles(orphanFiles);

    final duration = DateTime.now().difference(startTime);

    debugPrint('✅ Scan completo em ${duration.inMilliseconds}ms');
    debugPrint('   - Arquivos encontrados: ${files.length}');
    debugPrint('   - Arquivos órfãos: ${orphanFiles.length}');
    debugPrint('   - Arquivos registrados: $registered');

    return ScanResult(
      totalFilesFound: files.length,
      orphanFilesFound: orphanFiles.length,
      filesRegistered: registered,
      duration: duration,
    );
  }

  Future<int> _registerOrphanFiles(List<File> orphanFiles) async {
    var registered = 0;

    for (var index = 0; index < orphanFiles.length; index++) {
      final file = orphanFiles[index];

      try {
        await _yieldIfNeeded(index);
        final item = await _createDownloadItemFromFile(file);
        await DatabaseService.instance.insertDownload(item);
        registered += 1;
        debugPrint('✅ Arquivo registrado: ${item.title}');
      } catch (e) {
        debugPrint('❌ Erro ao registrar arquivo ${file.path}: $e');
      }
    }

    return registered;
  }

  Future<void> _yieldIfNeeded(int index) async {
    if (index <= 0 || index % _yieldBatchSize != 0) {
      return;
    }
    await Future.delayed(_yieldDuration);
  }

  String _toTitleCase(String input) {
    return input.split(' ').map((word) {
      if (word.isEmpty) {
        return word;
      }
      return word[0].toUpperCase() + word.substring(1).toLowerCase();
    }).join(' ');
  }
}

/// Resultado do scan do filesystem
class ScanResult {
  final int totalFilesFound;
  final int orphanFilesFound;
  final int filesRegistered;
  final Duration duration;

  const ScanResult({
    required this.totalFilesFound,
    required this.orphanFilesFound,
    required this.filesRegistered,
    required this.duration,
  });

  bool get hasOrphans => orphanFilesFound > 0;

  @override
  String toString() {
    return 'ScanResult(encontrados: $totalFilesFound, órfãos: $orphanFilesFound, registrados: $filesRegistered, tempo: ${duration.inMilliseconds}ms)';
  }
}
