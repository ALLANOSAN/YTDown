import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../utils/common_utils.dart';

class BrowserState {
  static const String defaultYouTubeUrl = 'https://m.youtube.com';

  final String currentUrl;
  final bool isYoutube;
  final bool isLoading;
  final double progress;
  final bool isInitialLoad;
  final bool hasShownError;

  const BrowserState({
    this.currentUrl = defaultYouTubeUrl,
    this.isYoutube = true,
    this.isLoading = false,
    this.progress = 0.0,
    this.isInitialLoad = true,
    this.hasShownError = false,
  });

  BrowserState copyWith({
    String? currentUrl,
    bool? isYoutube,
    bool? isLoading,
    double? progress,
    bool? isInitialLoad,
    bool? hasShownError,
  }) {
    return BrowserState(
      currentUrl: currentUrl ?? this.currentUrl,
      isYoutube: isYoutube ?? this.isYoutube,
      isLoading: isLoading ?? this.isLoading,
      progress: progress ?? this.progress,
      isInitialLoad: isInitialLoad ?? this.isInitialLoad,
      hasShownError: hasShownError ?? this.hasShownError,
    );
  }
}

class BrowserNotifier extends Notifier<BrowserState> {
  @override
  BrowserState build() {
    return const BrowserState();
  }

  bool _isYouTubeUrl(String url) {
    return YouTubeUtils.isYouTubeUrl(url);
  }

  void setProgress(double progress) {
    state = state.copyWith(progress: progress);
  }

  void setUrl(String url) {
    state = state.copyWith(
      currentUrl: url,
      isYoutube: _isYouTubeUrl(url),
    );
  }

  void setLoading(bool isLoading) {
    state = state.copyWith(isLoading: isLoading);
  }

  void setInitialLoad(bool isInitialLoad) {
    state = state.copyWith(isInitialLoad: isInitialLoad);
  }

  void setHasShownError(bool hasShownError) {
    state = state.copyWith(hasShownError: hasShownError);
  }
}

final browserProvider =
    NotifierProvider.autoDispose<BrowserNotifier, BrowserState>(
  BrowserNotifier.new,
);
