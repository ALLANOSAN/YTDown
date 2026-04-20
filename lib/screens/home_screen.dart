import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:receive_sharing_intent/receive_sharing_intent.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../theme/app_theme.dart';
import '../models/download_item.dart';
import 'downloads_screen.dart';
import 'library_screen.dart';
import '../utils/video_info_handler.dart';
import '../services/download_service.dart';
import '../services/player_service.dart';
import 'player_full_screen.dart';
import 'browser_screen.dart';
import '../services/database_service.dart';
import '../utils/common_utils.dart';
import '../widgets/lazy_indexed_stack.dart';
import '../providers/home_provider.dart';
import '../providers/player_provider.dart';
import '../providers/sharing_provider.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen>
    with SingleTickerProviderStateMixin {
  static const String _invalidYoutubeUrlMessage =
      'Insira um link válido do YouTube';

  int _currentIndex = 0;
  final _urlController = TextEditingController();
  StreamSubscription? _sharingSubscription;

  late AnimationController _animationController;
  late Animation<double> _fadeAnimation;
  static const _notificationChannel =
      MethodChannel('com.example.ytdown/notification');

  @override
  void initState() {
    super.initState();
    _initSharingIntent();
    _initNotificationListener();

    _animationController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 800),
    );
    _fadeAnimation = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _animationController, curve: Curves.easeOut),
    );
    _animationController.forward();
  }

  void _initNotificationListener() {
    _notificationChannel.setMethodCallHandler((call) async {
      if (call.method == "onNotificationClick") {
        if (!mounted) return;
        _openFullPlayer();
      }
    });
  }

  void _openFullPlayer() {
    final playerService = PlayerService.instance;
    if (playerService.currentTrack == null) {
      return;
    }
    _pushFullPlayerScreen();
  }

  Future<void> _pushFullPlayerScreen() {
    return Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => const PlayerFullScreen(),
        fullscreenDialog: true,
      ),
    );
  }

  void _setCurrentTab(int index) {
    if (_currentIndex == index) {
      return;
    }
    HapticFeedback.lightImpact();
    setState(() => _currentIndex = index);
  }

  void _clearUrlInput() {
    _urlController.clear();
    final uiNotifier = ref.read(homeUiProvider.notifier);
    if (ref.read(homeUiProvider).error != null) {
      uiNotifier.setError(null);
    }
    HapticFeedback.lightImpact();
  }

  void _initSharingIntent() {
    final sharingIntent = ref.read(sharingIntentProvider);

    _sharingSubscription =
        sharingIntent.getMediaStream().listen((List<SharedMediaFile> files) {
      if (!mounted) return;
      final rawContent = files.isNotEmpty ? files.first.path : null;
      final url = rawContent != null ? _extractYoutubeUrl(rawContent) : null;
      if (url != null) {
        _handleUrl(url);
      } else if (rawContent != null) {
        _reportInvalidYoutubeUrl(ref.read(homeUiProvider.notifier));
      }
    }, onError: (_) {});

    sharingIntent.getInitialMedia().then((files) {
      if (!mounted) return;
      final rawContent = files.isNotEmpty ? files.first.path : null;
      final url = rawContent != null ? _extractYoutubeUrl(rawContent) : null;
      if (url != null) {
        _handleUrl(url);
        sharingIntent.reset();
      } else if (rawContent != null) {
        _reportInvalidYoutubeUrl(ref.read(homeUiProvider.notifier));
      }
    });
  }

  String? _extractYoutubeUrl(String raw) => YouTubeUtils.extractUrl(raw);

  Future<void> _handleUrl(String sharedOrTypedValue) async {
    final uiNotifier = ref.read(homeUiProvider.notifier);
    final uiState = ref.read(homeUiProvider);

    if (uiState.isProcessingRequest) return;

    final url = _extractYoutubeUrl(sharedOrTypedValue);
    if (url == null) {
      _reportInvalidYoutubeUrl(uiNotifier);
      return;
    }

    if (uiState.isLoading) return;

    _prepareUrlInput(url, uiNotifier);
    _logUrlSearch(url);
    await _fetchVideoInfo(url, uiNotifier);
  }

  void _reportInvalidYoutubeUrl(dynamic uiNotifier) {
    if (!mounted) return;
    uiNotifier.setError(_invalidYoutubeUrlMessage);
    HapticFeedback.heavyImpact();
  }

  void _prepareUrlInput(String url, dynamic uiNotifier) {
    _urlController
      ..text = url
      ..selection = TextSelection.collapsed(offset: url.length);
    uiNotifier.setLoading(true);
    uiNotifier.setError(null);
    uiNotifier.setProcessing(true);
    HapticFeedback.mediumImpact();
  }

  void _logUrlSearch(String url) {
    try {
      final databaseService = DatabaseService.instance;
      unawaited(
        databaseService.insertSearchQuery(url).catchError((_) {}),
      );
    } catch (_) {
      // Ignore database logging failures in UI flows.
    }
  }

  Future<void> _fetchVideoInfo(String url, dynamic uiNotifier) async {
    try {
      final info = await DownloadService.instance.fetchVideoInfo(url);
      if (!mounted) return;

      uiNotifier.setLoading(false);
      ref.invalidate(recentSearchesProvider);
      ref.invalidate(favoritesProvider);
      await _handleVideoInfoResult(info, url);
    } catch (e) {
      if (!mounted) return;
      uiNotifier.setLoading(false);
      uiNotifier.setError('Não foi possível obter informações do vídeo');
      HapticFeedback.heavyImpact();
    } finally {
      if (mounted) {
        uiNotifier.setProcessing(false);
      }
    }
  }

  Future<void> _handleVideoInfoResult(
    Map<String, dynamic> info,
    String url,
  ) async {
    final navigatorContext = context;
    if (!navigatorContext.mounted) return;
    await VideoInfoHandler.handleVideoInfo(navigatorContext, info, url);
  }

  @override
  void dispose() {
    _sharingSubscription?.cancel();
    _urlController.dispose();
    _animationController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Column(
        children: [
          Expanded(
            child: LazyIndexedStack(
              index: _currentIndex,
              children: [
                _buildSearchTab(),
                const DownloadsScreen(),
                const LibraryScreen(),
                const BrowserScreen(),
              ],
            ),
          ),
          _buildMiniPlayer(),
        ],
      ),
      bottomNavigationBar: _buildBottomNav(),
    );
  }

  Widget _buildSearchTab() {
    final uiState = ref.watch(homeUiProvider);
    return SafeArea(
      child: CustomScrollView(
        slivers: [
          SliverToBoxAdapter(child: _buildHeader()),
          SliverToBoxAdapter(child: _buildUrlInput()),
          if (uiState.error != null)
            SliverToBoxAdapter(child: _buildError(uiState.error!)),
          SliverToBoxAdapter(child: _buildDiscoveryView()),
        ],
      ),
    );
  }

  Widget _buildDiscoveryView() {
    final recentSearchesAsync = ref.watch(recentSearchesProvider);
    final favoritesAsync = ref.watch(favoritesProvider);

    final recentSearches = recentSearchesAsync.value ?? [];
    final favorites = favoritesAsync.value ?? [];

    if (recentSearches.isEmpty && favorites.isEmpty) {
      return _buildHint();
    }

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (favorites.isNotEmpty) ...[
            _buildSectionHeader(
                'Favoritos', Icons.favorite_rounded, AppTheme.accent),
            const SizedBox(height: 12),
            SizedBox(
              height: 120,
              child: ListView.builder(
                scrollDirection: Axis.horizontal,
                itemCount: favorites.length,
                itemBuilder: (context, index) {
                  final fav = favorites[index];
                  return _buildFavoriteCard(fav);
                },
              ),
            ),
            const SizedBox(height: 24),
          ],
          if (recentSearches.isNotEmpty) ...[
            _buildSectionHeader(
                'Buscas Recentes', Icons.history_rounded, AppTheme.primary),
            const SizedBox(height: 12),
            ListView.builder(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              itemCount: recentSearches.length,
              itemBuilder: (context, index) {
                return _buildHistoryItem(recentSearches[index]);
              },
            ),
            const SizedBox(height: 32),
          ],
        ],
      ),
    );
  }

  Widget _buildSectionHeader(String title, IconData icon, Color color) {
    return Row(
      children: [
        Icon(icon, size: 18, color: color),
        const SizedBox(width: 8),
        Text(
          title,
          style: const TextStyle(
            color: AppTheme.textPrimary,
            fontSize: 16,
            fontWeight: FontWeight.bold,
          ),
        ),
      ],
    );
  }

  Widget _buildFavoriteCard(Map<String, dynamic> fav) {
    return GestureDetector(
      onTap: () => _handleUrl(fav['url']),
      child: Container(
        width: 140,
        margin: const EdgeInsets.only(right: 12),
        decoration: BoxDecoration(
          color: AppTheme.card,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: AppTheme.border),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: ClipRRect(
                borderRadius:
                    const BorderRadius.vertical(top: Radius.circular(16)),
                child: fav['thumbnail'] != null
                    ? CachedNetworkImage(
                        imageUrl: fav['thumbnail'],
                        fit: BoxFit.cover,
                        width: double.infinity,
                      )
                    : Container(color: AppTheme.surfaceElevated),
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(8.0),
              child: Text(
                fav['title'],
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                    color: AppTheme.textPrimary,
                    fontSize: 11,
                    fontWeight: FontWeight.w500),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHistoryItem(String query) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Material(
        color: AppTheme.card,
        borderRadius: BorderRadius.circular(12),
        child: InkWell(
          onTap: () => _handleUrl(query),
          borderRadius: BorderRadius.circular(12),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            child: Row(
              children: [
                const Icon(Icons.search_rounded,
                    size: 16, color: AppTheme.textTertiary),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    query,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                        color: AppTheme.textSecondary, fontSize: 13),
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.close_rounded,
                      size: 16, color: AppTheme.textTertiary),
                  onPressed: () async {
                    final databaseService = DatabaseService.instance;
                    await databaseService.deleteSearchQuery(query);
                    ref.invalidate(recentSearchesProvider);
                  },
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Container(
      padding: const EdgeInsets.fromLTRB(24, 32, 24, 16),
      child: Row(
        children: [
          Container(
            width: 48,
            height: 48,
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [AppTheme.primary, AppTheme.accent],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(14),
              boxShadow: [
                BoxShadow(
                  color: AppTheme.primary.withValues(alpha: 0.3),
                  blurRadius: 12,
                  offset: const Offset(0, 4),
                ),
              ],
            ),
            child: const Icon(
              Icons.download_rounded,
              color: Colors.white,
              size: 26,
            ),
          ).animate().fadeIn(duration: 600.ms).slideX(begin: -0.3, end: 0),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'YTDown',
                  style: TextStyle(
                    fontSize: 26,
                    fontWeight: FontWeight.w800,
                    color: AppTheme.textPrimary,
                    letterSpacing: -0.5,
                    height: 1.2,
                  ),
                )
                    .animate()
                    .fadeIn(delay: 200.ms, duration: 600.ms)
                    .slideX(begin: -0.2, end: 0),
                const SizedBox(height: 4),
                Text(
                  'Baixe vídeos do YouTube',
                  style: TextStyle(
                    color: AppTheme.textSecondary,
                    fontSize: 14,
                    fontWeight: FontWeight.w500,
                  ),
                )
                    .animate()
                    .fadeIn(delay: 400.ms, duration: 600.ms)
                    .slideX(begin: -0.2, end: 0),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildUrlInput() {
    return FadeTransition(
      opacity: _fadeAnimation,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(24, 8, 24, 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Cole o link do YouTube',
              style: TextStyle(
                color: AppTheme.textSecondary,
                fontSize: 13,
                fontWeight: FontWeight.w600,
                letterSpacing: 0.3,
              ),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _urlController,
                    keyboardType: TextInputType.url,
                    textInputAction: TextInputAction.search,
                    autofillHints: const [AutofillHints.url],
                    style: const TextStyle(
                      color: AppTheme.textPrimary,
                      fontSize: 15,
                      fontWeight: FontWeight.w500,
                    ),
                    decoration: InputDecoration(
                      hintText: 'https://youtube.com/watch?v=...',
                      prefixIcon: const Icon(Icons.link_rounded,
                          color: AppTheme.textSecondary, size: 22),
                      suffixIconConstraints:
                          const BoxConstraints(minWidth: 40, minHeight: 40),
                      suffixIcon: ValueListenableBuilder<TextEditingValue>(
                        valueListenable: _urlController,
                        builder: (context, value, _) {
                          if (value.text.isEmpty) {
                            return const SizedBox.shrink();
                          }
                          return Tooltip(
                            message: 'Limpar link',
                            child: IconButton(
                              tooltip: 'Limpar link',
                              icon: const Icon(Icons.clear_rounded,
                                  color: AppTheme.textSecondary, size: 20),
                              onPressed: _clearUrlInput,
                            ),
                          );
                        },
                      ),
                      filled: true,
                      fillColor: AppTheme.card,
                    ),
                    onSubmitted: _handleUrl,
                  ),
                ),
                const SizedBox(width: 12),
                _buildSearchButton(),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildError(String errorMsg) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppTheme.error.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: AppTheme.error.withValues(alpha: 0.3)),
        ),
        child: Row(
          children: [
            const Icon(Icons.error_outline_rounded,
                color: AppTheme.error, size: 22),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                errorMsg,
                style: const TextStyle(
                  color: AppTheme.error,
                  fontSize: 13,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
          ],
        ),
      ).animate().fadeIn().shake(),
    );
  }

  Widget _buildSearchButton() {
    final uiState = ref.watch(homeUiProvider);
    final isLoading = uiState.isLoading;
    return Semantics(
      button: true,
      enabled: !isLoading,
      label: 'Buscar vídeo no YouTube',
      hint: 'Analisa o link para abrir as opções de formato',
      child: Tooltip(
        message: 'Buscar vídeo',
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          width: 56,
          height: 56,
          decoration: BoxDecoration(
            gradient: isLoading
                ? null
                : const LinearGradient(
                    colors: [AppTheme.primary, AppTheme.primaryDark],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
            color: isLoading ? AppTheme.surfaceElevated : null,
            borderRadius: BorderRadius.circular(16),
            boxShadow: isLoading
                ? null
                : [
                    BoxShadow(
                      color: AppTheme.primary.withValues(alpha: 0.4),
                      blurRadius: 12,
                      offset: const Offset(0, 4),
                    ),
                  ],
          ),
          child: IconButton(
            key: const Key('home_search_button'),
            tooltip: 'Buscar vídeo',
            onPressed: isLoading
                ? null
                : () {
                    HapticFeedback.lightImpact();
                    _handleUrl(_urlController.text);
                  },
            icon: isLoading
                ? const SizedBox(
                    width: 24,
                    height: 24,
                    child: CircularProgressIndicator(
                      strokeWidth: 2.5,
                      color: AppTheme.primary,
                    ),
                  )
                : const Icon(Icons.search_rounded,
                    color: Colors.white, size: 26),
          ),
        ),
      ),
    );
  }

  Widget _buildHint() {
    return FadeTransition(
      opacity: _fadeAnimation,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(24, 24, 24, 32),
        child: Container(
          padding: const EdgeInsets.all(24),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [
                AppTheme.card,
                AppTheme.surfaceElevated,
              ],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
            borderRadius: BorderRadius.circular(20),
            border: Border.all(color: AppTheme.border),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.2),
                blurRadius: 20,
                offset: const Offset(0, 8),
              ),
            ],
          ),
          child: Column(
            children: [
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppTheme.primary.withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(16),
                ),
                child: const Icon(Icons.share_rounded,
                    color: AppTheme.primary, size: 36),
              ),
              const SizedBox(height: 16),
              const Text(
                'Compartilhe direto do YouTube',
                style: TextStyle(
                  color: AppTheme.textPrimary,
                  fontSize: 17,
                  fontWeight: FontWeight.w700,
                  letterSpacing: -0.3,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                'Abra um vídeo no YouTube, toque em Compartilhar e selecione YTDown.',
                style: TextStyle(
                  color: AppTheme.textSecondary,
                  fontSize: 14,
                  height: 1.4,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 20),
              _buildFormatChips(),
            ],
          ),
        )
            .animate()
            .fadeIn(delay: 300.ms, duration: 800.ms)
            .slideY(begin: 0.2, end: 0),
      ),
    );
  }

  Widget _buildFormatChips() {
    final formats = [
      {'label': 'MP3', 'icon': Icons.music_note_rounded},
      {'label': 'M4A', 'icon': Icons.audio_file_rounded},
      {'label': 'FLAC', 'icon': Icons.high_quality_rounded},
      {'label': 'MP4', 'icon': Icons.videocam_rounded},
      {'label': 'MKV', 'icon': Icons.movie_rounded},
    ];

    return Wrap(
      spacing: 10,
      runSpacing: 10,
      alignment: WrapAlignment.center,
      children: formats
          .map((f) => Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                decoration: BoxDecoration(
                  color: AppTheme.surface,
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(color: AppTheme.border),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      f['icon'] as IconData,
                      color: AppTheme.primary,
                      size: 16,
                    ),
                    const SizedBox(width: 6),
                    Text(
                      f['label'] as String,
                      style: const TextStyle(
                        color: AppTheme.textSecondary,
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ))
          .toList(),
    );
  }

  Widget _buildBottomNav() {
    return Container(
      decoration: BoxDecoration(
        color: AppTheme.surface,
        border: Border(
          top: BorderSide(color: AppTheme.border, width: 0.5),
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.2),
            blurRadius: 20,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: BottomNavigationBar(
        currentIndex: _currentIndex,
        onTap: _setCurrentTab,
        items: const [
          BottomNavigationBarItem(
            icon: Icon(Icons.search_rounded),
            label: 'Buscar',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.folder_rounded),
            label: 'Downloads',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.library_music_rounded),
            label: 'Biblioteca',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.explore_rounded),
            label: 'Navegador',
          ),
        ],
      ),
    );
  }

  Widget _buildMiniPlayer() {
    final trackAsync = ref.watch(currentTrackProvider);
    final track = trackAsync.value;

    if (track == null) return const SizedBox.shrink();

    return GestureDetector(
      onTap: _openFullPlayer,
      child: Container(
        height: 64,
        width: double.infinity,
        decoration: BoxDecoration(
          color: AppTheme.card,
          border: Border(top: BorderSide(color: AppTheme.border, width: 0.5)),
        ),
        child: Column(
          children: [
            // Barra de progresso minificada e interativa
            const SizedBox(
              height: 12,
              child: _MiniPlayerProgressBar(),
            ),
            Expanded(
              child: Row(
                children: [
                  _MiniPlayerArt(track: track),
                  const SizedBox(width: 12),
                  Expanded(
                    child: _MiniPlayerInfo(track: track),
                  ),
                  const _MiniPlayerControls(),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// Widgets separados para o Mini Player - evita rebuilds desnecessários
class _MiniPlayerArt extends StatelessWidget {
  final DownloadItem track;
  const _MiniPlayerArt({required this.track});

  @override
  Widget build(BuildContext context) {
    return Hero(
      tag: 'album_art_${track.id}',
      child: ClipRRect(
        borderRadius: BorderRadius.circular(8),
        child: CachedNetworkImage(
          imageUrl: track.albumImageUrl ?? track.thumbnail ?? '',
          width: 44,
          height: 44,
          fit: BoxFit.cover,
          errorWidget: (_, __, ___) => const Icon(Icons.music_note_rounded),
        ),
      ),
    );
  }
}

class _MiniPlayerInfo extends StatelessWidget {
  final DownloadItem track;
  const _MiniPlayerInfo({required this.track});

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          track.title,
          style: const TextStyle(
              color: AppTheme.textPrimary,
              fontSize: 13,
              fontWeight: FontWeight.bold),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        Text(
          track.artist ?? 'Desconhecido',
          style: const TextStyle(color: AppTheme.textSecondary, fontSize: 11),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
      ],
    );
  }
}

class _MiniPlayerControls extends ConsumerWidget {
  const _MiniPlayerControls();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final playerService = ref.watch(playerServiceProvider);
    final playerStateAsync = ref.watch(playerStateProvider);
    final playing = playerStateAsync.value?.playing ?? false;

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        IconButton(
          icon: const Icon(Icons.skip_previous_rounded,
              color: AppTheme.textSecondary, size: 24),
          onPressed: () => playerService.previous(),
        ),
        IconButton(
          icon: Icon(playing ? Icons.pause_rounded : Icons.play_arrow_rounded,
              color: AppTheme.primary),
          onPressed: () =>
              playing ? playerService.pause() : playerService.resume(),
        ),
        IconButton(
          icon: const Icon(Icons.skip_next_rounded,
              color: AppTheme.textSecondary, size: 24),
          onPressed: () => playerService.next(),
        ),
      ],
    );
  }
}

class _MiniPlayerProgressBar extends ConsumerWidget {
  const _MiniPlayerProgressBar();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final positionAsync = ref.watch(positionProvider);
    final pos = positionAsync.value?.inMilliseconds.toDouble() ?? 0.0;

    final playerService = ref.watch(playerServiceProvider);
    final durVal = playerService.player.duration?.inMilliseconds.toDouble();
    final dur = (durVal != null && durVal > 0) ? durVal : 1.0;

    return SliderTheme(
      data: SliderTheme.of(context).copyWith(
        trackHeight: 2,
        thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 4),
        overlayShape: const RoundSliderOverlayShape(overlayRadius: 10),
        activeTrackColor: AppTheme.primary,
        inactiveTrackColor: AppTheme.border.withValues(alpha: 0.1),
        thumbColor: AppTheme.primary,
        padding: EdgeInsets.zero,
      ),
      child: Slider(
        value: pos.clamp(0.0, dur),
        max: dur,
        onChanged: (value) {
          playerService.seek(Duration(milliseconds: value.toInt()));
        },
      ),
    );
  }
}
