import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../models/download_item.dart';

class LibraryPlaylistSelectionState {
  const LibraryPlaylistSelectionState({
    required this.selectedTrackIds,
  });

  final Set<String> selectedTrackIds;

  bool get isSelectionMode => selectedTrackIds.isNotEmpty;
  int get selectedCount => selectedTrackIds.length;

  LibraryPlaylistSelectionState copyWith({
    Set<String>? selectedTrackIds,
  }) {
    return LibraryPlaylistSelectionState(
      selectedTrackIds: selectedTrackIds ?? this.selectedTrackIds,
    );
  }
}

class LibraryPlaylistSelectionNotifier
    extends Notifier<LibraryPlaylistSelectionState> {
  static const LibraryPlaylistSelectionState _emptySelectionState =
      LibraryPlaylistSelectionState(selectedTrackIds: {});

  @override
  LibraryPlaylistSelectionState build() {
    return _emptySelectionState;
  }

  void _setSelection(Set<String> trackIds) {
    state = state.copyWith(selectedTrackIds: trackIds);
  }

  void selectOnly(String trackId) {
    _setSelection({trackId});
  }

  void toggleTrack(String trackId) {
    final next = Set<String>.from(state.selectedTrackIds);
    if (next.contains(trackId)) {
      next.remove(trackId);
      _setSelection(next);
      return;
    }
    next.add(trackId);
    _setSelection(next);
  }

  void selectAll(List<DownloadItem> tracks) {
    final ids = tracks.map((track) => track.id).toSet();
    _setSelection(ids);
  }

  void clear() {
    state = _emptySelectionState;
  }
}

final libraryPlaylistSelectionProvider = NotifierProvider<
    LibraryPlaylistSelectionNotifier, LibraryPlaylistSelectionState>(
  LibraryPlaylistSelectionNotifier.new,
);
