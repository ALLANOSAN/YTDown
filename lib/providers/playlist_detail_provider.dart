import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/download_item.dart';
import '../services/database_service.dart';

final playlistTracksProvider = FutureProvider.family
    .autoDispose<List<DownloadItem>, String>((ref, playlistId) async {
  return DatabaseService.instance.getPlaylistTracks(playlistId);
});
