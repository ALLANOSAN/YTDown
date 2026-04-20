import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../services/database_service.dart';

// Provider para as Buscas Recentes
final recentSearchesProvider =
    FutureProvider.autoDispose<List<String>>((ref) async {
  final databaseService = DatabaseService.instance;
  return databaseService.getRecentSearches();
});

// Provider para os Favoritos
final favoritesProvider =
    FutureProvider.autoDispose<List<Map<String, dynamic>>>((ref) async {
  final databaseService = DatabaseService.instance;
  return databaseService.getFavorites();
});

// StateNotifier para isolar o estado de UI da Home (carregando processos, erros)
class HomeUiState {
  static const Object _unset = Object();

  final bool isLoading;
  final bool isProcessingRequest;
  final String? error;
  final String currentUrl;

  const HomeUiState({
    this.isLoading = false,
    this.isProcessingRequest = false,
    this.error,
    this.currentUrl = '',
  });

  HomeUiState copyWith({
    bool? isLoading,
    bool? isProcessingRequest,
    Object? error = _unset,
    String? currentUrl,
  }) {
    return HomeUiState(
      isLoading: isLoading ?? this.isLoading,
      isProcessingRequest: isProcessingRequest ?? this.isProcessingRequest,
      error: identical(error, _unset) ? this.error : error as String?,
      currentUrl: currentUrl ?? this.currentUrl,
    );
  }
}

class HomeUiNotifier extends Notifier<HomeUiState> {
  @override
  HomeUiState build() => const HomeUiState();

  void setLoading(bool loading) => state = state.copyWith(isLoading: loading);
  void setProcessing(bool processing) =>
      state = state.copyWith(isProcessingRequest: processing);
  void setError(String? error) => state = state.copyWith(error: error);
  void setUrl(String url) =>
      state = state.copyWith(currentUrl: url, error: null);

  void clearError() => state = state.copyWith(error: null);
}

final homeUiProvider = NotifierProvider<HomeUiNotifier, HomeUiState>(() {
  return HomeUiNotifier();
});
