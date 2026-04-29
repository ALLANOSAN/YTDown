import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../services/download_service.dart';
import '../utils/video_info_handler.dart';
import '../providers/browser_provider.dart';
import '../theme/app_theme.dart';
import 'dart:async';

class BrowserScreen extends ConsumerStatefulWidget {
  const BrowserScreen({super.key});

  @override
  ConsumerState<BrowserScreen> createState() => _BrowserScreenState();
}

class _BrowserScreenState extends ConsumerState<BrowserScreen> {
  InAppWebViewController? _webViewController;
  final TextEditingController _urlController = TextEditingController();

  BrowserState get _browserState => ref.read(browserProvider);

  BrowserNotifier get _browserNotifier => ref.read(browserProvider.notifier);

  void _loadUrl(String url) {
    _webViewController?.loadUrl(urlRequest: URLRequest(url: WebUri(url)));
  }

  void _showBrowserErrorSnackBar(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: AppTheme.error,
      ),
    );
  }

  bool _isGoogleAuthUrl(String url) {
    final lowerUrl = url.toLowerCase();
    return lowerUrl.contains('accounts.google.com') ||
        lowerUrl.contains('signin') && lowerUrl.contains('youtube.com');
  }

  bool _shouldIgnoreHttpError({
    required String url,
    required int statusCode,
  }) {
    // Google auth endpoints often respond with 403 inside WebView.
    if (statusCode == 403 && _isGoogleAuthUrl(url)) {
      return true;
    }
    return false;
  }

  String _buildHttpErrorMessage(
      {required int statusCode, required String url}) {
    final host = Uri.tryParse(url)?.host;
    if (host != null && host.isNotEmpty) {
      return 'Erro HTTP $statusCode em $host';
    }
    return 'Erro HTTP $statusCode ao carregar a página';
  }

  void _checkUrl(String url) {
    if (url == _browserState.currentUrl) return;
    _browserNotifier.setUrl(url);
    _urlController.text = url;
  }

  Future<void> _handleDownload() async {
    final browserState = _browserState;
    if (!browserState.isYoutube || browserState.isLoading) return;

    _browserNotifier.setLoading(true);
    HapticFeedback.mediumImpact();

    try {
      final info = await DownloadService.instance
          .fetchVideoInfo(browserState.currentUrl);
      if (!mounted) return;

      _browserNotifier.setLoading(false);

      // Usa o handler centralizado para evitar duplicação
      await VideoInfoHandler.handleVideoInfo(
          context, info, browserState.currentUrl);
    } catch (e) {
      if (!mounted) return;
      _browserNotifier.setLoading(false);
      _showBrowserErrorSnackBar('Erro ao processar link do YouTube');
    }
  }

  @override
  Widget build(BuildContext context) {
    final browserState = ref.watch(browserProvider);

    return Scaffold(
      backgroundColor: AppTheme.surface,
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        elevation: 0,
        titleSpacing: 0,
        title: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: Container(
            height: 40,
            decoration: BoxDecoration(
              color: AppTheme.card,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: AppTheme.border, width: 0.5),
            ),
            child: TextField(
              controller: _urlController,
              onSubmitted: (value) {
                var url = value.trim();
                if (!url.startsWith('http')) url = 'https://$url';
                _loadUrl(url);
              },
              style: const TextStyle(color: AppTheme.textPrimary, fontSize: 13),
              decoration: const InputDecoration(
                hintText: 'Pesquisar ou digitar URL',
                hintStyle:
                    TextStyle(color: AppTheme.textSecondary, fontSize: 13),
                prefixIcon: Icon(Icons.public_rounded,
                    size: 18, color: AppTheme.textSecondary),
                border: InputBorder.none,
                contentPadding: EdgeInsets.symmetric(vertical: 10),
              ),
            ),
          ),
        ),
        actions: [
          IconButton(
            icon:
                const Icon(Icons.refresh_rounded, color: AppTheme.textPrimary),
            onPressed: () => _webViewController?.reload(),
          ),
        ],
      ),
      body: Column(
        children: [
          if (browserState.progress < 1.0)
            LinearProgressIndicator(
              value: browserState.progress,
              backgroundColor: Colors.transparent,
              valueColor: const AlwaysStoppedAnimation<Color>(AppTheme.primary),
              minHeight: 2,
            ),
          Expanded(
            child: InAppWebView(
              initialUrlRequest: URLRequest(
                  url: WebUri(_browserState
                      .currentUrl)), // read here to prevent recreation of webview
              initialSettings: InAppWebViewSettings(
                javaScriptEnabled: true,
                domStorageEnabled: true,
                databaseEnabled: true,
                useShouldOverrideUrlLoading: false,
                mediaPlaybackRequiresUserGesture: false,
                allowsInlineMediaPlayback: true,
                iframeAllow: "camera; microphone",
                iframeAllowFullscreen: true,
                // User-Agent Mobile para compatibilidade com YouTube
                userAgent:
                    "Mozilla/5.0 (Linux; Android 10; SM-G960F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
              ),
              onWebViewCreated: (controller) {
                _webViewController = controller;
                _urlController.text = _browserState.currentUrl;
              },
              onProgressChanged: (controller, progress) {
                if (!mounted) return;
                _browserNotifier.setProgress(progress / 100);
              },
              onLoadStart: (controller, url) {
                final currentState = _browserState;
                if (url != null && url.toString() != currentState.currentUrl) {
                  _browserNotifier.setInitialLoad(false);
                }
              },
              onLoadStop: (controller, url) {
                if (!mounted) return;
                _browserNotifier.setInitialLoad(false);
                if (url != null) _checkUrl(url.toString());
              },
              onUpdateVisitedHistory: (controller, url, isReload) {
                if (!mounted) return;
                if (url != null) _checkUrl(url.toString());
              },
              onReceivedError: (controller, request, error) {
                if (!mounted) return;

                // Ignora erros de sub-frames
                if (request.isForMainFrame != true) return;

                final url = request.url.toString();
                final isYouTube =
                    url.contains('youtube.com') || url.contains('youtu.be');

                // Se for erro de conexão no YouTube, ignora silenciosamente
                if (isYouTube &&
                    error.type == WebResourceErrorType.CANNOT_CONNECT_TO_HOST) {
                  debugPrint('⚠️ YouTube connection refused (ignoring): $url');
                  return;
                }

                final currentState = _browserState;

                // Ignora erros na carga inicial
                if (currentState.isInitialLoad) {
                  _browserNotifier.setInitialLoad(false);
                  return;
                }

                // Previne mostrar o mesmo erro múltiplas vezes
                if (currentState.hasShownError) return;

                // Mostra erro apenas para erros reais
                _browserNotifier.setHasShownError(true);
                ScaffoldMessenger.of(context)
                    .showSnackBar(
                      SnackBar(
                        content: Text(
                            'Erro ao carregar página: ${error.description}'),
                        backgroundColor: Colors.red,
                        duration: const Duration(seconds: 3),
                        behavior: SnackBarBehavior.floating,
                      ),
                    )
                    .closed
                    .then((_) {
                  if (mounted) _browserNotifier.setHasShownError(false);
                });
              },
              onReceivedHttpError: (controller, request, errorResponse) {
                if (!mounted) return;

                // Ignore sub-frame resources and react only to main page loads.
                if (request.isForMainFrame != true) return;

                final url = request.url.toString();
                final statusCode = errorResponse.statusCode;
                if (statusCode == null) return;

                if (_shouldIgnoreHttpError(url: url, statusCode: statusCode)) {
                  debugPrint(
                    '⚠️ Ignoring HTTP $statusCode from Google auth endpoint: $url',
                  );
                  return;
                }

                final currentState = _browserState;

                if (currentState.isInitialLoad) {
                  _browserNotifier.setInitialLoad(false);
                  return;
                }

                if (currentState.hasShownError) return;

                _browserNotifier.setHasShownError(true);
                ScaffoldMessenger.of(context)
                    .showSnackBar(
                      SnackBar(
                        content: Text(
                          _buildHttpErrorMessage(
                            statusCode: statusCode,
                            url: url,
                          ),
                        ),
                        backgroundColor: Colors.orange,
                        duration: const Duration(seconds: 3),
                        behavior: SnackBarBehavior.floating,
                      ),
                    )
                    .closed
                    .then((_) {
                  if (mounted) _browserNotifier.setHasShownError(false);
                });
              },
            ),
          ),
        ],
      ),
      bottomNavigationBar: Container(
        height: 50,
        decoration: BoxDecoration(
          color: AppTheme.surface,
          border: Border(top: BorderSide(color: AppTheme.border, width: 0.5)),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: [
            IconButton(
              icon: const Icon(Icons.arrow_back_ios_new_rounded, size: 20),
              onPressed: () => _webViewController?.goBack(),
            ),
            IconButton(
              icon: const Icon(Icons.arrow_forward_ios_rounded, size: 20),
              onPressed: () => _webViewController?.goForward(),
            ),
            IconButton(
              icon: const Icon(Icons.home_rounded),
              onPressed: () => _loadUrl('https://www.youtube.com'),
            ),
          ],
        ),
      ),
      floatingActionButton: browserState.isYoutube
          ? FloatingActionButton.extended(
              onPressed: browserState.isLoading ? null : _handleDownload,
              backgroundColor: AppTheme.primary,
              icon: browserState.isLoading
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                          color: Colors.white, strokeWidth: 2))
                  : const Icon(Icons.download_rounded, color: Colors.white),
              label: const Text('Baixar',
                  style: TextStyle(
                      color: Colors.white, fontWeight: FontWeight.bold)),
            )
          : null,
    );
  }
}
