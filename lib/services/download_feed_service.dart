import '../models/download_item.dart';

abstract class DownloadFeedService {
  Future<List<DownloadItem>> getAllDownloads();
  Stream<DownloadItem?> get updates;
}
