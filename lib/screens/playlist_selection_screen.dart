import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../models/download_item.dart';
import '../services/download_service.dart';
import '../theme/app_theme.dart';

class PlaylistSelectionScreen extends ConsumerStatefulWidget {
  final String url;
  final String title;
  final String? thumbnail;
  final List<dynamic> entries;
  final int totalDuration;

  const PlaylistSelectionScreen({
    super.key,
    required this.url,
    required this.title,
    this.thumbnail,
    required this.entries,
    required this.totalDuration,
    this.artist,
    this.album,
  });

  final String? artist;
  final String? album;

  @override
  ConsumerState<PlaylistSelectionScreen> createState() =>
      _PlaylistSelectionScreenState();
}

class _PlaylistSelectionScreenState
    extends ConsumerState<PlaylistSelectionScreen> {
  static const String _audioDefaultFormat = 'mp3';
  static const String _audioDefaultQuality = '320';
  static const String _videoDefaultFormat = 'mp4';
  static const String _videoDefaultQuality = '1080p';

  bool _isDownloading = false;
  late Set<int> _selectedIndices;
  bool _isAudio = true;
  String _format = _audioDefaultFormat;
  String _quality = _audioDefaultQuality;
  late List<Map<String, dynamic>> _entries;
  late TextEditingController _globalArtistController;
  late TextEditingController _globalAlbumController;
  late FocusNode _globalArtistFocus;
  late FocusNode _globalAlbumFocus;

  @override
  void initState() {
    super.initState();
    _selectedIndices = Set.from(Iterable.generate(widget.entries.length));
    _entries =
        widget.entries.map((e) => Map<String, dynamic>.from(e as Map)).toList();
    _globalArtistController = TextEditingController(text: widget.artist);
    _globalAlbumController = TextEditingController(text: widget.album);
    _globalArtistFocus = FocusNode();
    _globalAlbumFocus = FocusNode();
  }

  @override
  void dispose() {
    _globalArtistController.dispose();
    _globalAlbumController.dispose();
    _globalArtistFocus.dispose();
    _globalAlbumFocus.dispose();
    super.dispose();
  }

  int get _currentTotalDuration {
    int total = 0;
    for (int i in _selectedIndices) {
      final entry = widget.entries[i];
      total += (entry['duration'] as num? ?? 0).toInt();
    }
    return total;
  }

  Set<int> get _allIndices {
    return Set<int>.from(Iterable<int>.generate(widget.entries.length));
  }

  String _trimmed(TextEditingController controller) {
    return controller.text.trim();
  }

  String? _trimToNull(TextEditingController controller) {
    final trimmed = _trimmed(controller);
    if (trimmed.isEmpty) {
      return null;
    }
    return trimmed;
  }

  Map<String, dynamic> _withGlobalMetadata(Map<String, dynamic> source) {
    final entry = Map<String, dynamic>.from(source);
    final globalArtist = _trimmed(_globalArtistController);
    final globalAlbum = _trimmed(_globalAlbumController);

    if (globalArtist.isNotEmpty &&
        (entry['artist'] == null || entry['artist'].toString().isEmpty)) {
      entry['artist'] = globalArtist;
    }
    if (globalAlbum.isNotEmpty &&
        (entry['album'] == null || entry['album'].toString().isEmpty)) {
      entry['album'] = globalAlbum;
    }

    return entry;
  }

  void _setEntrySelection(int index, {bool? selected}) {
    setState(() {
      final shouldSelect = selected ?? !_selectedIndices.contains(index);
      if (shouldSelect) {
        _selectedIndices.add(index);
        return;
      }
      _selectedIndices.remove(index);
    });
  }

  InputDecoration _buildGlobalMetadataDecoration(String label) {
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

  String _formatDuration(int seconds) {
    if (seconds <= 0) return '00:00';
    final h = seconds ~/ 3600;
    final m = (seconds % 3600) ~/ 60;
    final s = seconds % 60;

    if (h > 0) {
      return '$h:${m.toString().padLeft(2, '0')}:${s.toString().padLeft(2, '0')}';
    }
    return '$m:${s.toString().padLeft(2, '0')}';
  }

  void _toggleSelectAll() {
    setState(() {
      if (_selectedIndices.length == widget.entries.length) {
        _selectedIndices.clear();
        return;
      }
      _selectedIndices = _allIndices;
    });
  }

  Future<void> _startDownload({bool all = false}) async {
    final indicesToDownload =
        all ? _allIndices : Set<int>.from(_selectedIndices);

    if (indicesToDownload.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Selecione ao menos um vídeo')),
      );
      return;
    }

    setState(() => _isDownloading = true);
    HapticFeedback.heavyImpact();

    final processedEntries = indicesToDownload
        .map((index) => _withGlobalMetadata(_entries[index]))
        .toList();
    final downloadService = DownloadService.instance;

    await downloadService.startDownload(
      url: widget.url,
      title: widget.title.replaceFirst('Playlist: ', ''),
      thumbnail: widget.thumbnail,
      type: _isAudio ? DownloadType.audio : DownloadType.video,
      format: _format,
      quality: _quality,
      isPlaylist: true,
      entries: processedEntries,
      artist: _trimToNull(_globalArtistController),
      album: _trimToNull(_globalAlbumController),
    );

    if (mounted) {
      Navigator.pop(context);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content:
              Text('Download de ${indicesToDownload.length} itens iniciado!'),
          backgroundColor: AppTheme.success,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      resizeToAvoidBottomInset: true,
      backgroundColor: AppTheme.surface,
      appBar: AppBar(
        title: const Text('Configurar Playlist'),
        leading: IconButton(
          icon: const Icon(Icons.close_rounded),
          onPressed: () => Navigator.pop(context),
        ),
        actions: [
          TextButton(
            onPressed: _toggleSelectAll,
            child: Text(
              _selectedIndices.length == widget.entries.length
                  ? 'Desmarcar Tudo'
                  : 'Selecionar Tudo',
              style: const TextStyle(color: AppTheme.primary),
            ),
          ),
        ],
      ),
      // ✅ Barra inferior fixada fora do body — o teclado nunca
      // compete com ela no layout, eliminando o efeito de "expulsão".
      bottomNavigationBar: _buildBottomActions(),
      body: Column(
        children: [
          _buildHeader(),
          _buildFormatSelector(),
          if (_isAudio) _buildGlobalMetadataInputs(),
          const Divider(height: 1, color: AppTheme.border),
          Expanded(
            child: _buildEntriesList(bottomPadding: 16),
          ),
        ],
      ),
    );
  }

  Widget _buildHeader() {
    return Container(
      padding: const EdgeInsets.all(20),
      color: AppTheme.card,
      child: Row(
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: widget.thumbnail != null
                ? CachedNetworkImage(
                    imageUrl: widget.thumbnail!,
                    width: 100,
                    height: 60,
                    fit: BoxFit.cover,
                  )
                : Container(
                    width: 100,
                    height: 60,
                    color: AppTheme.surface,
                    child: const Icon(Icons.playlist_play_rounded,
                        color: AppTheme.textSecondary, size: 32),
                  ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  widget.title,
                  style: const TextStyle(
                    color: AppTheme.textPrimary,
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                  ),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 6),
                Row(
                  children: [
                    Icon(Icons.video_library_rounded,
                        size: 14, color: AppTheme.textSecondary),
                    const SizedBox(width: 4),
                    Text(
                      '${_selectedIndices.length} de ${widget.entries.length} vídeos',
                      style: const TextStyle(
                          color: AppTheme.textSecondary, fontSize: 13),
                    ),
                    const SizedBox(width: 12),
                    Icon(Icons.access_time_rounded,
                        size: 14, color: AppTheme.textSecondary),
                    const SizedBox(width: 4),
                    Text(
                      _formatDuration(_currentTotalDuration),
                      style: const TextStyle(
                          color: AppTheme.textSecondary, fontSize: 13),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFormatSelector() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(
                child: _buildTypeButton(
                  label: '🎵 Áudio',
                  isSelected: _isAudio,
                  onTap: () => setState(() {
                    _isAudio = true;
                    _format = _audioDefaultFormat;
                    _quality = _audioDefaultQuality;
                  }),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _buildTypeButton(
                  label: '🎬 Vídeo',
                  isSelected: !_isAudio,
                  onTap: () => setState(() {
                    _isAudio = false;
                    _format = _videoDefaultFormat;
                    _quality = _videoDefaultQuality;
                  }),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          _buildDetailOptions(),
        ],
      ),
    );
  }

  Widget _buildGlobalMetadataInputs() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
      child: Row(
        children: [
          Expanded(
            child: TextField(
              controller: _globalArtistController,
              focusNode: _globalArtistFocus,
              style: const TextStyle(color: AppTheme.textPrimary, fontSize: 13),
              textInputAction: TextInputAction.next,
              decoration: _buildGlobalMetadataDecoration('Artista Global'),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: TextField(
              controller: _globalAlbumController,
              focusNode: _globalAlbumFocus,
              style: const TextStyle(color: AppTheme.textPrimary, fontSize: 13),
              textInputAction: TextInputAction.done,
              decoration: _buildGlobalMetadataDecoration('Álbum Global'),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTypeButton({
    required String label,
    required bool isSelected,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: const EdgeInsets.symmetric(vertical: 12),
        decoration: BoxDecoration(
          color: isSelected ? AppTheme.primary : AppTheme.card,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: isSelected ? AppTheme.primary : AppTheme.border,
          ),
        ),
        child: Center(
          child: Text(
            label,
            style: TextStyle(
              color: isSelected ? Colors.white : AppTheme.textSecondary,
              fontWeight: FontWeight.bold,
              fontSize: 14,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildDetailOptions() {
    final options =
        _isAudio ? FormatOptions.audioFormats : FormatOptions.videoFormats;
    final details = _isAudio
        ? (FormatOptions.audioBitrates[_format] ?? [])
        : FormatOptions.videoResolutions;

    return Column(
      children: [
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: options.map((opt) {
              final selected = _format == opt;
              return Padding(
                padding: const EdgeInsets.only(right: 8),
                child: ChoiceChip(
                  label: Text(opt.toUpperCase()),
                  selected: selected,
                  onSelected: (val) {
                    if (val) {
                      setState(() {
                        _format = opt;
                        if (_isAudio) {
                          _quality = FormatOptions.audioBitrates[opt]!.last;
                        }
                      });
                    }
                  },
                  selectedColor: AppTheme.primary.withValues(alpha: 0.2),
                  labelStyle: TextStyle(
                    color: selected ? AppTheme.primary : AppTheme.textSecondary,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              );
            }).toList(),
          ),
        ),
        const SizedBox(height: 8),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: details.map((det) {
              final selected = _quality == det;
              return Padding(
                padding: const EdgeInsets.only(right: 8),
                child: ChoiceChip(
                  label: Text(_isAudio ? '$det kbps' : det),
                  selected: selected,
                  onSelected: (val) {
                    if (val) {
                      setState(() => _quality = det);
                    }
                  },
                  selectedColor: AppTheme.primary.withValues(alpha: 0.2),
                  labelStyle: TextStyle(
                    color: selected ? AppTheme.primary : AppTheme.textSecondary,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              );
            }).toList(),
          ),
        ),
      ],
    );
  }

  Widget _buildEntriesList({double bottomPadding = 8}) {
    return ListView.builder(
      keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
      itemCount: _entries.length,
      padding: EdgeInsets.fromLTRB(0, 8, 0, bottomPadding),
      itemBuilder: (context, index) {
        final entry = _entries[index];
        final isSelected = _selectedIndices.contains(index);
        final duration = (entry['duration'] as num? ?? 0).toInt();

        return ListTile(
          onTap: () => _setEntrySelection(index),
          leading: Stack(
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(6),
                child: CachedNetworkImage(
                  imageUrl: entry['thumbnail'] as String? ?? '',
                  width: 70,
                  height: 40,
                  fit: BoxFit.cover,
                  errorWidget: (_, __, ___) => Container(
                    width: 70,
                    height: 40,
                    color: AppTheme.card,
                    child: const Icon(Icons.video_library_rounded, size: 20),
                  ),
                ),
              ),
              Positioned(
                bottom: 2,
                right: 2,
                child: Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
                  decoration: BoxDecoration(
                    color: Colors.black.withValues(alpha: 0.7),
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Text(
                    _formatDuration(duration),
                    style: const TextStyle(
                        color: Colors.white,
                        fontSize: 9,
                        fontWeight: FontWeight.bold),
                  ),
                ),
              ),
            ],
          ),
          title: Text(
            entry['title'] as String? ?? 'Sem título',
            style: const TextStyle(
                color: AppTheme.textPrimary,
                fontSize: 13,
                fontWeight: FontWeight.w500),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
          trailing: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              IconButton(
                icon:
                    const Icon(Icons.edit_note_rounded, color: AppTheme.accent),
                onPressed: () => _showEditDialog(index),
              ),
              Checkbox(
                value: isSelected,
                activeColor: AppTheme.primary,
                onChanged: (val) {
                  if (val == null) {
                    return;
                  }
                  _setEntrySelection(index, selected: val);
                },
              ),
            ],
          ),
        );
      },
    );
  }

  Future<void> _showEditDialog(int index) async {
    final entry = _entries[index];
    final titleController = TextEditingController(text: entry['title']);
    final artistController = TextEditingController(
        text: entry['artist'] ?? _globalArtistController.text);
    final albumController = TextEditingController(
        text: entry['album'] ?? _globalAlbumController.text);

    // FocusNodes explicitos para evitar que o teclado feche ao reconstruir
    final titleFocus = FocusNode();
    final artistFocus = FocusNode();
    final albumFocus = FocusNode();

    try {
      await showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        backgroundColor: Colors.transparent,
        builder: (sheetContext) {
          final viewInsets = MediaQuery.viewInsetsOf(sheetContext);
          return Padding(
            padding: EdgeInsets.only(bottom: viewInsets.bottom),
            child: Container(
              decoration: const BoxDecoration(
                color: AppTheme.surface,
                borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
              ),
              child: SafeArea(
                top: false,
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Container(
                        width: 40,
                        height: 4,
                        decoration: BoxDecoration(
                          color: AppTheme.border,
                          borderRadius: BorderRadius.circular(999),
                        ),
                      ),
                      const SizedBox(height: 12),
                      const Align(
                        alignment: Alignment.centerLeft,
                        child: Text(
                          'Editar Metadados',
                          style: TextStyle(
                            color: AppTheme.textPrimary,
                            fontSize: 16,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: titleController,
                        focusNode: titleFocus,
                        autofocus: true,
                        style: const TextStyle(color: AppTheme.textPrimary),
                        textInputAction: TextInputAction.next,
                        decoration: const InputDecoration(labelText: 'Título'),
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: artistController,
                        focusNode: artistFocus,
                        style: const TextStyle(color: AppTheme.textPrimary),
                        textInputAction: TextInputAction.next,
                        decoration: const InputDecoration(labelText: 'Artista'),
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: albumController,
                        focusNode: albumFocus,
                        style: const TextStyle(color: AppTheme.textPrimary),
                        textInputAction: TextInputAction.done,
                        decoration: const InputDecoration(labelText: 'Álbum'),
                      ),
                      const SizedBox(height: 16),
                      Row(
                        children: [
                          Expanded(
                            child: OutlinedButton(
                              onPressed: () => Navigator.pop(sheetContext),
                              child: const Text('Cancelar'),
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: ElevatedButton(
                              onPressed: () {
                                setState(() {
                                  _entries[index]['title'] =
                                      titleController.text.trim();
                                  _entries[index]['artist'] =
                                      artistController.text.trim();
                                  _entries[index]['album'] =
                                      albumController.text.trim();
                                });
                                Navigator.pop(sheetContext);
                              },
                              child: const Text('Salvar'),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ),
          );
        },
      );
    } finally {
      // Limpar FocusNodes apos fechar dialogo
      titleFocus.dispose();
      artistFocus.dispose();
      albumFocus.dispose();
      titleController.dispose();
      artistController.dispose();
      albumController.dispose();
    }
  }

  Widget _buildBottomActions() {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppTheme.card,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.1),
            blurRadius: 10,
            offset: const Offset(0, -2),
          ),
        ],
      ),
      child: Row(
        children: [
          Expanded(
            child: OutlinedButton(
              onPressed:
                  _isDownloading ? null : () => _startDownload(all: true),
              style: OutlinedButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 16),
                side: const BorderSide(color: AppTheme.primary),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
              ),
              child: const Text('Baixar Tudo',
                  style: TextStyle(fontWeight: FontWeight.bold)),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            flex: 2,
            child: ElevatedButton(
              onPressed: _isDownloading ? null : () => _startDownload(),
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 16),
                backgroundColor: AppTheme.primary,
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
              ),
              child: _isDownloading
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white))
                  : Text('Baixar Selecionados (${_selectedIndices.length})',
                      style: const TextStyle(
                          fontWeight: FontWeight.bold, color: Colors.white)),
            ),
          ),
        ],
      ),
    );
  }
}
