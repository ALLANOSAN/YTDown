import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:just_audio/just_audio.dart';
import 'dart:async';
import '../models/download_item.dart';
import '../services/player_service.dart';

final playerServiceProvider = Provider<PlayerService>((ref) {
  return PlayerService.instance;
});

PlayerService _watchPlayerService(Ref ref) {
  return ref.watch(playerServiceProvider);
}

final currentTrackProvider = StreamProvider<DownloadItem?>((ref) {
  final playerService = _watchPlayerService(ref);
  return playerService.currentTrackStream;
});

final positionProvider = StreamProvider<Duration>((ref) {
  final playerService = _watchPlayerService(ref);
  return playerService.positionStream;
});

final playerStateProvider = StreamProvider<PlayerState>((ref) {
  final playerService = _watchPlayerService(ref);
  return playerService.playerStateStream;
});

final durationProvider = StreamProvider<Duration?>((ref) {
  final playerService = _watchPlayerService(ref);
  return playerService.durationStream;
});

final loopModeProvider = StreamProvider<LoopMode>((ref) {
  final playerService = _watchPlayerService(ref);
  return playerService.player.loopModeStream;
});

class ArtToggleNotifier extends Notifier<bool> {
  Timer? _timer;
  final bool enableTimer;

  ArtToggleNotifier({this.enableTimer = true});

  void _cancelTimer() {
    _timer?.cancel();
    _timer = null;
  }

  void _onTick(Timer timer) {
    final track = ref.read(playerServiceProvider).currentTrack;
    if (track == null) {
      _cancelTimer();
      return;
    }
    state = !state;
  }

  void _startTimer() {
    _cancelTimer();
    if (!enableTimer) return;
    _timer = Timer.periodic(const Duration(seconds: 10), _onTick);
  }

  @override
  bool build() {
    _startTimer();

    ref.onDispose(() {
      _cancelTimer();
    });

    return false;
  }
}

final artToggleProvider = NotifierProvider<ArtToggleNotifier, bool>(() {
  return ArtToggleNotifier();
});
