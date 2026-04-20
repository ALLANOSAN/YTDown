import 'dart:async';
import '../models/download_item.dart';

class DownloadProgressService {
  DownloadProgressService._();
  static final instance = DownloadProgressService._();

  final StreamController<DownloadItem?> _updatesController =
      StreamController<DownloadItem?>.broadcast();
  bool _isDisposed = false;

  Stream<DownloadItem?> updates() => _updatesController.stream;

  void addUpdate(DownloadItem? item) {
    if (_isDisposed || _updatesController.isClosed) return;
    _updatesController.add(item);
  }

  void dispose() {
    if (_isDisposed) return;
    _isDisposed = true;
    _updatesController.close();
  }
}
