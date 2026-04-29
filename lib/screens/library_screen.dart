import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:image_picker/image_picker.dart';
import '../models/download_item.dart';
import '../services/download_service.dart';
import '../services/player_service.dart';
import '../theme/app_theme.dart';
import '../utils/common_utils.dart';
import '../widgets/shimmer_loading_list.dart';
import 'package:ytdown/providers/library_provider.dart';
import '../providers/library_playlists_provider.dart';
import '../providers/library_playlist_selection_provider.dart';
import 'playlist_detail_screen.dart';

class LibraryScreen extends ConsumerStatefulWidget {
  const LibraryScreen({super.key});

  @override
  ConsumerState<LibraryScreen> createState() => _LibraryScreenState();
}

class _LibraryScreenState extends ConsumerState<LibraryScreen>
    with AutomaticKeepAliveClientMixin {
  static const String _unknownArtistLabel = 'Desconhecido';
  static const String _unknownAlbumLabel = 'Sem álbum';

  final _searchController = TextEditingController();
  final _imagePicker = ImagePicker();
  _LibraryBatchEditState? _batchEditState;
  String _batchEditNameDraft = '';
  String _batchEditImageSourceDraft = '';
  String _batchEditCurrentImageSource = '';
  bool _batchEditHasCustomImage = false;
  bool _batchEditSubmitting = false;
  bool _suppressNextArtistTap = false;
  bool _suppressNextAlbumTap = false;

  @override
  bool get wantKeepAlive => true; // Mantém o estado entre trocas de aba

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  String? _trimToNull(String? value) {
    final trimmed = value?.trim();
    if (trimmed == null || trimmed.isEmpty) {
      return null;
    }
    return trimmed;
  }

  String _batchEntityLabel(_LibraryBatchEditType type) {
    return type == _LibraryBatchEditType.artist ? 'artista' : 'album';
  }

  String _capitalize(String value) {
    if (value.isEmpty) {
      return value;
    }
    return value[0].toUpperCase() + value.substring(1);
  }

  String _trackSubtitle(DownloadItem item) {
    final artist = item.artist ?? _unknownArtistLabel;
    final album = item.album ?? _unknownAlbumLabel;
    return '$artist • $album';
  }

  void _clearSearchInput() {
    _searchController.clear();
    _onSearchChanged('');
  }

  Future<void> _openPlaylistDetails(Map<String, dynamic> playlist) async {
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => PlaylistDetailScreen(playlist: playlist),
      ),
    );
    ref.invalidate(libraryPlaylistsProvider);
  }

  void _onSearchChanged(String value) {
    ref.read(libraryProvider.notifier).setSearchQuery(value);
  }

  Future<void> _refreshData() async {
    await ref.read(libraryProvider.notifier).refresh();
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);

    final libraryAsync = ref.watch(libraryProvider);

    return DefaultTabController(
      length: 4,
      child: Scaffold(
        backgroundColor: AppTheme.surface,
        body: SafeArea(
          child: Stack(
            children: [
              NestedScrollView(
                headerSliverBuilder: (context, innerBoxIsScrolled) {
                  return [
                    SliverAppBar(
                      backgroundColor: AppTheme.surface,
                      elevation: innerBoxIsScrolled ? 4 : 0,
                      pinned: true,
                      floating: true,
                      title: TextField(
                        controller: _searchController,
                        style: const TextStyle(color: AppTheme.textPrimary),
                        decoration: InputDecoration(
                          hintText: 'Buscar na biblioteca...',
                          hintStyle: const TextStyle(
                              color: AppTheme.textSecondary, fontSize: 16),
                          border: InputBorder.none,
                          suffixIcon: _searchController.text.isNotEmpty
                              ? IconButton(
                                  icon: const Icon(Icons.close,
                                      color: AppTheme.textSecondary),
                                  onPressed: _clearSearchInput,
                                )
                              : const Icon(Icons.search,
                                  color: AppTheme.textSecondary),
                        ),
                        onChanged: _onSearchChanged,
                      ),
                      bottom: const TabBar(
                        isScrollable: true,
                        indicatorColor: AppTheme.primary,
                        labelColor: AppTheme.primary,
                        unselectedLabelColor: AppTheme.textSecondary,
                        tabs: [
                          Tab(text: 'Artistas'),
                          Tab(text: 'Álbuns'),
                          Tab(text: 'Músicas'),
                          Tab(text: 'Playlists'),
                        ],
                      ),
                    ),
                  ];
                },
                body: libraryAsync.when(
                  data: (data) => _buildBody(data),
                  loading: () => const ShimmerLoadingList(),
                  error: (err, stack) => Center(child: Text('Erro: $err')),
                ),
              ),
              if (_batchEditState != null)
                _buildBatchEditOverlay(_batchEditState!),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildBody(LibraryState state) {
    final filteredDownloads = state.audios;

    return RefreshIndicator(
      onRefresh: _refreshData,
      color: AppTheme.primary,
      child: TabBarView(
        children: [
          _buildArtistsTab(state),
          _buildAlbumsTab(state),
          _buildTracksTab(filteredDownloads),
          _buildPlaylistsTab(),
        ],
      ),
    );
  }

  Widget _buildTabEmptyState({required String message}) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.library_music_rounded,
            size: 80,
            color: AppTheme.primary.withValues(alpha: 0.2),
          )
              .animate(onPlay: (controller) => controller.repeat(reverse: true))
              .scaleXY(begin: 0.9, end: 1.1, duration: 2.seconds)
              .fadeIn(),
          const SizedBox(height: 24),
          const Text(
            'Sua biblioteca está vazia',
            style: TextStyle(
                color: AppTheme.textPrimary,
                fontSize: 18,
                fontWeight: FontWeight.bold),
          )
              .animate()
              .fadeIn(delay: const Duration(milliseconds: 200))
              .slideY(begin: 0.2, end: 0),
          const SizedBox(height: 8),
          Text(
            message,
            style: const TextStyle(color: AppTheme.textSecondary, fontSize: 14),
          ).animate().fadeIn(delay: const Duration(milliseconds: 400)),
        ],
      ),
    );
  }

  Widget _buildArtistsTab(LibraryState state) {
    final artists = state.artists;

    if (artists.isEmpty) {
      return _buildTabEmptyState(
        message: state.isScanning
            ? 'Escaneando arquivos de áudio...'
            : 'Nenhum artista encontrado ainda',
      );
    }

    return GridView.builder(
      padding: const EdgeInsets.all(16),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        childAspectRatio: 0.8,
        crossAxisSpacing: 16,
        mainAxisSpacing: 16,
      ),
      itemCount: artists.length,
      itemBuilder: (context, index) {
        final artist = artists[index];
        final name = artist['name'] as String;
        final trackCount = artist['trackCount'] as int;
        final imageUrl = artist['imageUrl'] as String?;

        return _buildGridItem(
          title: name,
          subtitle: '$trackCount músicas',
          imageUrl: imageUrl,
          isCircle: true,
          onTap: () => _handleArtistTap(
            artistName: name,
            totalTracks: trackCount,
          ),
          helperText: 'Segure para editar',
          onLongPress: () => _handleArtistLongPress(
            artistName: name,
            totalTracks: trackCount,
            currentImageUrl: imageUrl,
          ),
        );
      },
    );
  }

  Widget _buildAlbumsTab(LibraryState state) {
    final albums = state.albums;

    if (albums.isEmpty) {
      return _buildTabEmptyState(
        message: state.isScanning
            ? 'Escaneando arquivos de áudio...'
            : 'Nenhum álbum encontrado ainda',
      );
    }

    return GridView.builder(
      padding: const EdgeInsets.all(16),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        childAspectRatio: 0.8,
        crossAxisSpacing: 16,
        mainAxisSpacing: 16,
      ),
      itemCount: albums.length,
      itemBuilder: (context, index) {
        final album = albums[index];
        final name = album['name'] as String;
        final subtitle = album['subtitle'] as String? ?? 'Artista Desconhecido';
        final trackCount = album['trackCount'] as int;
        final imageUrl = album['imageUrl'] as String?;

        return _buildGridItem(
          title: name,
          subtitle: subtitle,
          imageUrl: imageUrl,
          isCircle: false,
          onTap: () => _handleAlbumTap(
            albumName: name,
            totalTracks: trackCount,
          ),
          helperText: 'Segure para editar',
          onLongPress: () => _handleAlbumLongPress(
            albumName: name,
            totalTracks: trackCount,
            currentImageUrl: imageUrl,
          ),
        );
      },
    );
  }

  Widget _buildTracksTab(List<DownloadItem> tracks) {
    final audioTracks =
        tracks.where((i) => i.type == DownloadType.audio).toList();

    if (audioTracks.isEmpty) {
      return _buildTabEmptyState(
        message: 'Baixe músicas do YouTube para começar',
      );
    }

    return ListView.builder(
      itemCount: audioTracks.length,
      itemBuilder: (context, index) {
        final item = audioTracks[index];
        return ListTile(
          leading: _buildImage(item.albumImageUrl ?? item.thumbnail,
              size: 50, isCircle: false),
          title: Text(item.title,
              style: const TextStyle(color: AppTheme.textPrimary, fontSize: 14),
              maxLines: 1,
              overflow: TextOverflow.ellipsis),
          subtitle: Text(_trackSubtitle(item),
              style:
                  const TextStyle(color: AppTheme.textSecondary, fontSize: 12)),
          onTap: () => PlayerService.instance.playTrack(item),
        );
      },
    );
  }

  Widget _buildPlaylistsTab() {
    final playlistsAsync = ref.watch(libraryPlaylistsProvider);

    return playlistsAsync.when(
      loading: () => const Center(
        child: CircularProgressIndicator(color: AppTheme.primary),
      ),
      error: (err, stack) => Center(
        child: Text('Erro ao carregar playlists: $err'),
      ),
      data: (playlists) {
        if (playlists.isEmpty) {
          return Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Text('Nenhuma playlist criada',
                  style: TextStyle(color: AppTheme.textSecondary)),
              const SizedBox(height: 16),
              ElevatedButton.icon(
                icon: const Icon(Icons.add_rounded),
                label: const Text('Criar Playlist'),
                onPressed: _showCreatePlaylistDialog,
              ),
            ],
          );
        }

        return Stack(
          children: [
            ListView.builder(
              itemCount: playlists.length,
              itemBuilder: (context, index) {
                final p = playlists[index];
                return ListTile(
                  leading:
                      _buildImage(p['thumbnail'], size: 50, isCircle: false),
                  title: Text(p['name'] ?? 'Playlist sem nome',
                      style: const TextStyle(color: AppTheme.textPrimary)),
                  subtitle: Text('${p['trackCount']} músicas',
                      style: const TextStyle(color: AppTheme.textSecondary)),
                  onTap: () => _openPlaylistDetails(p),
                );
              },
            ),
            Positioned(
              right: 16,
              bottom: 16,
              child: FloatingActionButton(
                mini: true,
                backgroundColor: AppTheme.primary,
                onPressed: _showCreatePlaylistDialog,
                child: const Icon(Icons.add_rounded, color: Colors.white),
              ),
            ),
          ],
        );
      },
    );
  }

  Future<void> _showCreatePlaylistDialog() async {
    final controller = TextEditingController();
    final name = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppTheme.card,
        title:
            const Text('Nova Playlist', style: TextStyle(color: Colors.white)),
        content: TextField(
          controller: controller,
          autofocus: true,
          style: const TextStyle(color: Colors.white),
          decoration: const InputDecoration(
              hintText: 'Nome da playlist',
              hintStyle: TextStyle(color: AppTheme.textSecondary)),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Cancelar')),
          TextButton(
              onPressed: () => Navigator.pop(context, controller.text),
              child: const Text('Criar')),
        ],
      ),
    );

    if (name != null && name.trim().isNotEmpty) {
      await ref.read(libraryPlaylistsProvider.notifier).createPlaylist(
            name.trim(),
          );
      ref.invalidate(libraryPlaylistsProvider);
    }
  }

  Widget _buildGridItem({
    required String title,
    required String subtitle,
    String? imageUrl,
    required bool isCircle,
    required VoidCallback onTap,
    String? helperText,
    VoidCallback? onLongPress,
  }) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: onTap,
      onLongPress: onLongPress,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Expanded(
            child: _buildImage(imageUrl,
                size: double.infinity, isCircle: isCircle),
          ),
          const SizedBox(height: 8),
          Text(
            title,
            style: const TextStyle(
                color: AppTheme.textPrimary,
                fontWeight: FontWeight.bold,
                fontSize: 14),
            textAlign: TextAlign.center,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          Text(
            subtitle,
            style: const TextStyle(color: AppTheme.textSecondary, fontSize: 12),
            textAlign: TextAlign.center,
          ),
          if (helperText != null)
            Text(
              helperText,
              style: const TextStyle(color: AppTheme.primary, fontSize: 11),
              textAlign: TextAlign.center,
            ),
        ],
      ),
    );
  }

  Future<void> _handleArtistLongPress({
    required String artistName,
    required int totalTracks,
    required String? currentImageUrl,
  }) async {
    _suppressNextArtistTap = true;
    if (_batchEditState != null) return;

    unawaited(HapticFeedback.mediumImpact());
    _openBatchEditOverlay(
      type: _LibraryBatchEditType.artist,
      currentName: artistName,
      totalTracks: totalTracks,
      currentImageUrl: currentImageUrl,
    );
  }

  void _handleArtistTap({
    required String artistName,
    required int totalTracks,
  }) {
    if (_suppressNextArtistTap) {
      _suppressNextArtistTap = false;
      return;
    }

    if (_batchEditState != null) return;

    _showGroupDetails(artistName, totalTracks, isAlbum: false);
  }

  Future<void> _handleAlbumLongPress({
    required String albumName,
    required int totalTracks,
    required String? currentImageUrl,
  }) async {
    _suppressNextAlbumTap = true;
    if (_batchEditState != null) return;

    unawaited(HapticFeedback.mediumImpact());
    _openBatchEditOverlay(
      type: _LibraryBatchEditType.album,
      currentName: albumName,
      totalTracks: totalTracks,
      currentImageUrl: currentImageUrl,
    );
  }

  void _handleAlbumTap({
    required String albumName,
    required int totalTracks,
  }) {
    if (_suppressNextAlbumTap) {
      _suppressNextAlbumTap = false;
      return;
    }

    if (_batchEditState != null) return;

    _showGroupDetails(albumName, totalTracks, isAlbum: true);
  }

  Widget _buildImage(String? url, {double size = 48, bool isCircle = false}) {
    final normalized = CommonUtils.normalizeText(url);
    final uri = Uri.tryParse(normalized);
    final isRemote = CommonUtils.isRemoteHttpUri(uri);
    final localPath = _resolveLocalImagePath(normalized, uri);

    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        shape: isCircle ? BoxShape.circle : BoxShape.rectangle,
        borderRadius: isCircle ? null : BorderRadius.circular(12),
        color: AppTheme.card,
      ),
      clipBehavior: Clip.antiAlias,
      child: isRemote
          ? CachedNetworkImage(
              imageUrl: normalized,
              fit: BoxFit.cover,
              memCacheWidth: 250,
              memCacheHeight: 250,
              placeholder: (context, url) => const Icon(
                  Icons.music_note_rounded,
                  color: AppTheme.textSecondary),
              errorWidget: (context, url, error) => const Icon(
                  Icons.music_note_rounded,
                  color: AppTheme.textSecondary),
            )
          : (localPath != null && File(localPath).existsSync())
              ? Image.file(
                  File(localPath),
                  fit: BoxFit.cover,
                  errorBuilder: (context, error, stackTrace) => const Icon(
                    Icons.music_note_rounded,
                    color: AppTheme.textSecondary,
                  ),
                )
              : const Icon(Icons.music_note_rounded,
                  color: AppTheme.textSecondary),
    );
  }

  String? _resolveLocalImagePath(String normalized, Uri? uri) {
    if (normalized.isEmpty) return null;

    if (uri != null && uri.scheme.toLowerCase() == 'file') {
      try {
        return uri.toFilePath();
      } catch (_) {
        return null;
      }
    }

    if (normalized.startsWith('/')) return normalized;
    return null;
  }

  void _showGroupDetails(String title, int totalTracks,
      {required bool isAlbum}) {
    ref.read(libraryPlaylistSelectionProvider.notifier).clear();

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => Consumer(
        builder: (context, ref, _) {
          final selection = ref.watch(libraryPlaylistSelectionProvider);
          final selectionNotifier =
              ref.read(libraryPlaylistSelectionProvider.notifier);

          final future = isAlbum
              ? ref.read(libraryServiceProvider).getLibraryByAlbum(title)
              : ref.read(libraryServiceProvider).getLibraryByArtist(title);

          return Container(
            height: MediaQuery.of(context).size.height * 0.75,
            decoration: const BoxDecoration(
              color: AppTheme.surface,
              borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
            ),
            child: FutureBuilder<List<DownloadItem>>(
              future: future,
              builder: (context, snapshot) {
                final isLoading =
                    snapshot.connectionState == ConnectionState.waiting;
                final items = snapshot.data ?? [];

                return Column(
                  children: [
                    const SizedBox(height: 12),
                    Container(
                        width: 40,
                        height: 4,
                        decoration: BoxDecoration(
                            color: AppTheme.border,
                            borderRadius: BorderRadius.circular(2))),
                    const SizedBox(height: 20),
                    Text(title,
                        style: const TextStyle(
                            color: AppTheme.textPrimary,
                            fontSize: 20,
                            fontWeight: FontWeight.bold)),
                    const SizedBox(height: 8),
                    Text('$totalTracks músicas',
                        style: const TextStyle(
                            color: AppTheme.textSecondary, fontSize: 14)),
                    if (selection.isSelectionMode) ...[
                      const SizedBox(height: 12),
                      Container(
                        margin: const EdgeInsets.symmetric(horizontal: 16),
                        padding: const EdgeInsets.all(10),
                        decoration: BoxDecoration(
                          color: AppTheme.card,
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: AppTheme.border),
                        ),
                        child: Column(
                          children: [
                            Row(
                              children: [
                                Expanded(
                                  child: Text(
                                    '${selection.selectedCount} música(s) selecionada(s)',
                                    style: const TextStyle(
                                      color: AppTheme.textPrimary,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                ),
                                TextButton(
                                  onPressed: isLoading
                                      ? null
                                      : () =>
                                          selectionNotifier.selectAll(items),
                                  child: const Text('Selecionar todas'),
                                ),
                                TextButton(
                                  onPressed: selectionNotifier.clear,
                                  child: const Text('Limpar'),
                                ),
                              ],
                            ),
                            const SizedBox(height: 8),
                            SizedBox(
                              width: double.infinity,
                              child: ElevatedButton.icon(
                                onPressed:
                                    selection.selectedCount == 0 || isLoading
                                        ? null
                                        : () => _showPlaylistPickerForSelection(
                                              items,
                                              context,
                                            ),
                                icon: const Icon(Icons.playlist_add_rounded),
                                label: const Text('Escolher playlist'),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                    const SizedBox(height: 16),
                    isLoading
                        ? const Expanded(
                            child: Center(child: CircularProgressIndicator()))
                        : Expanded(
                            child: ListView.builder(
                              itemCount: items.length,
                              itemBuilder: (context, index) {
                                final track = items[index];
                                final isSelected = selection.selectedTrackIds
                                    .contains(track.id);

                                return ListTile(
                                  leading: _buildImage(
                                      track.albumImageUrl ?? track.thumbnail,
                                      size: 50,
                                      isCircle: false),
                                  title: Text(track.title,
                                      style: const TextStyle(
                                          color: AppTheme.textPrimary,
                                          fontSize: 14),
                                      maxLines: 1),
                                  trailing: selection.isSelectionMode
                                      ? Icon(
                                          isSelected
                                              ? Icons.check_circle_rounded
                                              : Icons
                                                  .radio_button_unchecked_rounded,
                                          color: isSelected
                                              ? AppTheme.primary
                                              : AppTheme.textSecondary,
                                        )
                                      : null,
                                  onLongPress: () {
                                    selectionNotifier.selectOnly(track.id);
                                  },
                                  onTap: () {
                                    if (selection.isSelectionMode) {
                                      selectionNotifier.toggleTrack(track.id);
                                      return;
                                    }

                                    Navigator.pop(context);
                                    PlayerService.instance.playPlaylist(
                                      items,
                                      initialIndex: index,
                                    );
                                  },
                                );
                              },
                            ),
                          ),
                  ],
                );
              },
            ),
          );
        },
      ),
    ).whenComplete(() {
      ref.read(libraryPlaylistSelectionProvider.notifier).clear();
    });
  }

  Future<void> _showPlaylistPickerForSelection(
    List<DownloadItem> groupTracks,
    BuildContext groupSheetContext,
  ) async {
    final selection = ref.read(libraryPlaylistSelectionProvider);
    final selectedTrackIds = groupTracks
        .where((track) => selection.selectedTrackIds.contains(track.id))
        .map((track) => track.id)
        .toList();

    if (selectedTrackIds.isEmpty) return;

    await showModalBottomSheet(
      context: context,
      backgroundColor: AppTheme.surface,
      isScrollControlled: true,
      builder: (context) => Consumer(
        builder: (context, ref, _) {
          final playlistsAsync = ref.watch(libraryPlaylistsProvider);

          return SafeArea(
            child: Container(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
              height: MediaQuery.of(context).size.height * 0.6,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Escolher playlist',
                    style: TextStyle(
                      color: AppTheme.textPrimary,
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    '${selectedTrackIds.length} música(s) selecionada(s)',
                    style: const TextStyle(
                        color: AppTheme.textSecondary, fontSize: 13),
                  ),
                  const SizedBox(height: 12),
                  Expanded(
                    child: playlistsAsync.when(
                      loading: () => const Center(
                        child:
                            CircularProgressIndicator(color: AppTheme.primary),
                      ),
                      error: (err, stack) => Center(
                        child: Text('Erro ao carregar playlists: $err'),
                      ),
                      data: (playlists) {
                        if (playlists.isEmpty) {
                          return Center(
                            child: Column(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                const Text(
                                  'Nenhuma playlist criada ainda',
                                  style:
                                      TextStyle(color: AppTheme.textSecondary),
                                ),
                                const SizedBox(height: 12),
                                ElevatedButton.icon(
                                  onPressed: () async {
                                    Navigator.pop(context);
                                    await _showCreatePlaylistDialog();
                                  },
                                  icon: const Icon(Icons.add_rounded),
                                  label: const Text('Criar Playlist'),
                                ),
                              ],
                            ),
                          );
                        }

                        return ListView.builder(
                          itemCount: playlists.length,
                          itemBuilder: (context, index) {
                            final playlist = playlists[index];
                            return ListTile(
                              leading: _buildImage(
                                  playlist['thumbnail'] as String?,
                                  size: 46,
                                  isCircle: false),
                              title: Text(
                                playlist['name']?.toString() ??
                                    'Playlist sem nome',
                                style: const TextStyle(
                                    color: AppTheme.textPrimary),
                              ),
                              subtitle: Text(
                                '${playlist['trackCount']} músicas',
                                style: const TextStyle(
                                    color: AppTheme.textSecondary),
                              ),
                              onTap: () async {
                                await ref
                                    .read(libraryPlaylistsProvider.notifier)
                                    .addTracksToPlaylist(
                                      playlistId: playlist['id'].toString(),
                                      trackIds: selectedTrackIds,
                                    );

                                if (!mounted || !context.mounted) return;
                                Navigator.pop(context);
                                await Future<void>.delayed(
                                  const Duration(milliseconds: 150),
                                );
                                if (!mounted || !groupSheetContext.mounted) {
                                  return;
                                }
                                final groupNavigator =
                                    Navigator.of(groupSheetContext);
                                if (groupNavigator.canPop()) {
                                  groupNavigator.pop();
                                }
                                ref
                                    .read(libraryPlaylistSelectionProvider
                                        .notifier)
                                    .clear();

                                ScaffoldMessenger.of(this.context).showSnackBar(
                                  SnackBar(
                                    content: Text(
                                      '${selectedTrackIds.length} música(s) adicionada(s) em "${playlist['name']}"',
                                    ),
                                    backgroundColor: AppTheme.success,
                                  ),
                                );
                              },
                            );
                          },
                        );
                      },
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  void _openBatchEditOverlay({
    required _LibraryBatchEditType type,
    required String currentName,
    required int totalTracks,
    required String? currentImageUrl,
  }) {
    if (!mounted || _batchEditState != null) return;

    _batchEditNameDraft = currentName;
    _batchEditCurrentImageSource = currentImageUrl?.trim() ?? '';
    _batchEditImageSourceDraft = '';
    _batchEditHasCustomImage = false;
    FocusManager.instance.primaryFocus?.unfocus();
    setState(() {
      _batchEditSubmitting = false;
      _batchEditState = _LibraryBatchEditState(
        type: type,
        currentName: currentName,
        totalTracks: totalTracks,
      );
    });
  }

  Future<void> _pickBatchImageFromGallery() async {
    if (!mounted || _batchEditState == null || _batchEditSubmitting) return;

    try {
      final picked = await _imagePicker.pickImage(
        source: ImageSource.gallery,
        imageQuality: 95,
      );

      if (picked == null || !mounted) return;

      setState(() {
        _batchEditImageSourceDraft = picked.path;
        _batchEditHasCustomImage = true;
      });
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Falha ao selecionar imagem: $e'),
          backgroundColor: AppTheme.error,
        ),
      );
    }
  }

  void _closeBatchEditOverlay() {
    if (!mounted || _batchEditState == null) return;
    FocusManager.instance.primaryFocus?.unfocus();
    setState(() {
      _batchEditState = null;
      _batchEditSubmitting = false;
      _batchEditImageSourceDraft = '';
      _batchEditCurrentImageSource = '';
      _batchEditHasCustomImage = false;
    });
  }

  Future<void> _submitBatchEdit(_LibraryBatchEditState state) async {
    if (_batchEditSubmitting || !mounted) return;

    final normalizedNewName = _batchEditNameDraft.trim();
    final normalizedImageSource =
        _batchEditHasCustomImage ? _batchEditImageSourceDraft.trim() : '';
    final entityLabel = _batchEntityLabel(state.type);

    if (normalizedNewName.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Informe um novo nome de $entityLabel'),
          backgroundColor: AppTheme.error,
        ),
      );
      return;
    }

    setState(() {
      _batchEditSubmitting = true;
    });

    final result = await _performBatchUpdate(
      state,
      normalizedNewName,
      normalizedImageSource,
    );

    if (!mounted) return;

    if (result['success'] == true) {
      final updatedCount = (result['updatedCount'] as num?)?.toInt() ?? 0;
      final failedCount = (result['failedCount'] as num?)?.toInt() ?? 0;
      final suffix = failedCount > 0 ? ' ($failedCount falha(s))' : '';

      _closeBatchEditOverlay();

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            '${_capitalize(entityLabel)} atualizado em $updatedCount faixa(s)$suffix',
          ),
          backgroundColor: AppTheme.success,
        ),
      );

      await ref.read(libraryProvider.notifier).refresh();
      return;
    }

    setState(() {
      _batchEditSubmitting = false;
    });

    final errorMessage = result['error']?.toString() ??
        'Falha ao atualizar $entityLabel em lote.';
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(errorMessage),
        backgroundColor: AppTheme.error,
      ),
    );
  }

  Future<Map<String, dynamic>> _performBatchUpdate(
    _LibraryBatchEditState state,
    String normalizedNewName,
    String normalizedImageSource,
  ) async {
    try {
      if (state.type == _LibraryBatchEditType.artist) {
        final result =
            await DownloadService.instance.rewriteArtistMetadataBatch(
          currentArtist: state.currentName,
          newArtist: normalizedNewName,
          newArtistImageUrl:
              normalizedImageSource.isNotEmpty ? normalizedImageSource : null,
        );

        if (result['success'] == true) {
          final appliedArtistImageUrl =
              _trimToNull(result['appliedArtistImageUrl']?.toString());
          PlayerService.instance.applyArtistBatchMetadataUpdate(
            oldArtist: state.currentName,
            newArtist: normalizedNewName,
            newArtistImageUrl: appliedArtistImageUrl,
          );
        }

        return result;
      }

      final result = await DownloadService.instance.rewriteAlbumMetadataBatch(
        currentAlbum: state.currentName,
        newAlbum: normalizedNewName,
        newAlbumImageUrl:
            normalizedImageSource.isNotEmpty ? normalizedImageSource : null,
      );

      if (result['success'] == true) {
        final appliedAlbumImageUrl =
            _trimToNull(result['appliedAlbumImageUrl']?.toString());
        PlayerService.instance.applyAlbumBatchMetadataUpdate(
          oldAlbum: state.currentName,
          newAlbum: normalizedNewName,
          newAlbumImageUrl: appliedAlbumImageUrl,
        );
      }

      return result;
    } catch (e) {
      return <String, dynamic>{
        'success': false,
        'error': e.toString(),
      };
    }
  }

  Widget _buildBatchEditOverlay(_LibraryBatchEditState state) {
    final isArtist = state.type == _LibraryBatchEditType.artist;
    final title = isArtist ? 'Editar artista em lote' : 'Editar album em lote';
    final currentLabel = isArtist ? 'Artista atual' : 'Album atual';
    final nameLabel = isArtist ? 'Novo nome do artista' : 'Novo nome do album';
    final imageLabel = isArtist ? 'Foto da banda/artista' : 'Capa do album';
    final selectedImageSource = _batchEditHasCustomImage
        ? _batchEditImageSourceDraft
        : _batchEditCurrentImageSource;
    final hasSelectedImage = selectedImageSource.trim().isNotEmpty;
    final imageStateLabel = _batchEditHasCustomImage
        ? 'Nova imagem selecionada da galeria'
        : hasSelectedImage
            ? 'Imagem atual mantida'
            : 'Nenhuma imagem definida';

    return Positioned.fill(
      child: Material(
        color: Colors.black.withValues(alpha: 0.62),
        child: SafeArea(
          child: Center(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 420),
                child: Container(
                  padding: const EdgeInsets.fromLTRB(20, 18, 20, 16),
                  decoration: BoxDecoration(
                    color: AppTheme.card,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: AppTheme.border),
                  ),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        title,
                        style: TextStyle(
                          color: AppTheme.textPrimary,
                          fontSize: 18,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 10),
                      Text(
                        '$currentLabel: ${state.currentName}',
                        style: const TextStyle(color: AppTheme.textSecondary),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'Faixas encontradas: ${state.totalTracks}',
                        style: const TextStyle(color: AppTheme.textSecondary),
                      ),
                      const SizedBox(height: 14),
                      TextFormField(
                        // Removida a ValueKey dinamica que causava perda de foco
                        initialValue: _batchEditNameDraft,
                        enabled: !_batchEditSubmitting,
                        style: const TextStyle(color: AppTheme.textPrimary),
                        onChanged: (value) {
                          _batchEditNameDraft = value;
                        },
                        decoration: InputDecoration(
                          labelText: nameLabel,
                          labelStyle:
                              const TextStyle(color: AppTheme.textSecondary),
                        ),
                      ),
                      const SizedBox(height: 12),
                      Row(
                        children: [
                          Icon(
                            isArtist
                                ? Icons.person_rounded
                                : Icons.album_rounded,
                            size: 18,
                            color: AppTheme.textSecondary,
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              '$imageLabel: $imageStateLabel',
                              style: const TextStyle(
                                color: AppTheme.textSecondary,
                              ),
                            ),
                          ),
                        ],
                      ),
                      if (hasSelectedImage) ...[
                        const SizedBox(height: 10),
                        Center(
                          child: _buildImage(
                            selectedImageSource,
                            size: 80,
                            isCircle: isArtist,
                          ),
                        ),
                      ],
                      const SizedBox(height: 10),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          OutlinedButton.icon(
                            onPressed: _batchEditSubmitting
                                ? null
                                : _pickBatchImageFromGallery,
                            icon: const Icon(Icons.photo_library_rounded),
                            label: Text(
                              _batchEditHasCustomImage
                                  ? 'Trocar imagem'
                                  : 'Selecionar da galeria',
                            ),
                          ),
                          if (_batchEditHasCustomImage)
                            TextButton.icon(
                              onPressed: _batchEditSubmitting
                                  ? null
                                  : () {
                                      setState(() {
                                        _batchEditImageSourceDraft = '';
                                        _batchEditHasCustomImage = false;
                                      });
                                    },
                              icon: const Icon(Icons.undo_rounded),
                              label: const Text('Manter imagem atual'),
                            ),
                        ],
                      ),
                      const SizedBox(height: 16),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.end,
                        children: [
                          TextButton(
                            onPressed: _batchEditSubmitting
                                ? null
                                : _closeBatchEditOverlay,
                            child: const Text('Cancelar'),
                          ),
                          const SizedBox(width: 8),
                          FilledButton(
                            onPressed: _batchEditSubmitting
                                ? null
                                : () => _submitBatchEdit(state),
                            child: _batchEditSubmitting
                                ? const SizedBox(
                                    width: 16,
                                    height: 16,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                      color: Colors.white,
                                    ),
                                  )
                                : const Text('Aplicar'),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

enum _LibraryBatchEditType { artist, album }

class _LibraryBatchEditState {
  const _LibraryBatchEditState({
    required this.type,
    required this.currentName,
    required this.totalTracks,
  });

  final _LibraryBatchEditType type;
  final String currentName;
  final int totalTracks;
}
