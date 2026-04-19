import 'package:flutter_test/flutter_test.dart';
import 'package:ytdown/models/download_item.dart';

void main() {
  test('DownloadItem serializa e desserializa corretamente', () {
    final now = DateTime(2025, 1, 2, 3, 4, 5);
    final item = DownloadItem(
      id: 'abc123',
      url: 'https://youtu.be/xyz',
      title: 'Video de teste',
      thumbnail: 'https://img.youtube.com/vi/xyz/maxresdefault.jpg',
      type: DownloadType.audio,
      format: 'mp3',
      quality: '320',
      outputPath: '/tmp/video.mp3',
      status: DownloadStatus.completed,
      progress: 1.0,
      errorMessage: null,
      createdAt: now,
      fileSizeBytes: 1048576,
    );

    final restored = DownloadItem.fromMap(item.toMap());

    expect(restored.id, item.id);
    expect(restored.url, item.url);
    expect(restored.title, item.title);
    expect(restored.thumbnail, item.thumbnail);
    expect(restored.type, item.type);
    expect(restored.format, item.format);
    expect(restored.quality, item.quality);
    expect(restored.outputPath, item.outputPath);
    expect(restored.status, item.status);
    expect(restored.progress, item.progress);
    expect(restored.errorMessage, item.errorMessage);
    expect(restored.createdAt, item.createdAt);
    expect(restored.fileSizeBytes, item.fileSizeBytes);
  });

  test('DownloadItem qualityLabel e statusLabel respeitam tipo e status', () {
    final audioItem = DownloadItem(
      id: '1',
      url: 'https://youtube.com/watch?v=1',
      title: 'Audio',
      type: DownloadType.audio,
      format: 'm4a',
      quality: '192',
      outputPath: '/tmp/audio.m4a',
      status: DownloadStatus.downloading,
      progress: 0.3,
    );

    final videoItem = DownloadItem(
      id: '2',
      url: 'https://youtube.com/watch?v=2',
      title: 'Video',
      type: DownloadType.video,
      format: 'mp4',
      quality: '1080p',
      outputPath: '/tmp/video.mp4',
      status: DownloadStatus.failed,
      progress: 0.0,
      errorMessage: 'Erro',
    );

    expect(audioItem.qualityLabel, '192 kbps');
    expect(audioItem.statusLabel, 'Baixando...');

    expect(videoItem.qualityLabel, '1080p');
    expect(videoItem.statusLabel, 'Erro');
  });

  test('FormatOptions gera argumentos esperados', () {
    final audioArgs = FormatOptions.getAudioArgs('mp3', '320');
    final videoArgs = FormatOptions.getVideoArgs('mkv', '1080p');

    expect(
        audioArgs, containsAll(['--extract-audio', '--audio-format', 'mp3']));
    expect(audioArgs, containsAll(['--audio-quality', '320K']));

    expect(videoArgs, contains('-f'));
    expect(videoArgs,
        contains('bestvideo[height<=1080]+bestaudio/best[height<=1080]'));
    expect(videoArgs, containsAll(['--merge-output-format', 'mkv']));
  });
}
