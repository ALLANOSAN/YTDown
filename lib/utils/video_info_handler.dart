import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../widgets/format_selection_sheet.dart';
import '../screens/playlist_selection_screen.dart';
import '../services/download_service.dart';

/// Handler centralizado para processar informações de vídeo/playlist
/// Evita duplicação de código entre HomeScreen e BrowserScreen
class VideoInfoHandler {
  VideoInfoHandler._();

  /// Processa informações de vídeo/playlist e navega para a tela apropriada
  static Future<void> handleVideoInfo(
    BuildContext context,
    Map<String, dynamic> info,
    String url,
  ) async {
    final payload = _VideoInfoPayload.fromInfo(info);

    if (!context.mounted) return;

    if (payload.isPlaylist && payload.entries != null) {
      await _openPlaylistSelection(
        context: context,
        url: url,
        payload: payload,
      );
      return;
    }

    await _openFormatSheet(
      context: context,
      url: url,
      payload: payload,
    );
  }

  static Future<void> _openPlaylistSelection({
    required BuildContext context,
    required String url,
    required _VideoInfoPayload payload,
  }) async {
    await _releaseInputConnection();
    if (!context.mounted) return;

    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => PlaylistSelectionScreen(
          url: url,
          title: payload.title,
          thumbnail: payload.effectiveThumbnail,
          entries: payload.entries!,
          totalDuration: payload.totalDuration,
          artist: payload.artist,
          album: payload.album,
        ),
      ),
    );
  }

  static Future<void> _openFormatSheet({
    required BuildContext context,
    required String url,
    required _VideoInfoPayload payload,
  }) async {
    await showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => FormatSelectionSheet(
        url: url,
        title: payload.title,
        thumbnail: payload.effectiveThumbnail,
        duration: payload.durationSeconds,
        artist: payload.artist,
        album: payload.album,
      ),
    );
  }

  static Future<void> _releaseInputConnection() async {
    final focusManager = FocusManager.instance;
    focusManager.primaryFocus?.unfocus();
    await SystemChannels.textInput.invokeMethod<void>('TextInput.hide');
    await Future<void>.delayed(const Duration(milliseconds: 150));
  }

  /// Fetch e processa info em uma única chamada
  static Future<void> fetchAndHandle(
    BuildContext context,
    String url,
  ) async {
    final downloadService = DownloadService.instance;
    final info = await downloadService.fetchVideoInfo(url);
    if (context.mounted) {
      await handleVideoInfo(context, info, url);
    }
  }
}

class _VideoInfoPayload {
  const _VideoInfoPayload({
    required this.isPlaylist,
    required this.title,
    required this.artist,
    required this.album,
    required this.thumbnail,
    required this.effectiveThumbnail,
    required this.entries,
    required this.durationSeconds,
    required this.totalDuration,
  });

  final bool isPlaylist;
  final String title;
  final String? artist;
  final String? album;
  final String? thumbnail;
  final String? effectiveThumbnail;
  final List<dynamic>? entries;
  final int durationSeconds;
  final int totalDuration;

  factory _VideoInfoPayload.fromInfo(Map<String, dynamic> info) {
    final isPlaylist = info['is_playlist'] == true;
    final entries = info['entries'] as List<dynamic>?;
    final thumbnail = info['thumbnail'] as String?;
    final effectiveThumbnail = _resolveThumbnail(
      thumbnail: thumbnail,
      isPlaylist: isPlaylist,
      entries: entries,
    );

    return _VideoInfoPayload(
      isPlaylist: isPlaylist,
      title: info['title'] as String? ?? 'Sem título',
      artist: info['artist'] as String?,
      album: info['album'] as String?,
      thumbnail: thumbnail,
      effectiveThumbnail: effectiveThumbnail,
      entries: entries,
      durationSeconds: (info['duration'] as num? ?? 0).toInt(),
      totalDuration: _resolveTotalDuration(entries),
    );
  }

  static String? _resolveThumbnail({
    required String? thumbnail,
    required bool isPlaylist,
    required List<dynamic>? entries,
  }) {
    if (thumbnail != null && thumbnail.trim().isNotEmpty) {
      return thumbnail;
    }

    if (!isPlaylist || entries == null || entries.isEmpty) {
      return null;
    }

    return entries.first['thumbnail'] as String?;
  }

  static int _resolveTotalDuration(List<dynamic>? entries) {
    if (entries == null) return 0;

    var total = 0;
    for (final entry in entries) {
      total += (entry['duration'] as num? ?? 0).toInt();
    }
    return total;
  }
}
