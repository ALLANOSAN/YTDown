import 'package:receive_sharing_intent/receive_sharing_intent.dart';

abstract class SharingIntentService {
  Stream<List<SharedMediaFile>> getMediaStream();
  Future<List<SharedMediaFile>> getInitialMedia();
  Future<void> reset();
}

class ReceiveSharingIntentService implements SharingIntentService {
  final ReceiveSharingIntent _instance = ReceiveSharingIntent.instance;

  @override
  Stream<List<SharedMediaFile>> getMediaStream() {
    return _instance.getMediaStream();
  }

  @override
  Future<List<SharedMediaFile>> getInitialMedia() {
    return _instance.getInitialMedia();
  }

  @override
  Future<void> reset() {
    return _instance.reset();
  }
}
