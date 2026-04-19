enum DownloadStatus { queued, downloading, completed, failed }

enum DownloadType { audio, video }

enum ExportStatus { pending, exported, failed }

extension DownloadTypeLabel on DownloadType {
  String get label {
    switch (this) {
      case DownloadType.audio:
        return 'Áudio';
      case DownloadType.video:
        return 'Vídeo';
    }
  }
}

extension DownloadStatusLabel on DownloadStatus {
  String get label {
    switch (this) {
      case DownloadStatus.queued:
        return 'Na fila';
      case DownloadStatus.downloading:
        return 'Baixando...';
      case DownloadStatus.completed:
        return 'Concluído';
      case DownloadStatus.failed:
        return 'Erro';
    }
  }
}

extension ExportStatusLabel on ExportStatus {
  String get label {
    switch (this) {
      case ExportStatus.pending:
        return 'Somente no app';
      case ExportStatus.exported:
        return 'Exportado';
      case ExportStatus.failed:
        return 'Falha ao exportar';
    }
  }
}

class DownloadItem {
  final String id;
  final String url;
  final String title;
  final String? thumbnail;
  final DownloadType type;
  final String format; // mp3, m4a, flac, mp4, mkv
  final String quality; // bitrate ou resolução
  final String outputPath;
  DownloadStatus status;
  double progress; // 0.0 a 1.0
  String? errorMessage;
  final DateTime createdAt;
  int? fileSizeBytes;
  String? exportedPath;
  ExportStatus exportStatus;

  // Novos campos para Biblioteca e Player
  String? artist;
  String? album;
  String? artistImageUrl;
  String? albumImageUrl;

  DownloadItem({
    required this.id,
    required this.url,
    required this.title,
    this.thumbnail,
    required this.type,
    required this.format,
    required this.quality,
    required this.outputPath,
    this.status = DownloadStatus.queued,
    this.progress = 0.0,
    this.errorMessage,
    DateTime? createdAt,
    this.fileSizeBytes,
    this.exportedPath,
    this.exportStatus = ExportStatus.pending,
    this.artist,
    this.album,
    this.artistImageUrl,
    this.albumImageUrl,
  }) : createdAt = createdAt ?? DateTime.now();

  String get typeLabel => type.label;

  String get qualityLabel {
    if (type == DownloadType.audio) return '$quality kbps';
    return quality;
  }

  String get statusLabel => status.label;

  String get exportStatusLabel => exportStatus.label;

  Map<String, dynamic> toMap() => {
        'id': id,
        'url': url,
        'title': title,
        'thumbnail': thumbnail,
        'type': type.index,
        'format': format,
        'quality': quality,
        'outputPath': outputPath,
        'status': status.index,
        'progress': progress,
        'errorMessage': errorMessage,
        'createdAt': createdAt.millisecondsSinceEpoch,
        'fileSizeBytes': fileSizeBytes,
        'exportedPath': exportedPath,
        'exportStatus': exportStatus.name,
        'artist': artist,
        'album': album,
        'artistImageUrl': artistImageUrl,
        'albumImageUrl': albumImageUrl,
      };

  factory DownloadItem.fromMap(Map<String, dynamic> map) {
    return DownloadItem(
      id: map['id'] as String,
      url: map['url'] as String,
      title: map['title'] as String,
      thumbnail: map['thumbnail'] as String?,
      type: _parseDownloadType(map['type']),
      format: map['format'] as String,
      quality: map['quality'] as String,
      outputPath: map['outputPath'] as String,
      status: _parseDownloadStatus(map['status']),
      progress: (map['progress'] as num?)?.toDouble() ?? 0.0,
      errorMessage: map['errorMessage'] as String?,
      createdAt: _parseCreatedAt(map['createdAt']),
      fileSizeBytes: map['fileSizeBytes'] as int?,
      exportedPath: map['exportedPath'] as String?,
      exportStatus: _parseExportStatus(map['exportStatus'] as String?),
      artist: map['artist'] as String?,
      album: map['album'] as String?,
      artistImageUrl: map['artistImageUrl'] as String?,
      albumImageUrl: map['albumImageUrl'] as String?,
    );
  }

  static DownloadType _parseDownloadType(dynamic value) {
    if (value is int && value >= 0 && value < DownloadType.values.length) {
      return DownloadType.values[value];
    }
    return DownloadType.audio;
  }

  static DownloadStatus _parseDownloadStatus(dynamic value) {
    if (value is int && value >= 0 && value < DownloadStatus.values.length) {
      return DownloadStatus.values[value];
    }
    return DownloadStatus.queued;
  }

  static DateTime _parseCreatedAt(dynamic value) {
    if (value is int) {
      return DateTime.fromMillisecondsSinceEpoch(value);
    }
    return DateTime.now();
  }

  static ExportStatus _parseExportStatus(String? value) {
    if (value == null) return ExportStatus.pending;
    for (final status in ExportStatus.values) {
      if (status.name == value) {
        return status;
      }
    }
    return ExportStatus.pending;
  }

  DownloadItem copyWith({
    String? id,
    String? url,
    String? title,
    String? thumbnail,
    DownloadType? type,
    String? format,
    String? quality,
    String? outputPath,
    DownloadStatus? status,
    double? progress,
    String? errorMessage,
    DateTime? createdAt,
    int? fileSizeBytes,
    String? exportedPath,
    ExportStatus? exportStatus,
    String? artist,
    String? album,
    String? artistImageUrl,
    String? albumImageUrl,
  }) {
    return DownloadItem(
      id: id ?? this.id,
      url: url ?? this.url,
      title: title ?? this.title,
      thumbnail: thumbnail ?? this.thumbnail,
      type: type ?? this.type,
      format: format ?? this.format,
      quality: quality ?? this.quality,
      outputPath: outputPath ?? this.outputPath,
      status: status ?? this.status,
      progress: progress ?? this.progress,
      errorMessage: errorMessage ?? this.errorMessage,
      createdAt: createdAt ?? this.createdAt,
      fileSizeBytes: fileSizeBytes ?? this.fileSizeBytes,
      exportedPath: exportedPath ?? this.exportedPath,
      exportStatus: exportStatus ?? this.exportStatus,
      artist: artist ?? this.artist,
      album: album ?? this.album,
      artistImageUrl: artistImageUrl ?? this.artistImageUrl,
      albumImageUrl: albumImageUrl ?? this.albumImageUrl,
    );
  }
}

// Opções de formato disponíveis
class FormatOptions {
  FormatOptions._();

  static const audioFormats = [
    'mp3',
    'm4a',
    'wav',
    'flac',
    'opus',
    'ogg',
    'aac'
  ];

  static const audioBitrates = {
    'mp3': ['128', '192', '256', '320'],
    'm4a': ['128', '192', '256'],
    'wav': ['lossless'],
    'flac': ['lossless'],
    'opus': ['128', '160', '192'],
    'ogg': ['128', '192', '320'],
    'aac': ['128', '192', '256'],
  };

  static const videoFormats = ['mp4', 'mkv'];

  static const videoResolutions = [
    '360p',
    '480p',
    '720p',
    '1080p',
    '1440p',
    '2160p'
  ];

  static List<String> getAudioArgs(String format, String bitrate) {
    final List<String> args = ['--extract-audio', '--audio-format', format];
    if (bitrate != 'lossless') {
      args.addAll(['--audio-quality', '${bitrate}K']);
    }
    return args;
  }

  static List<String> getVideoArgs(String format, String resolution) {
    final height = resolution.replaceAll('p', '');
    return [
      '-f',
      'bestvideo[height<=$height]+bestaudio/best[height<=$height]',
      '--merge-output-format',
      format
    ];
  }
}
