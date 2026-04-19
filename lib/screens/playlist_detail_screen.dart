import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../models/download_item.dart';
import '../services/database_service.dart';
import '../services/player_service.dart';
import '../providers/playlist_detail_provider.dart';
import '../theme/app_theme.dart';
import '../widgets/shimmer_loading_list.dart';

class PlaylistDetailScreen extends ConsumerWidget {
  final Map<String, dynamic> playlist;
  const PlaylistDetailScreen({super.key, required this.playlist});

  String get _playlistId => playlist['id']?.toString() ?? '';

  String get _playlistName => playlist['name']?.toString() ?? 'Playlist';

  String? get _playlistThumbnail => playlist['thumbnail']?.toString();

  void _refreshTracks(WidgetRef ref) {
    ref.invalidate(playlistTracksProvider(_playlistId));
  }

  List<DownloadItem> _audioDownloads(List<DownloadItem> downloads) {
    return downloads
        .where((download) => download.type == DownloadType.audio)
        .toList();
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      backgroundColor: AppTheme.surface,
      body: CustomScrollView(
        slivers: [
          _buildAppBar(ref),
          _buildTrackList(ref),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        backgroundColor: AppTheme.primary,
        child: const Icon(Icons.add_rounded, color: Colors.white),
        onPressed: () => _showAddSongsPanel(context, ref),
      ),
    );
  }

  Widget _buildAppBar(WidgetRef ref) {
    return SliverAppBar(
      expandedHeight: 300,
      pinned: true,
      backgroundColor: AppTheme.card,
      flexibleSpace: FlexibleSpaceBar(
        title: Text(_playlistName),
        background: Stack(
          fit: StackFit.expand,
          children: [
            if (_playlistThumbnail != null && _playlistThumbnail!.isNotEmpty)
              CachedNetworkImage(
                imageUrl: _playlistThumbnail!,
                fit: BoxFit.cover,
              ),
            if (_playlistThumbnail == null || _playlistThumbnail!.isEmpty)
              Container(
                color: AppTheme.card,
                child: const Icon(
                  Icons.playlist_play_rounded,
                  size: 80,
                  color: AppTheme.textSecondary,
                ),
              ),
            Container(
                decoration: const BoxDecoration(
                    gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: [Colors.transparent, Colors.black87]))),
          ],
        ),
      ),
      actions: [
        IconButton(
          icon: const Icon(Icons.delete_outline_rounded, color: AppTheme.error),
          onPressed: () {
            // Future feature: Delete playlist
          },
        ),
      ],
    );
  }

  Widget _buildTrackList(WidgetRef ref) {
    final tracksAsync = ref.watch(playlistTracksProvider(_playlistId));

    return tracksAsync.when(
      data: (tracks) {
        if (tracks.isEmpty) {
          return const SliverFillRemaining(
            child: Center(
                child: Text('Nenhuma música nesta playlist',
                    style: TextStyle(color: AppTheme.textSecondary))),
          );
        }
        return SliverList(
          delegate: SliverChildBuilderDelegate(
            (context, index) {
              final track = tracks[index];
              return ListTile(
                leading: ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: CachedNetworkImage(
                    imageUrl: track.albumImageUrl ?? track.thumbnail ?? '',
                    width: 50,
                    height: 50,
                    fit: BoxFit.cover,
                  ),
                ),
                title: Text(track.title,
                    style: const TextStyle(
                        color: AppTheme.textPrimary, fontSize: 14),
                    maxLines: 1),
                subtitle: Text(track.artist ?? 'Desconhecido',
                    style: const TextStyle(
                        color: AppTheme.textSecondary, fontSize: 12)),
                trailing: IconButton(
                  icon: const Icon(Icons.remove_circle_outline_rounded,
                      color: AppTheme.textSecondary, size: 20),
                  onPressed: () async {
                    await DatabaseService.instance
                        .removeTrackFromPlaylist(_playlistId, track.id);
                    _refreshTracks(ref);
                  },
                ),
                onTap: () {
                  PlayerService.instance.playTrack(track);
                },
              );
            },
            childCount: tracks.length,
          ),
        );
      },
      loading: () => const ShimmerLoadingList(isSliver: true),
      error: (err, stack) => SliverFillRemaining(
        child: Center(
          child:
              Text('Erro: $err', style: const TextStyle(color: AppTheme.error)),
        ),
      ),
    );
  }

  void _showAddSongsPanel(BuildContext context, WidgetRef ref) async {
    final downloads = await DatabaseService.instance.getAllDownloads();
    final audioDownloads = _audioDownloads(downloads);

    if (!context.mounted) return;

    showModalBottomSheet(
      context: context,
      backgroundColor: AppTheme.surface,
      isScrollControlled: true,
      builder: (context) {
        return Container(
          height: MediaQuery.of(context).size.height * 0.7,
          padding: const EdgeInsets.all(16),
          child: Column(
            children: [
              const Text('Adicionar Música',
                  style: TextStyle(
                      color: AppTheme.textPrimary,
                      fontSize: 18,
                      fontWeight: FontWeight.bold)),
              const SizedBox(height: 16),
              Expanded(
                child: ListView.builder(
                  itemCount: audioDownloads.length,
                  itemBuilder: (context, index) {
                    final item = audioDownloads[index];

                    return ListTile(
                      leading: CachedNetworkImage(
                        imageUrl: item.albumImageUrl ?? item.thumbnail ?? '',
                        width: 40,
                        height: 40,
                        errorWidget: (_, __, ___) =>
                            const Icon(Icons.music_note_rounded),
                      ),
                      title: Text(item.title,
                          style: const TextStyle(color: AppTheme.textPrimary)),
                      onTap: () async {
                        await DatabaseService.instance
                            .addTrackToPlaylist(_playlistId, item.id);
                        if (context.mounted) Navigator.pop(context);
                        _refreshTracks(ref);
                      },
                    );
                  },
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}
