import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../models/download_item.dart';
import '../services/download_service.dart';
import '../services/database_service.dart';
import '../theme/app_theme.dart';

class FormatSelectionSheet extends StatefulWidget {
  final String url;
  final String title;
  final String? thumbnail;
  final int duration;
  final bool isPlaylist;
  final List<dynamic>? entries;

  const FormatSelectionSheet({
    super.key,
    required this.url,
    required this.title,
    this.thumbnail,
    required this.duration,
    this.isPlaylist = false,
    this.entries,
    this.artist,
    this.album,
  });

  final String? artist;
  final String? album;

  @override
  State<FormatSelectionSheet> createState() => _FormatSelectionSheetState();
}

class _FormatSelectionSheetState extends State<FormatSelectionSheet>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;

  // Áudio
  String _audioFormat = 'mp3';
  String _audioBitrate = '320';

  // Vídeo
  String _videoFormat = 'mp4';
  String _videoResolution = '1080p';

  bool _isDownloading = false;
  bool _isFavorite = false;
  late TextEditingController _artistController;
  late TextEditingController _albumController;
  late FocusNode _artistFocus;
  late FocusNode _albumFocus;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _artistController = TextEditingController(text: widget.artist);
    _albumController = TextEditingController(text: widget.album);
    _artistFocus = FocusNode();
    _albumFocus = FocusNode();
    _checkFavorite();
  }

  Future<void> _checkFavorite() async {
    try {
      final databaseService = DatabaseService.instance;
      final isFav = await databaseService.isFavorite(widget.url);
      if (mounted) {
        setState(() => _isFavorite = isFav);
      }
    } catch (_) {
      if (mounted) {
        setState(() => _isFavorite = false);
      }
    }
  }

  Future<void> _toggleFavorite() async {
    try {
      final databaseService = DatabaseService.instance;
      await databaseService.toggleFavorite(
        url: widget.url,
        title: widget.title,
        thumbnail: widget.thumbnail,
        type: widget.isPlaylist ? 'playlist' : 'video',
      );
      _checkFavorite();
      HapticFeedback.mediumImpact();
    } catch (_) {
      // Ignore favorite toggle errors in UI flow.
    }
  }

  @override
  void dispose() {
    _tabController.dispose();
    _artistController.dispose();
    _albumController.dispose();
    _artistFocus.dispose();
    _albumFocus.dispose();
    super.dispose();
  }

  String _formatDuration(int seconds) {
    final m = seconds ~/ 60;
    final s = seconds % 60;
    return '$m:${s.toString().padLeft(2, '0')}';
  }

  String? _trimToNull(String value) {
    final trimmed = value.trim();
    if (trimmed.isEmpty) {
      return null;
    }
    return trimmed;
  }

  String _videoTitleWithContext() {
    if (!widget.isPlaylist || widget.entries == null) {
      return widget.title;
    }
    return '${widget.title} (${widget.entries!.length} itens)';
  }

  InputDecoration _buildMetadataInputDecoration(String label) {
    return InputDecoration(
      labelText: label,
      labelStyle: const TextStyle(color: AppTheme.textSecondary, fontSize: 12),
      filled: true,
      fillColor: AppTheme.card,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: BorderSide.none,
      ),
      contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      isDense: true,
    );
  }

  Future<void> _download() async {
    setState(() => _isDownloading = true);
    HapticFeedback.heavyImpact();

    final isAudio = _tabController.index == 0;
    final downloadService = DownloadService.instance;

    await downloadService.startDownload(
      url: widget.url,
      title: widget.title,
      thumbnail: widget.thumbnail,
      type: isAudio ? DownloadType.audio : DownloadType.video,
      format: isAudio ? _audioFormat : _videoFormat,
      quality: isAudio ? _audioBitrate : _videoResolution,
      isPlaylist: widget.isPlaylist,
      entries: widget.entries,
      artist: _trimToNull(_artistController.text),
      album: _trimToNull(_albumController.text),
    );

    if (mounted) {
      Navigator.pop(context);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Download iniciado!'),
          backgroundColor: AppTheme.success,
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      child: SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _buildHandle(),
            _buildVideoInfo(),
            _buildTabs(),
            _buildMetadataInputs(),
            _buildTabContent(),
            _buildDownloadButton(),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }

  Widget _buildHandle() {
    return Container(
      margin: const EdgeInsets.only(top: 12),
      width: 36,
      height: 4,
      decoration: BoxDecoration(
        color: AppTheme.border,
        borderRadius: BorderRadius.circular(2),
      ),
    );
  }

  Widget _buildVideoInfo() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
      child: Row(
        children: [
          // Thumbnail
          ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: widget.thumbnail != null
                ? CachedNetworkImage(
                    imageUrl: widget.thumbnail!,
                    width: 80,
                    height: 50,
                    fit: BoxFit.cover,
                  )
                : Container(
                    width: 80,
                    height: 50,
                    color: AppTheme.card,
                    child: const Icon(Icons.videocam_rounded,
                        color: AppTheme.textSecondary),
                  ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  _videoTitleWithContext(),
                  style: const TextStyle(
                    color: AppTheme.textPrimary,
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                  ),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 4),
                Text(
                  _formatDuration(widget.duration),
                  style: const TextStyle(
                      color: AppTheme.textSecondary, fontSize: 13),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          IconButton(
            onPressed: _toggleFavorite,
            icon: Icon(
              _isFavorite
                  ? Icons.favorite_rounded
                  : Icons.favorite_border_rounded,
              color: _isFavorite ? AppTheme.accent : AppTheme.textTertiary,
              size: 26,
            ),
            tooltip: _isFavorite
                ? 'Remover dos favoritos'
                : 'Adicionar aos favoritos',
          ),
        ],
      ),
    );
  }

  Widget _buildTabs() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
      child: Container(
        decoration: BoxDecoration(
          color: AppTheme.card,
          borderRadius: BorderRadius.circular(12),
        ),
        child: TabBar(
          controller: _tabController,
          indicator: BoxDecoration(
            color: AppTheme.accent,
            borderRadius: BorderRadius.circular(10),
          ),
          indicatorSize: TabBarIndicatorSize.tab,
          labelColor: Colors.white,
          unselectedLabelColor: AppTheme.textSecondary,
          labelStyle:
              const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
          tabs: const [
            Tab(text: '🎵 Áudio'),
            Tab(text: '🎬 Vídeo'),
          ],
        ),
      ),
    );
  }

  Widget _buildTabContent() {
    return SizedBox(
      height: 280, // Aumentado para acomodar mais formatos de áudio
      child: TabBarView(
        controller: _tabController,
        children: [
          _buildAudioOptions(),
          _buildVideoOptions(),
        ],
      ),
    );
  }

  Widget _buildMetadataInputs() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
      child: Row(
        children: [
          Expanded(
            child: TextField(
              controller: _artistController,
              focusNode: _artistFocus,
              style: const TextStyle(color: AppTheme.textPrimary, fontSize: 13),
              textInputAction: TextInputAction.next,
              decoration: _buildMetadataInputDecoration('Artista'),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: TextField(
              controller: _albumController,
              focusNode: _albumFocus,
              style: const TextStyle(color: AppTheme.textPrimary, fontSize: 13),
              textInputAction: TextInputAction.done,
              decoration: _buildMetadataInputDecoration('Álbum'),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAudioOptions() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const SizedBox(height: 12),
          const Text('Formato',
              style: TextStyle(
                  color: AppTheme.textSecondary,
                  fontSize: 12,
                  fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          _buildChipGroup(
            options: FormatOptions.audioFormats,
            selected: _audioFormat,
            onSelected: (v) => setState(() {
              _audioFormat = v;
              final bitrates = FormatOptions.audioBitrates[v]!;
              _audioBitrate = bitrates.last;
            }),
            labels: {
              'mp3': 'MP3',
              'm4a': 'M4A',
              'wav': 'WAV',
              'flac': 'FLAC',
              'opus': 'OPUS',
              'ogg': 'OGG',
              'aac': 'AAC'
            },
          ),
          const SizedBox(height: 16),
          const Text('Qualidade',
              style: TextStyle(
                  color: AppTheme.textSecondary,
                  fontSize: 12,
                  fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          _buildChipGroup(
            options: FormatOptions.audioBitrates[_audioFormat]!,
            selected: _audioBitrate,
            onSelected: (v) => setState(() => _audioBitrate = v),
            labels: {
              '128': '128 kbps',
              '192': '192 kbps',
              '256': '256 kbps',
              '320': '320 kbps',
              '160': '160 kbps',
              'lossless': 'Lossless',
            },
          ),
        ],
      ),
    );
  }

  Widget _buildVideoOptions() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const SizedBox(height: 12),
          const Text('Formato',
              style: TextStyle(
                  color: AppTheme.textSecondary,
                  fontSize: 12,
                  fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          _buildChipGroup(
            options: FormatOptions.videoFormats,
            selected: _videoFormat,
            onSelected: (v) => setState(() => _videoFormat = v),
            labels: {'mp4': 'MP4', 'mkv': 'MKV'},
          ),
          const SizedBox(height: 16),
          const Text('Resolução',
              style: TextStyle(
                  color: AppTheme.textSecondary,
                  fontSize: 12,
                  fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: FormatOptions.videoResolutions.map((r) {
              final selected = _videoResolution == r;
              return GestureDetector(
                onTap: () => setState(() => _videoResolution = r),
                child: Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                  decoration: BoxDecoration(
                    color: selected ? AppTheme.accent : AppTheme.card,
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(
                      color: selected ? AppTheme.accent : AppTheme.border,
                    ),
                  ),
                  child: Text(
                    r,
                    style: TextStyle(
                      color: selected ? Colors.white : AppTheme.textSecondary,
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
        ],
      ),
    );
  }

  Widget _buildChipGroup({
    required List<String> options,
    required String selected,
    required Function(String) onSelected,
    Map<String, String>? labels,
  }) {
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: options.map((o) {
        final isSelected = selected == o;
        final label = labels?[o] ?? o;
        return GestureDetector(
          onTap: () {
            HapticFeedback.selectionClick();
            onSelected(o);
          },
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
            decoration: BoxDecoration(
              color: isSelected ? AppTheme.accent : AppTheme.card,
              borderRadius: BorderRadius.circular(20),
              border: Border.all(
                color: isSelected ? AppTheme.accent : AppTheme.border,
              ),
            ),
            child: Text(
              label,
              style: TextStyle(
                color: isSelected ? Colors.white : AppTheme.textSecondary,
                fontSize: 13,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        );
      }).toList(),
    );
  }

  Widget _buildDownloadButton() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 8),
      child: SizedBox(
        width: double.infinity,
        child: ElevatedButton.icon(
          onPressed: _isDownloading ? null : _download,
          icon: _isDownloading
              ? const SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(
                      strokeWidth: 2, color: Colors.white),
                )
              : const Icon(Icons.download_rounded),
          label: Text(_isDownloading ? 'Iniciando...' : 'Baixar'),
        ),
      ),
    );
  }
}
