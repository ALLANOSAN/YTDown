import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../services/database_service.dart';

class LibraryPlaylistsNotifier
    extends AsyncNotifier<List<Map<String, dynamic>>> {
  Future<void> _reloadPlaylists() async {
    state = await AsyncValue.guard(DatabaseService.instance.getPlaylists);
  }

  @override
  Future<List<Map<String, dynamic>>> build() async {
    return DatabaseService.instance.getPlaylists();
  }

  Future<void> refresh() async {
    state = const AsyncLoading();
    await _reloadPlaylists();
  }

  Future<String?> createPlaylist(String name, {String? description}) async {
    final trimmed = name.trim();
    if (trimmed.isEmpty) return null;

    final id = await DatabaseService.instance
        .createPlaylist(trimmed, description: description);

    // Refresh the list after creating a playlist.
    await _reloadPlaylists();
    return id;
  }

  Future<int> addTracksToPlaylist({
    required String playlistId,
    required List<String> trackIds,
  }) async {
    var addedCount = 0;
    for (final trackId in trackIds) {
      await DatabaseService.instance.addTrackToPlaylist(playlistId, trackId);
      addedCount++;
    }

    await _reloadPlaylists();
    return addedCount;
  }
}

final libraryPlaylistsProvider =
    AsyncNotifierProvider<LibraryPlaylistsNotifier, List<Map<String, dynamic>>>(
  LibraryPlaylistsNotifier.new,
);
