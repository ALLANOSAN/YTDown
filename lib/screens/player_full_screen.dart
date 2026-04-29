import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:just_audio/just_audio.dart';
import 'dart:io';
import '../models/download_item.dart';
import '../services/player_service.dart';
import '../providers/player_provider.dart';
import '../theme/app_theme.dart';
import 'dart:ui';

class PlayerFullScreen extends ConsumerWidget {
  const PlayerFullScreen({super.key});

  static const String _unknownArtistLabel = 'Desconhecido';
  static const String _unknownAlbumLabel = 'Sem álbum';

  String _formatDuration(Duration duration) {
    String twoDigits(int n) => n.toString().padLeft(2, '0');
    final minutes = twoDigits(duration.inMinutes.remainder(60));
    final seconds = twoDigits(duration.inSeconds.remainder(60));
    return "$minutes:$seconds";
  }

  String _resolveArtworkSource(DownloadItem track, bool showArtistImage) {
    final preferred =
        (showArtistImage ? track.artistImageUrl : track.albumImageUrl)?.trim();
    if (preferred != null && preferred.isNotEmpty) {
      return preferred;
    }

    final fallback = track.thumbnail?.trim();
    if (fallback != null && fallback.isNotEmpty) {
      return fallback;
    }

    return '';
  }

  String _artworkVariant(bool showArtistImage) {
    return showArtistImage ? 'artist' : 'album';
  }

  String _artworkKey({
    required String prefix,
    required DownloadItem track,
    required bool showArtistImage,
    required String artworkSource,
  }) {
    return '${prefix}_${track.id}_${_artworkVariant(showArtistImage)}_$artworkSource';
  }

  String _trackArtistAlbumLabel(DownloadItem track) {
    final artist = track.artist ?? _unknownArtistLabel;
    final album = track.album ?? _unknownAlbumLabel;
    return '$artist • $album';
  }

  bool _isRemoteArtworkSource(String source) {
    final uri = Uri.tryParse(source);
    if (uri == null) return false;
    final scheme = uri.scheme.toLowerCase();
    return scheme == 'http' || scheme == 'https';
  }

  String? _resolveLocalArtworkPath(String source) {
    final trimmed = source.trim();
    if (trimmed.isEmpty) return null;

    final uri = Uri.tryParse(trimmed);
    if (uri != null && uri.scheme.toLowerCase() == 'file') {
      try {
        return uri.toFilePath();
      } catch (_) {
        return null;
      }
    }

    if (trimmed.startsWith('/')) {
      return trimmed;
    }

    return null;
  }

  Widget _buildArtworkImage({
    required String source,
    required Key key,
    required BoxFit fit,
    required Widget fallback,
  }) {
    final trimmed = source.trim();
    if (trimmed.isEmpty) {
      return KeyedSubtree(key: key, child: fallback);
    }

    if (_isRemoteArtworkSource(trimmed)) {
      return CachedNetworkImage(
        key: key,
        imageUrl: trimmed,
        fit: fit,
        errorWidget: (_, __, ___) => fallback,
      );
    }

    final localPath = _resolveLocalArtworkPath(trimmed);
    if (localPath != null && File(localPath).existsSync()) {
      return Image.file(
        File(localPath),
        key: key,
        fit: fit,
        errorBuilder: (_, __, ___) => fallback,
      );
    }

    return KeyedSubtree(key: key, child: fallback);
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final trackAsync = ref.watch(currentTrackProvider);
    final track = trackAsync.value ??
        PlayerService.instance.currentTrack; // Fallback sync value

    if (track == null) {
      return const Scaffold(
          body: Center(child: Text('Nenhuma música tocando')));
    }

    final showArtistImage = ref.watch(artToggleProvider);
    final artworkSource = _resolveArtworkSource(track, showArtistImage);

    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          // Background Blur
          Positioned.fill(
            child: AnimatedSwitcher(
              duration: const Duration(seconds: 2),
              child: _buildArtworkImage(
                source: artworkSource,
                key: ValueKey(
                  _artworkKey(
                    prefix: 'player_bg',
                    track: track,
                    showArtistImage: showArtistImage,
                    artworkSource: artworkSource,
                  ),
                ),
                fit: BoxFit.cover,
                fallback: Container(color: AppTheme.surface),
              ),
            ),
          ),
          Positioned.fill(
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 40, sigmaY: 40),
              child: Container(color: Colors.black.withValues(alpha: 0.5)),
            ),
          ),

          // Content
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 32),
              child: Column(
                children: [
                  _buildHeader(context),
                  const Spacer(),
                  _buildArt(context, track, showArtistImage),
                  const Spacer(),
                  _buildTrackInfo(track),
                  const SizedBox(height: 40),
                  _buildProgressBar(ref),
                  const SizedBox(height: 40),
                  _buildControls(ref),
                  const Spacer(flex: 2),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        IconButton(
          icon: const Icon(Icons.keyboard_arrow_down_rounded,
              color: Colors.white, size: 32),
          onPressed: () => Navigator.pop(context),
        ),
        const Text(
          'TOCANDO AGORA',
          style: TextStyle(
              color: Colors.white,
              fontSize: 12,
              fontWeight: FontWeight.bold,
              letterSpacing: 2),
        ),
        IconButton(
          icon: const Icon(Icons.more_vert_rounded, color: Colors.white),
          onPressed: () {},
        ),
      ],
    );
  }

  Widget _buildArt(
      BuildContext context, DownloadItem track, bool showArtistImage) {
    final artworkSource = _resolveArtworkSource(track, showArtistImage);

    return Hero(
      tag: 'album_art_${track.id}',
      child: AnimatedSwitcher(
        duration: const Duration(seconds: 1),
        transitionBuilder: (child, animation) => FadeTransition(
            opacity: animation,
            child: ScaleTransition(scale: animation, child: child)),
        child: Container(
          key: ValueKey(
            _artworkKey(
              prefix: 'player_art',
              track: track,
              showArtistImage: showArtistImage,
              artworkSource: artworkSource,
            ),
          ),
          width: MediaQuery.of(context).size.width * 0.8,
          height: MediaQuery.of(context).size.width * 0.8,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(24),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.5),
                blurRadius: 30,
                offset: const Offset(0, 20),
              ),
            ],
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(24),
            child: _buildArtworkImage(
              source: artworkSource,
              key: ValueKey(
                _artworkKey(
                  prefix: 'player_art_image',
                  track: track,
                  showArtistImage: showArtistImage,
                  artworkSource: artworkSource,
                ),
              ),
              fit: BoxFit.cover,
              fallback: const Icon(
                Icons.music_note_rounded,
                size: 80,
                color: Colors.white24,
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildTrackInfo(DownloadItem track) {
    return Column(
      children: [
        Text(
          track.title,
          style: const TextStyle(
              color: Colors.white, fontSize: 24, fontWeight: FontWeight.bold),
          textAlign: TextAlign.center,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
        ).animate().fadeIn(duration: 600.ms).slideY(begin: 0.2),
        const SizedBox(height: 12),
        Text(
          _trackArtistAlbumLabel(track),
          style: TextStyle(
              color: Colors.white.withValues(alpha: 0.7), fontSize: 16),
          textAlign: TextAlign.center,
        ).animate().fadeIn(delay: 200.ms, duration: 600.ms).slideY(begin: 0.2),
      ],
    );
  }

  Widget _buildProgressBar(WidgetRef ref) {
    final position = ref.watch(positionProvider).value ?? Duration.zero;
    final duration = ref.watch(durationProvider).value ?? Duration.zero;

    return Column(
      children: [
        SliderTheme(
          data: SliderTheme.of(ref.context).copyWith(
            trackHeight: 4,
            thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6),
            overlayShape: const RoundSliderOverlayShape(overlayRadius: 14),
            activeTrackColor: Colors.white,
            inactiveTrackColor: Colors.white.withValues(alpha: 0.2),
            thumbColor: Colors.white,
          ),
          child: Slider(
            value: position.inMilliseconds
                .toDouble()
                .clamp(0, duration.inMilliseconds.toDouble()),
            max: duration.inMilliseconds.toDouble() > 0
                ? duration.inMilliseconds.toDouble()
                : 1.0,
            onChanged: (value) {
              PlayerService.instance
                  .seek(Duration(milliseconds: value.toInt()));
            },
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(_formatDuration(position),
                  style: TextStyle(
                      color: Colors.white.withValues(alpha: 0.5),
                      fontSize: 12)),
              Text(_formatDuration(duration),
                  style: TextStyle(
                      color: Colors.white.withValues(alpha: 0.5),
                      fontSize: 12)),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildControls(WidgetRef ref) {
    final playerState = ref.watch(playerStateProvider).value;
    final playing = playerState?.playing ?? false;
    final loopMode = ref.watch(loopModeProvider).value ?? LoopMode.off;

    final repeatIcon = switch (loopMode) {
      LoopMode.one => Icons.repeat_one_rounded,
      LoopMode.off || LoopMode.all => Icons.repeat_rounded,
    };
    final repeatColor =
        loopMode == LoopMode.off ? Colors.white54 : Colors.white;

    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
      children: [
        IconButton(
          icon: const Icon(Icons.shuffle_rounded,
              color: Colors.white54, size: 24),
          onPressed: () {},
        ),
        IconButton(
          icon: const Icon(Icons.skip_previous_rounded,
              color: Colors.white, size: 48),
          onPressed: () => PlayerService.instance.previous(),
        ),
        Container(
          width: 80,
          height: 80,
          decoration:
              const BoxDecoration(shape: BoxShape.circle, color: Colors.white),
          child: IconButton(
            icon: Icon(playing ? Icons.pause_rounded : Icons.play_arrow_rounded,
                color: Colors.black, size: 48),
            onPressed: () => playing
                ? PlayerService.instance.pause()
                : PlayerService.instance.resume(),
          ),
        ),
        IconButton(
          icon: const Icon(Icons.skip_next_rounded,
              color: Colors.white, size: 48),
          onPressed: () => PlayerService.instance.next(),
        ),
        IconButton(
          icon: Icon(repeatIcon, color: repeatColor, size: 30),
          tooltip: 'Repetir',
          onPressed: () => PlayerService.instance.cycleLoopMode(),
        ),
      ],
    );
  }
}
