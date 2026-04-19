import 'dart:async';
import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:just_audio/just_audio.dart';
import '../models/download_item.dart';
import '../providers/player_provider.dart';
import '../theme/app_theme.dart';

class MusicPlayerScreen extends ConsumerStatefulWidget {
  const MusicPlayerScreen({super.key});

  @override
  ConsumerState<MusicPlayerScreen> createState() => _MusicPlayerScreenState();
}

class _MusicPlayerScreenState extends ConsumerState<MusicPlayerScreen> {
  static const String _unknownArtistLabel = 'Desconhecido';
  static const String _unknownAlbumLabel = 'Sem álbum';

  bool _showArtistImage = false;
  Timer? _toggleTimer;

  void _startArtworkToggleTimer() {
    _toggleTimer?.cancel();
    _toggleTimer = Timer.periodic(const Duration(seconds: 10), (timer) {
      if (!mounted) {
        return;
      }
      setState(() => _showArtistImage = !_showArtistImage);
    });
  }

  String _trackArtistAlbumLabel(DownloadItem track) {
    final artist = track.artist ?? _unknownArtistLabel;
    final album = track.album ?? _unknownAlbumLabel;
    return '$artist — $album';
  }

  Widget _buildArtworkFallback() {
    return Container(
      color: AppTheme.card,
      child: const Icon(Icons.music_note_rounded, size: 64),
    );
  }

  @override
  void initState() {
    super.initState();
    _startArtworkToggleTimer();
  }

  @override
  void dispose() {
    _toggleTimer?.cancel();
    super.dispose();
  }

  String _formatDuration(Duration duration) {
    final minutes = duration.inMinutes;
    final seconds = duration.inSeconds % 60;
    return '$minutes:${seconds.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final trackAsync = ref.watch(currentTrackProvider);
    final track = trackAsync.value;

    if (track == null) {
      return const Scaffold(
          body: Center(child: Text('Nenhuma música tocando')));
    }

    return Scaffold(
      backgroundColor: AppTheme.surface,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: Semantics(
          label: 'Minimizar player',
          button: true,
          child: IconButton(
            icon: const Icon(Icons.expand_more_rounded, size: 32),
            onPressed: () => Navigator.pop(context),
          ),
        ),
        title: const Text('Tocando agora',
            style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold)),
        centerTitle: true,
      ),
      body: Column(
        children: [
          const SizedBox(height: 20),
          _buildImageSection(track),
          const SizedBox(height: 48),
          _buildInfoSection(track),
          const SizedBox(height: 32),
          _buildControlsSection(),
          const SizedBox(height: 40),
        ],
      ),
    );
  }

  Widget _buildImageSection(DownloadItem track) {
    final albumUrl = track.albumImageUrl ?? track.thumbnail ?? '';
    final artistUrl = track.artistImageUrl ?? albumUrl;

    return Expanded(
      child: Center(
        child: Container(
          width: MediaQuery.of(context).size.width * 0.85,
          margin: const EdgeInsets.symmetric(horizontal: 24),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(24),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.4),
                blurRadius: 30,
                offset: const Offset(0, 10),
              ),
            ],
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(24),
            child: AnimatedSwitcher(
              duration: const Duration(milliseconds: 1000),
              transitionBuilder: (child, animation) =>
                  FadeTransition(opacity: animation, child: child),
              child: CachedNetworkImage(
                key: ValueKey(_showArtistImage ? artistUrl : albumUrl),
                imageUrl: _showArtistImage ? artistUrl : albumUrl,
                fit: BoxFit.cover,
                width: double.infinity,
                height: double.infinity,
                placeholder: (context, url) => _buildArtworkFallback(),
                errorWidget: (context, url, error) => _buildArtworkFallback(),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildInfoSection(DownloadItem track) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 32),
      child: Column(
        children: [
          Text(
            track.title,
            style: const TextStyle(
                color: AppTheme.textPrimary,
                fontSize: 22,
                fontWeight: FontWeight.bold),
            textAlign: TextAlign.center,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
          const SizedBox(height: 8),
          Text(
            _trackArtistAlbumLabel(track),
            style: const TextStyle(
                color: AppTheme.primary,
                fontSize: 16,
                fontWeight: FontWeight.w500),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }

  Widget _buildControlsSection() {
    final positionAsync = ref.watch(positionProvider);
    final position = positionAsync.value ?? Duration.zero;

    final playerService = ref.watch(playerServiceProvider);
    final player = playerService.player;
    final duration = player.duration ?? Duration.zero;

    final playerStateAsync = ref.watch(playerStateProvider);
    final playing = playerStateAsync.value?.playing ?? false;
    final loopMode = ref.watch(loopModeProvider).value ?? LoopMode.off;

    final repeatIcon = switch (loopMode) {
      LoopMode.one => Icons.repeat_one_rounded,
      LoopMode.off || LoopMode.all => Icons.repeat_rounded,
    };
    final repeatColor =
        loopMode == LoopMode.off ? AppTheme.textSecondary : AppTheme.primary;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Column(
        children: [
          Column(
            children: [
              SliderTheme(
                data: SliderTheme.of(context).copyWith(
                  trackHeight: 4,
                  thumbShape:
                      const RoundSliderThumbShape(enabledThumbRadius: 6),
                  activeTrackColor: AppTheme.primary,
                  inactiveTrackColor: AppTheme.border,
                  thumbColor: AppTheme.primary,
                ),
                child: Slider(
                  value: position.inMilliseconds
                      .toDouble()
                      .clamp(0, duration.inMilliseconds.toDouble()),
                  max: duration.inMilliseconds.toDouble(),
                  onChanged: (value) =>
                      player.seek(Duration(milliseconds: value.toInt())),
                ),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(_formatDuration(position),
                        style: const TextStyle(
                            color: AppTheme.textSecondary, fontSize: 12)),
                    Text(_formatDuration(duration),
                        style: const TextStyle(
                            color: AppTheme.textSecondary, fontSize: 12)),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              Semantics(
                label: 'Repetição',
                button: true,
                child: IconButton(
                  icon: Icon(repeatIcon, size: 30, color: repeatColor),
                  onPressed: () => playerService.cycleLoopMode(),
                ),
              ),
              Semantics(
                label: 'Faixa anterior',
                button: true,
                child: IconButton(
                  icon: const Icon(Icons.skip_previous_rounded,
                      size: 48, color: AppTheme.textPrimary),
                  onPressed: () => playerService.previous(),
                ),
              ),
              Semantics(
                label: playing ? 'Pausar' : 'Reproduzir',
                button: true,
                child: Container(
                  decoration: const BoxDecoration(
                      shape: BoxShape.circle, color: AppTheme.primary),
                  child: IconButton(
                    icon: Icon(
                        playing
                            ? Icons.pause_rounded
                            : Icons.play_arrow_rounded,
                        size: 56,
                        color: Colors.white),
                    onPressed: () => playing
                        ? playerService.pause()
                        : playerService.resume(),
                  ),
                ),
              ),
              Semantics(
                label: 'Próxima faixa',
                button: true,
                child: IconButton(
                  icon: const Icon(Icons.skip_next_rounded,
                      size: 48, color: AppTheme.textPrimary),
                  onPressed: () => playerService.next(),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
