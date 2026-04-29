import "package:animated_list_plus/animated_list_plus.dart";
import "package:animated_list_plus/transitions.dart";
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/download_item.dart';
import '../providers/download_diagnostics_provider.dart';
import '../services/download_service.dart';
import '../theme/app_theme.dart';
import '../widgets/download_card.dart';
import '../providers/download_provider.dart';

class DownloadsScreen extends ConsumerStatefulWidget {
  const DownloadsScreen({super.key});

  @override
  ConsumerState<DownloadsScreen> createState() => _DownloadsScreenState();
}

class _DownloadsScreenState extends ConsumerState<DownloadsScreen>
    with SingleTickerProviderStateMixin, AutomaticKeepAliveClientMixin {
  final _searchController = TextEditingController();
  late TabController _tabController;
  String _searchQuery = '';
  bool _isSelectionMode = false;
  bool _isDeletingSelection = false;
  final Set<String> _selectedDownloadIds = <String>{};

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
    // Removemos os setStates desnecessários e inscrições de stream
  }

  bool _isSelectedDownload(String downloadId) {
    return _selectedDownloadIds.contains(downloadId);
  }

  void _setSearchQuery(String query) {
    setState(() => _searchQuery = query);
  }

  void _clearSearchQuery() {
    _searchController.clear();
    _setSearchQuery('');
  }

  List<DownloadItem> _selectedItemsFrom(List<DownloadItem> allDownloads) {
    return allDownloads
        .where((item) => _selectedDownloadIds.contains(item.id))
        .toList();
  }

  String _selectionLabel(int count) {
    final plural = count == 1 ? '' : 's';
    return '$count arquivo$plural selecionado$plural';
  }

  @override
  void dispose() {
    _searchController.dispose();
    _tabController.dispose();
    super.dispose();
  }

  List<DownloadItem> _filterAndSortDownloads(
      List<DownloadItem> items, DownloadType? tabType) {
    var filtered = items.where((item) {
      if (tabType != null && item.type != tabType) return false;
      if (_searchQuery.isEmpty) return true;

      // Busca aprimorada
      final search = _searchQuery.toLowerCase();
      final titleMatch = item.title.toLowerCase().contains(search);
      final artistMatch = item.artist?.toLowerCase().contains(search) ?? false;
      final albumMatch = item.album?.toLowerCase().contains(search) ?? false;

      return titleMatch || artistMatch || albumMatch;
    }).toList();

    // Ordenação atualizada (ativos primeiro, depois por data mais recente)
    filtered.sort((a, b) {
      if (a.status == DownloadStatus.downloading &&
          b.status != DownloadStatus.downloading) {
        return -1;
      }
      if (b.status == DownloadStatus.downloading &&
          a.status != DownloadStatus.downloading) {
        return 1;
      }
      return b.createdAt.compareTo(a.createdAt);
    });

    return filtered;
  }

  void _startSelectionMode(String downloadId) {
    setState(() {
      _isSelectionMode = true;
      _selectedDownloadIds.add(downloadId);
    });
  }

  void _toggleDownloadSelection(String downloadId) {
    setState(() {
      if (_isSelectedDownload(downloadId)) {
        _selectedDownloadIds.remove(downloadId);
        if (_selectedDownloadIds.isEmpty) {
          _isSelectionMode = false;
        }
        return;
      }
      _selectedDownloadIds.add(downloadId);
    });
  }

  void _clearSelectionMode() {
    setState(() {
      _isSelectionMode = false;
      _isDeletingSelection = false;
      _selectedDownloadIds.clear();
    });
  }

  Future<void> _deleteSelectedDownloads(List<DownloadItem> allDownloads) async {
    final selectedItems = _selectedItemsFrom(allDownloads);
    if (selectedItems.isEmpty) {
      _clearSelectionMode();
      return;
    }

    final confirmed = await _confirmDeleteSelection(
      selectedItems.length,
      selectedItems.first.title,
    );
    if (confirmed != true || !mounted) return;

    setState(() {
      _isDeletingSelection = true;
    });

    final deletedCount = await _deleteDownloads(selectedItems);
    if (!mounted) return;

    _showDeleteResult(deletedCount, selectedItems.length);
    _clearSelectionMode();
  }

  Future<bool?> _confirmDeleteSelection(int count, String firstTitle) {
    final plural = count > 1;
    return showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: AppTheme.surfaceElevated,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: Text(
          plural ? 'Excluir downloads?' : 'Excluir download?',
          style: const TextStyle(
            color: AppTheme.textPrimary,
            fontWeight: FontWeight.w700,
            fontSize: 17,
          ),
        ),
        content: Text(
          plural
              ? '$count arquivos serão removidos permanentemente.'
              : '"$firstTitle" será removido permanentemente.',
          style: const TextStyle(
            color: AppTheme.textSecondary,
            fontSize: 14,
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text(
              'Cancelar',
              style: TextStyle(color: AppTheme.textSecondary),
            ),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text(
              'Excluir',
              style: TextStyle(
                color: AppTheme.error,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<int> _deleteDownloads(List<DownloadItem> items) async {
    var deletedCount = 0;
    for (final item in items) {
      try {
        await DownloadService.instance.deleteDownload(item);
        deletedCount++;
      } catch (_) {
        // Ignora falhas individuais para continuar o lote.
      }
    }
    return deletedCount;
  }

  void _showDeleteResult(int deletedCount, int totalCount) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        behavior: SnackBarBehavior.floating,
        backgroundColor:
            deletedCount == totalCount ? AppTheme.success : AppTheme.warning,
        content: Text(
          deletedCount == totalCount
              ? '$deletedCount arquivos excluidos com sucesso.'
              : 'Excluidos $deletedCount de $totalCount arquivos.',
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final downloadsAsync = ref.watch(downloadsProvider);
    final currentDownloads =
        downloadsAsync.asData?.value ?? const <DownloadItem>[];
    final selectedCount = currentDownloads
        .where((item) => _selectedDownloadIds.contains(item.id))
        .length;

    return Scaffold(
      backgroundColor: AppTheme.surface,
      bottomNavigationBar: _isSelectionMode
          ? _buildSelectionBar(
              allDownloads: currentDownloads,
              selectedCount: selectedCount,
            )
          : null,
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        elevation: 0,
        leading: _isSelectionMode
            ? IconButton(
                icon: const Icon(Icons.close, color: AppTheme.textPrimary),
                onPressed: _isDeletingSelection ? null : _clearSelectionMode,
              )
            : null,
        title: _isSelectionMode
            ? Text(
                _selectionLabel(selectedCount),
                style: const TextStyle(
                  color: AppTheme.textPrimary,
                  fontSize: 18,
                  fontWeight: FontWeight.w700,
                ),
              )
            : TextField(
                controller: _searchController,
                style: const TextStyle(color: AppTheme.textPrimary),
                decoration: InputDecoration(
                  hintText: 'Buscar nos downloads...',
                  hintStyle: const TextStyle(
                      color: AppTheme.textSecondary, fontSize: 16),
                  border: InputBorder.none,
                  suffixIcon: _searchQuery.isNotEmpty
                      ? IconButton(
                          icon: const Icon(Icons.close,
                              color: AppTheme.textSecondary),
                          onPressed: _clearSearchQuery,
                        )
                      : const Icon(Icons.search, color: AppTheme.textSecondary),
                ),
                onChanged: _setSearchQuery,
              ),
        actions: _isSelectionMode
            ? [
                IconButton(
                  tooltip: 'Excluir selecionados',
                  icon: _isDeletingSelection
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.delete_sweep_rounded,
                          color: AppTheme.error),
                  onPressed: _isDeletingSelection || selectedCount == 0
                      ? null
                      : () => _deleteSelectedDownloads(currentDownloads),
                ),
              ]
            : null,
        bottom: TabBar(
          controller: _tabController,
          indicatorColor: AppTheme.primary,
          labelColor: AppTheme.primary,
          unselectedLabelColor: AppTheme.textSecondary,
          tabs: const [
            Tab(text: 'Todos'),
            Tab(text: 'Áudio'),
            Tab(text: 'Vídeo'),
          ],
        ),
      ),
      body: downloadsAsync.when(
        data: (downloads) {
          return TabBarView(
            controller: _tabController,
            children: [
              _buildList(downloads, null),
              _buildList(downloads, DownloadType.audio),
              _buildList(downloads, DownloadType.video),
            ],
          );
        },
        loading: () => const Center(
          child: CircularProgressIndicator(color: AppTheme.primary),
        ),
        error: (err, stack) => Center(
          child: Text('Erro ao carregar downloads',
              style: const TextStyle(color: AppTheme.error)),
        ),
      ),
    );
  }

  Widget _buildList(List<DownloadItem> allItems, DownloadType? tabType) {
    final filtered = _filterAndSortDownloads(allItems, tabType);
    final showDiagnostics = tabType == null;

    if (filtered.isEmpty) {
      if (_searchQuery.isNotEmpty) {
        return _buildEmptySearchState();
      }
      if (showDiagnostics) {
        return _buildDiagnosticsOnlyEmptyState();
      }
      return _buildEmptyStateForType(tabType);
    }

    final dynamicItems = <Object>[
      if (showDiagnostics) 'diagnostics',
      ...filtered,
    ];

    return RefreshIndicator(
      onRefresh: () async {
        /* Adicionar recarregamento futuramente se necessario */
      },
      color: AppTheme.primary,
      child: ImplicitlyAnimatedList<Object>(
        padding: const EdgeInsets.symmetric(vertical: 8),
        items: dynamicItems,
        areItemsTheSame: (a, b) {
          if (a is String && b is String) return a == b;
          if (a is DownloadItem && b is DownloadItem) return a.id == b.id;
          return false;
        },
        itemBuilder: (context, animation, item, index) {
          return SizeFadeTransition(
            sizeFraction: 0.7,
            curve: Curves.easeInOut,
            animation: animation,
            child: Builder(builder: (context) {
              if (item is String) {
                return _buildDiagnosticsPanel();
              }
              final dl = item as DownloadItem;
              return DownloadCard(
                key: ValueKey(item.id),
                item: dl,
                isSelectionMode: _isSelectionMode,
                isSelected: _isSelectedDownload(dl.id),
                onLongPress: () => _startSelectionMode(dl.id),
                onTapSelection: () => _toggleDownloadSelection(dl.id),
              );
            }),
          );
        },
      ),
    );
  }

  Widget _buildSelectionBar({
    required List<DownloadItem> allDownloads,
    required int selectedCount,
  }) {
    return SafeArea(
      top: false,
      child: Container(
        padding: const EdgeInsets.fromLTRB(12, 10, 12, 12),
        decoration: BoxDecoration(
          color: AppTheme.surfaceElevated,
          border: Border(
            top: BorderSide(color: AppTheme.border.withValues(alpha: 0.6)),
          ),
        ),
        child: Row(
          children: [
            Expanded(
              child: Text(
                _selectionLabel(selectedCount),
                style: const TextStyle(
                  color: AppTheme.textPrimary,
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
            TextButton(
              onPressed: _isDeletingSelection ? null : _clearSelectionMode,
              child: const Text('Cancelar'),
            ),
            const SizedBox(width: 8),
            ElevatedButton.icon(
              onPressed: _isDeletingSelection || selectedCount == 0
                  ? null
                  : () => _deleteSelectedDownloads(allDownloads),
              icon: _isDeletingSelection
                  ? const SizedBox(
                      width: 14,
                      height: 14,
                      child: CircularProgressIndicator(
                        color: Colors.white,
                        strokeWidth: 2,
                      ),
                    )
                  : const Icon(Icons.delete_outline_rounded, size: 16),
              label: const Text('Excluir em lote'),
              style: ElevatedButton.styleFrom(
                backgroundColor: AppTheme.error,
                foregroundColor: Colors.white,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDiagnosticsOnlyEmptyState() {
    return ListView(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 24),
      children: [
        _buildDiagnosticsPanel(),
        const SizedBox(height: 12),
        Container(
          padding: const EdgeInsets.all(24),
          decoration: BoxDecoration(
            color: AppTheme.card,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppTheme.border),
          ),
          child: Column(
            children: [
              Icon(
                Icons.download_rounded,
                size: 64,
                color: AppTheme.primary.withValues(alpha: 0.25),
              ),
              const SizedBox(height: 16),
              const Text(
                'Nenhum download ainda',
                style: TextStyle(
                  color: AppTheme.textPrimary,
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 8),
              const Text(
                'Use a aba Buscar para encontrar videos',
                style: TextStyle(color: AppTheme.textSecondary, fontSize: 14),
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildDiagnosticsPanel() {
    final diagnosticsAsync = ref.watch(downloadDiagnosticsProvider);

    return diagnosticsAsync.when(
      loading: () => Container(
        margin: const EdgeInsets.fromLTRB(12, 0, 12, 12),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppTheme.card,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: AppTheme.border),
        ),
        child: const Row(
          children: [
            SizedBox(
              width: 16,
              height: 16,
              child: CircularProgressIndicator(strokeWidth: 2),
            ),
            SizedBox(width: 12),
            Text(
              'Carregando diagnostico...',
              style: TextStyle(color: AppTheme.textSecondary),
            ),
          ],
        ),
      ),
      error: (err, stack) => Container(
        margin: const EdgeInsets.fromLTRB(12, 0, 12, 12),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppTheme.card,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: AppTheme.border),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Diagnostico',
              style: TextStyle(
                color: AppTheme.textPrimary,
                fontSize: 16,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'Falha ao carregar: $err',
              style: const TextStyle(color: AppTheme.error),
            ),
          ],
        ),
      ),
      data: (diagnostics) {
        final notifier = ref.read(downloadDiagnosticsProvider.notifier);
        final currentVersion =
            diagnostics.currentYtDlpVersion ?? 'desconhecida';
        final latestVersion = diagnostics.latestYtDlpVersion;
        final versionText = latestVersion == null
            ? 'yt-dlp: $currentVersion'
            : 'yt-dlp: $currentVersion -> $latestVersion';

        return Container(
          margin: const EdgeInsets.fromLTRB(12, 0, 12, 12),
          decoration: BoxDecoration(
            color: AppTheme.card,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppTheme.border),
          ),
          child: Column(
            children: [
              InkWell(
                borderRadius: BorderRadius.circular(16),
                onTap: () => notifier.setExpanded(!diagnostics.isExpanded),
                child: Padding(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                  child: Row(
                    children: [
                      const Icon(Icons.medical_information_rounded,
                          color: AppTheme.primary, size: 20),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'Diagnostico',
                              style: TextStyle(
                                color: AppTheme.textPrimary,
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              versionText,
                              style: const TextStyle(
                                color: AppTheme.textSecondary,
                                fontSize: 12,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ],
                        ),
                      ),
                      if (diagnostics.updateAvailable)
                        Container(
                          margin: const EdgeInsets.only(right: 8),
                          padding: const EdgeInsets.symmetric(
                              horizontal: 8, vertical: 4),
                          decoration: BoxDecoration(
                            color: AppTheme.primary.withValues(alpha: 0.2),
                            borderRadius: BorderRadius.circular(999),
                          ),
                          child: const Text(
                            'Atualizacao',
                            style: TextStyle(
                              color: AppTheme.primary,
                              fontSize: 11,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                      Icon(
                        diagnostics.isExpanded
                            ? Icons.expand_less_rounded
                            : Icons.expand_more_rounded,
                        color: AppTheme.textSecondary,
                      ),
                    ],
                  ),
                ),
              ),
              if (diagnostics.isExpanded)
                Padding(
                  padding: const EdgeInsets.fromLTRB(14, 0, 14, 14),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Divider(color: AppTheme.border),
                      const SizedBox(height: 8),
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: AppTheme.surfaceElevated,
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'Status do yt-dlp',
                              style: TextStyle(
                                color: AppTheme.textPrimary,
                                fontSize: 13,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              versionText,
                              style: const TextStyle(
                                color: AppTheme.textSecondary,
                                fontSize: 12,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              'Ultima checagem: ${_formatLastChecked(diagnostics.lastCheckedAt)}',
                              style: const TextStyle(
                                color: AppTheme.textTertiary,
                                fontSize: 11,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 10),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          OutlinedButton.icon(
                            onPressed: diagnostics.isBusy
                                ? null
                                : () => notifier.checkYtDlpUpdate(
                                    forceRemote: true),
                            icon: diagnostics.isCheckingYtDlp
                                ? const SizedBox(
                                    width: 14,
                                    height: 14,
                                    child: CircularProgressIndicator(
                                        strokeWidth: 2),
                                  )
                                : const Icon(Icons.refresh_rounded, size: 16),
                            label: const Text('Verificar yt-dlp'),
                            style: OutlinedButton.styleFrom(
                              foregroundColor: AppTheme.textPrimary,
                              side: const BorderSide(color: AppTheme.border),
                              padding: const EdgeInsets.symmetric(
                                  horizontal: 14, vertical: 10),
                            ),
                          ),
                          ElevatedButton.icon(
                            onPressed: diagnostics.isBusy
                                ? null
                                : notifier.updateYtDlpNow,
                            icon: diagnostics.isUpdatingYtDlp
                                ? const SizedBox(
                                    width: 14,
                                    height: 14,
                                    child: CircularProgressIndicator(
                                      color: Colors.white,
                                      strokeWidth: 2,
                                    ),
                                  )
                                : const Icon(Icons.system_update_alt_rounded,
                                    size: 16),
                            label: const Text('Atualizar agora'),
                            style: ElevatedButton.styleFrom(
                              padding: const EdgeInsets.symmetric(
                                  horizontal: 14, vertical: 10),
                            ),
                          ),
                          OutlinedButton.icon(
                            onPressed: diagnostics.isBusy
                                ? null
                                : notifier.addMissingArtworkBatch,
                            icon: diagnostics.isRepairingMetadata
                                ? const SizedBox(
                                    width: 14,
                                    height: 14,
                                    child: CircularProgressIndicator(
                                        strokeWidth: 2),
                                  )
                                : const Icon(Icons.image_search_rounded,
                                    size: 16),
                            label: const Text('Baixar capas em lote'),
                            style: OutlinedButton.styleFrom(
                              foregroundColor: AppTheme.textPrimary,
                              side: const BorderSide(color: AppTheme.border),
                              padding: const EdgeInsets.symmetric(
                                  horizontal: 14, vertical: 10),
                            ),
                          ),
                        ],
                      ),
                      if (diagnostics.isRepairingMetadata) ...[
                        const SizedBox(height: 10),
                        _buildBatchProgress(diagnostics),
                      ],
                      const SizedBox(height: 12),
                      Container(
                        decoration: BoxDecoration(
                          color: AppTheme.surfaceElevated,
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: SwitchListTile.adaptive(
                          contentPadding:
                              const EdgeInsets.symmetric(horizontal: 10),
                          value: diagnostics.autoExportEnabled,
                          onChanged: diagnostics.isSavingAutoExport
                              ? null
                              : notifier.setAutoExportEnabled,
                          activeThumbColor: AppTheme.primary,
                          title: const Text(
                            'Auto-exportar ao concluir',
                            style: TextStyle(
                              color: AppTheme.textPrimary,
                              fontWeight: FontWeight.w600,
                              fontSize: 13,
                            ),
                          ),
                          subtitle: const Text(
                            'Exporta automaticamente para Music/Movies.',
                            style: TextStyle(
                              color: AppTheme.textSecondary,
                              fontSize: 12,
                            ),
                          ),
                        ),
                      ),
                      if (diagnostics.lastMessage != null) ...[
                        const SizedBox(height: 8),
                        Container(
                          width: double.infinity,
                          padding: const EdgeInsets.all(10),
                          decoration: BoxDecoration(
                            color: diagnostics.lastMessageIsError
                                ? AppTheme.error.withValues(alpha: 0.14)
                                : AppTheme.primary.withValues(alpha: 0.14),
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: Row(
                            children: [
                              Icon(
                                diagnostics.lastMessageIsError
                                    ? Icons.error_outline_rounded
                                    : Icons.check_circle_outline_rounded,
                                size: 16,
                                color: diagnostics.lastMessageIsError
                                    ? AppTheme.error
                                    : AppTheme.primary,
                              ),
                              const SizedBox(width: 8),
                              Expanded(
                                child: Text(
                                  diagnostics.lastMessage!,
                                  style: TextStyle(
                                    color: diagnostics.lastMessageIsError
                                        ? AppTheme.error
                                        : AppTheme.primary,
                                    fontSize: 12,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                      const SizedBox(height: 12),
                      _buildDiagnosticsSummaryCards(diagnostics),
                      const SizedBox(height: 10),
                      _buildDiagnosticsMapSection(
                        'Falhas por motivo',
                        diagnostics.telemetry.byReason,
                      ),
                      const SizedBox(height: 10),
                      _buildDiagnosticsMapSection(
                        'Historico por dia',
                        diagnostics.telemetry.byDay,
                      ),
                    ],
                  ),
                ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildDiagnosticsSummaryCards(DownloadDiagnosticsState diagnostics) {
    return Row(
      children: [
        Expanded(
          child: _buildDiagnosticsSummaryCard(
            icon: Icons.report_problem_outlined,
            title: 'Falhas',
            value: diagnostics.telemetry.total.toString(),
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: _buildDiagnosticsSummaryCard(
            icon: Icons.rule_folder_outlined,
            title: 'Ultimo motivo',
            value: _prettifyLabel(diagnostics.telemetry.lastReasonKey ?? '-'),
          ),
        ),
      ],
    );
  }

  Widget _buildDiagnosticsSummaryCard({
    required IconData icon,
    required String title,
    required String value,
  }) {
    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: AppTheme.surfaceElevated,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppTheme.border.withValues(alpha: 0.6)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 15, color: AppTheme.textSecondary),
          const SizedBox(height: 6),
          Text(
            title,
            style: const TextStyle(
              color: AppTheme.textSecondary,
              fontSize: 11,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            value,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              color: AppTheme.textPrimary,
              fontSize: 12,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildBatchProgress(DownloadDiagnosticsState diagnostics) {
    final hasTotal = diagnostics.repairTotal > 0;
    var processed = diagnostics.repairProcessed;
    if (processed < 0) processed = 0;
    if (hasTotal && processed > diagnostics.repairTotal) {
      processed = diagnostics.repairTotal;
    }

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: AppTheme.surfaceElevated,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Progresso do lote de capas',
            style: TextStyle(
              color: AppTheme.textPrimary,
              fontSize: 12,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 8),
          LinearProgressIndicator(
            value: hasTotal ? (processed / diagnostics.repairTotal) : null,
            backgroundColor: AppTheme.surface,
            color: AppTheme.primary.withValues(alpha: 0.8),
            minHeight: 5,
            borderRadius: BorderRadius.circular(99),
          ),
          const SizedBox(height: 6),
          Text(
            hasTotal
                ? '$processed de ${diagnostics.repairTotal} processados'
                : 'Preparando lote...',
            style: const TextStyle(
              color: AppTheme.textSecondary,
              fontSize: 11,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDiagnosticsMapSection(String title, Map<String, int> data) {
    final entries = data.entries.take(5).toList();
    final maxValue = entries.isEmpty
        ? 1
        : entries
            .map((entry) => entry.value)
            .reduce((value, element) => value > element ? value : element);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: const TextStyle(
            color: AppTheme.textPrimary,
            fontSize: 13,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 6),
        if (entries.isEmpty)
          const Text(
            'Sem dados no momento.',
            style: TextStyle(color: AppTheme.textSecondary, fontSize: 12),
          ),
        ...entries.map(
          (entry) => Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        _prettifyLabel(entry.key),
                        style: const TextStyle(
                          color: AppTheme.textSecondary,
                          fontSize: 12,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    Text(
                      entry.value.toString(),
                      style: const TextStyle(
                        color: AppTheme.textPrimary,
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                LinearProgressIndicator(
                  value: entry.value / maxValue,
                  backgroundColor: AppTheme.surfaceElevated,
                  color: AppTheme.primary.withValues(alpha: 0.7),
                  minHeight: 4,
                  borderRadius: BorderRadius.circular(99),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  String _formatLastChecked(DateTime? dateTime) {
    if (dateTime == null) return 'nunca';
    final hour = dateTime.hour.toString().padLeft(2, '0');
    final minute = dateTime.minute.toString().padLeft(2, '0');
    final day = dateTime.day.toString().padLeft(2, '0');
    final month = dateTime.month.toString().padLeft(2, '0');
    return '$day/$month $hour:$minute';
  }

  String _prettifyLabel(String label) {
    if (label.isEmpty) return '-';

    final replaced = label.replaceAll('_', ' ').trim();
    if (replaced.isEmpty) return '-';

    return replaced[0].toUpperCase() + replaced.substring(1);
  }

  Widget _buildEmptySearchState() {
    return const Center(
      child: Text(
        'Nenhum resultado encontrado.',
        style: TextStyle(color: AppTheme.textSecondary, fontSize: 16),
      ),
    );
  }

  Widget _buildEmptyStateForType(DownloadType? type) {
    final typeName = type == DownloadType.audio ? 'áudio' : 'vídeo';
    return Center(
      child: Text(
        'Nenhum download de $typeName ainda.',
        style: TextStyle(color: AppTheme.textSecondary, fontSize: 16),
      ),
    );
  }
}
