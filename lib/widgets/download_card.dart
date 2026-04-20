import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/download_provider.dart';
import 'package:intl/intl.dart';
import 'package:open_file/open_file.dart';
import 'package:share_plus/share_plus.dart';
import '../models/download_item.dart';
import '../services/download_service.dart';
import '../theme/app_theme.dart';

class DownloadCard extends ConsumerWidget {
  static final DateFormat _compactDateFormat = DateFormat('dd/MM HH:mm');

  final DownloadItem item;
  final VoidCallback? onDeleted;
  final Future<void> Function(DownloadItem item)? onExport;
  final bool isSelected;
  final bool isSelectionMode;
  final VoidCallback? onLongPress;
  final VoidCallback? onTapSelection;

  const DownloadCard({
    super.key,
    required this.item,
    this.onDeleted,
    this.onExport,
    this.isSelected = false,
    this.isSelectionMode = false,
    this.onLongPress,
    this.onTapSelection,
  });

  bool _isCompleted() => item.status == DownloadStatus.completed;
  bool _isDownloading() => item.status == DownloadStatus.downloading;
  bool _isFailed() => item.status == DownloadStatus.failed;

  String? _trimToNull(String value) {
    final trimmed = value.trim();
    if (trimmed.isEmpty) {
      return null;
    }
    return trimmed;
  }

  Color _exportStatusColor() {
    switch (item.exportStatus) {
      case ExportStatus.exported:
        return AppTheme.success;
      case ExportStatus.failed:
        return AppTheme.warning;
      case ExportStatus.pending:
        return AppTheme.textTertiary;
    }
  }

  IconData _exportStatusIcon() {
    switch (item.exportStatus) {
      case ExportStatus.exported:
        return Icons.check_circle_rounded;
      case ExportStatus.failed:
        return Icons.warning_amber_rounded;
      case ExportStatus.pending:
        return Icons.lock_outline_rounded;
    }
  }

  void _showMetadataUpdateFeedback(
    BuildContext context,
    Map<String, dynamic> result,
  ) {
    final warning = result['warning']?.toString();
    final hasWarning = warning != null && warning.isNotEmpty;
    final message = hasWarning ? warning : 'Metadados atualizados com sucesso.';

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: hasWarning ? AppTheme.warning : AppTheme.success,
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  String _formatSize(int bytes) {
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(0)} KB';
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }

  Future<void> _delete(BuildContext context) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: AppTheme.surfaceElevated,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Text('Excluir download?',
            style: TextStyle(
              color: AppTheme.textPrimary,
              fontWeight: FontWeight.w700,
              fontSize: 17,
            )),
        content: Text(
          '"${item.title}" será removido permanentemente.',
          style: const TextStyle(
            color: AppTheme.textSecondary,
            fontSize: 14,
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancelar',
                style: TextStyle(color: AppTheme.textSecondary)),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Excluir',
                style: TextStyle(
                  color: AppTheme.error,
                  fontWeight: FontWeight.w700,
                )),
          ),
        ],
      ),
    );

    if (confirm == true && context.mounted) {
      final downloadService = DownloadService.instance;
      await downloadService.deleteDownload(item);
      onDeleted?.call();
    }
  }

  Future<void> _editMetadata(BuildContext context) async {
    final titleController = TextEditingController(text: item.title);
    final artistController = TextEditingController(text: item.artist ?? '');
    final albumController = TextEditingController(text: item.album ?? '');
    final artistImageController =
        TextEditingController(text: item.artistImageUrl ?? '');
    final albumImageController =
        TextEditingController(text: item.albumImageUrl ?? '');

    // FocusNodes explicitos para evitar que o teclado feche ao reconstruir
    final titleFocus = FocusNode();
    final artistFocus = FocusNode();
    final albumFocus = FocusNode();
    final artistImageFocus = FocusNode();
    final albumImageFocus = FocusNode();

    var isSaving = false;
    String? errorMessage;

    final edited = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (dialogContext) {
        // Focar no primeiro campo apos o dialogo montar
        final widgetsBinding = WidgetsBinding.instance;
        widgetsBinding.addPostFrameCallback((_) {
          titleFocus.requestFocus();
        });

        return StatefulBuilder(
          builder: (context, setDialogState) {
            return AlertDialog(
              backgroundColor: AppTheme.surfaceElevated,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(20),
              ),
              title: const Text(
                'Editar metadados',
                style: TextStyle(
                  color: AppTheme.textPrimary,
                  fontWeight: FontWeight.w700,
                  fontSize: 17,
                ),
              ),
              content: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    TextField(
                      controller: titleController,
                      focusNode: titleFocus,
                      style: const TextStyle(color: AppTheme.textPrimary),
                      textInputAction: TextInputAction.next,
                      decoration: const InputDecoration(
                        labelText: 'Titulo',
                        labelStyle: TextStyle(color: AppTheme.textSecondary),
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: artistController,
                      focusNode: artistFocus,
                      style: const TextStyle(color: AppTheme.textPrimary),
                      textInputAction: TextInputAction.next,
                      decoration: const InputDecoration(
                        labelText: 'Artista',
                        labelStyle: TextStyle(color: AppTheme.textSecondary),
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: albumController,
                      focusNode: albumFocus,
                      style: const TextStyle(color: AppTheme.textPrimary),
                      textInputAction: TextInputAction.next,
                      decoration: const InputDecoration(
                        labelText: 'Album',
                        labelStyle: TextStyle(color: AppTheme.textSecondary),
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: artistImageController,
                      focusNode: artistImageFocus,
                      style: const TextStyle(color: AppTheme.textPrimary),
                      textInputAction: TextInputAction.next,
                      keyboardType: TextInputType.url,
                      decoration: const InputDecoration(
                        labelText: 'URL capa do artista/banda',
                        labelStyle: TextStyle(color: AppTheme.textSecondary),
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: albumImageController,
                      focusNode: albumImageFocus,
                      style: const TextStyle(color: AppTheme.textPrimary),
                      textInputAction: TextInputAction.done,
                      keyboardType: TextInputType.url,
                      decoration: const InputDecoration(
                        labelText: 'URL capa do album',
                        labelStyle: TextStyle(color: AppTheme.textSecondary),
                      ),
                    ),
                    if (errorMessage != null) ...[
                      const SizedBox(height: 12),
                      Text(
                        errorMessage!,
                        style: const TextStyle(
                          color: AppTheme.error,
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              actions: [
                TextButton(
                  onPressed: isSaving
                      ? null
                      : () => Navigator.pop(dialogContext, null),
                  child: const Text(
                    'Cancelar',
                    style: TextStyle(color: AppTheme.textSecondary),
                  ),
                ),
                ElevatedButton(
                  onPressed: isSaving
                      ? null
                      : () async {
                          final trimmedTitle = titleController.text.trim();
                          if (trimmedTitle.isEmpty) {
                            setDialogState(() {
                              errorMessage = 'Titulo nao pode ficar vazio.';
                            });
                            return;
                          }

                          setDialogState(() {
                            isSaving = true;
                            errorMessage = null;
                          });

                          final downloadService = DownloadService.instance;
                          final result =
                              await downloadService.rewriteDownloadMetadata(
                            downloadId: item.id,
                            title: trimmedTitle,
                            artist: _trimToNull(artistController.text),
                            album: _trimToNull(albumController.text),
                            artistImageUrl:
                                _trimToNull(artistImageController.text),
                            albumImageUrl:
                                _trimToNull(albumImageController.text),
                          );

                          if (result['success'] == true) {
                            if (dialogContext.mounted) {
                              Navigator.pop(
                                dialogContext,
                                Map<String, dynamic>.from(result),
                              );
                            }
                            return;
                          }

                          setDialogState(() {
                            isSaving = false;
                            errorMessage = result['error']?.toString() ??
                                'Falha ao salvar metadados.';
                          });
                        },
                  child: isSaving
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(
                            color: Colors.white,
                            strokeWidth: 2,
                          ),
                        )
                      : const Text('Salvar'),
                ),
              ],
            );
          },
        );
      },
    );

    // Limpar FocusNodes apos fechar dialogo
    titleFocus.dispose();
    artistFocus.dispose();
    albumFocus.dispose();
    artistImageFocus.dispose();
    albumImageFocus.dispose();

    titleController.dispose();
    artistController.dispose();
    albumController.dispose();
    artistImageController.dispose();
    albumImageController.dispose();

    if (edited != null && edited['success'] == true && context.mounted) {
      _showMetadataUpdateFeedback(context, edited);
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return RepaintBoundary(
      child: Semantics(
        container: true,
        label: 'Download ${item.title}',
        value: item.statusLabel,
        button: _isCompleted(),
        hint: _isCompleted() ? 'Toque para abrir o arquivo baixado' : null,
        child: Tooltip(
          message: _isCompleted()
              ? 'Abrir arquivo baixado'
              : 'Status do download: ${item.statusLabel}',
          child: GestureDetector(
            onTap: isSelectionMode
                ? onTapSelection
                : (_isCompleted()
                    ? () => OpenFile.open(item.outputPath)
                    : null),
            onLongPress: onLongPress,
            child: Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: _isDownloading()
                      ? [AppTheme.card, AppTheme.surfaceElevated]
                      : const [AppTheme.card, AppTheme.card],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(18),
                border: Border.all(
                  color: isSelected
                      ? AppTheme.primary
                      : _isDownloading()
                          ? AppTheme.primary.withValues(alpha: 0.3)
                          : AppTheme.border,
                  width: (isSelected || _isDownloading()) ? 1.5 : 1,
                ),
                boxShadow: (isSelected || _isDownloading())
                    ? [
                        BoxShadow(
                          color: AppTheme.primary.withValues(alpha: 0.15),
                          blurRadius: 12,
                          offset: const Offset(0, 4),
                        ),
                      ]
                    : null,
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildHeader(context),
                  if (_isDownloading()) ...[
                    const SizedBox(height: 14),
                    _buildProgressBar(ref.watch(itemProgressProvider(item))),
                  ],
                  if (_isFailed() && item.errorMessage != null) ...[
                    const SizedBox(height: 12),
                    _buildError(),
                  ],
                  if (_isCompleted()) ...[
                    const SizedBox(height: 12),
                    _buildFooter(),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    return Row(
      children: [
        _buildTypeIcon(),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                item.title,
                style: const TextStyle(
                  color: AppTheme.textPrimary,
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  height: 1.3,
                ),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 6),
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 3,
                    ),
                    decoration: BoxDecoration(
                      color: item.type == DownloadType.audio
                          ? AppTheme.audioAccent.withValues(alpha: 0.15)
                          : AppTheme.primary.withValues(alpha: 0.15),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      item.format.toUpperCase(),
                      style: TextStyle(
                        color: item.type == DownloadType.audio
                            ? AppTheme.audioAccent
                            : AppTheme.primary,
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        letterSpacing: 0.5,
                      ),
                    ),
                  ),
                  const SizedBox(width: 6),
                  Text(
                    '·',
                    style: TextStyle(
                      color: AppTheme.textTertiary,
                      fontSize: 14,
                    ),
                  ),
                  const SizedBox(width: 6),
                  Text(
                    item.qualityLabel,
                    style: const TextStyle(
                      color: AppTheme.textSecondary,
                      fontSize: 12,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(width: 8),
        if (isSelectionMode) _buildSelectionIndicator(),
        if (!isSelectionMode) ...[
          _buildStatusIcon(),
          _buildMenu(context),
        ],
      ],
    );
  }

  Widget _buildSelectionIndicator() {
    return Container(
      width: 24,
      height: 24,
      decoration: BoxDecoration(
        color: isSelected ? AppTheme.primary : Colors.transparent,
        shape: BoxShape.circle,
        border: Border.all(
          color: isSelected ? AppTheme.primary : AppTheme.textTertiary,
          width: 2,
        ),
      ),
      child: isSelected
          ? const Icon(Icons.check, color: Colors.white, size: 16)
          : null,
    );
  }

  Widget _buildTypeIcon() {
    final isAudio = item.type == DownloadType.audio;
    return Container(
      width: 44,
      height: 44,
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: isAudio
              ? [
                  AppTheme.audioAccent.withValues(alpha: 0.2),
                  AppTheme.audioAccent.withValues(alpha: 0.1)
                ]
              : [
                  AppTheme.primary.withValues(alpha: 0.2),
                  AppTheme.primary.withValues(alpha: 0.1)
                ],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Icon(
        isAudio ? Icons.music_note_rounded : Icons.videocam_rounded,
        color: isAudio ? AppTheme.audioAccent : AppTheme.primary,
        size: 22,
      ),
    );
  }

  Widget _buildStatusIcon() {
    switch (item.status) {
      case DownloadStatus.completed:
        return Container(
          padding: const EdgeInsets.all(6),
          decoration: BoxDecoration(
            color: AppTheme.success.withValues(alpha: 0.15),
            borderRadius: BorderRadius.circular(8),
          ),
          child: const Icon(
            Icons.check_circle_rounded,
            color: AppTheme.success,
            size: 18,
          ),
        );
      case DownloadStatus.downloading:
        return Container(
          padding: const EdgeInsets.all(6),
          decoration: BoxDecoration(
            color: AppTheme.primary.withValues(alpha: 0.15),
            borderRadius: BorderRadius.circular(8),
          ),
          child: SizedBox(
            width: 18,
            height: 18,
            child: CircularProgressIndicator(
              strokeWidth: 2.5,
              color: AppTheme.primary,
            ),
          ),
        );
      case DownloadStatus.failed:
        return Container(
          padding: const EdgeInsets.all(6),
          decoration: BoxDecoration(
            color: AppTheme.error.withValues(alpha: 0.15),
            borderRadius: BorderRadius.circular(8),
          ),
          child: const Icon(
            Icons.error_rounded,
            color: AppTheme.error,
            size: 18,
          ),
        );
      case DownloadStatus.queued:
        return Container(
          padding: const EdgeInsets.all(6),
          decoration: BoxDecoration(
            color: AppTheme.surface,
            borderRadius: BorderRadius.circular(8),
          ),
          child: const Icon(
            Icons.schedule_rounded,
            color: AppTheme.textTertiary,
            size: 18,
          ),
        );
    }
  }

  Widget _buildMenu(BuildContext context) {
    return IconButton(
      tooltip: 'Mais ações do download',
      icon: const Icon(Icons.more_vert_rounded,
          color: AppTheme.textSecondary,
          size: 24), // Increased from 20 to 24 for better touch target
      onPressed: () => _showBottomSheet(context),
      padding: const EdgeInsets.all(12), // Increased padding for touch target
    );
  }

  void _showBottomSheet(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: AppTheme.surfaceElevated,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (BuildContext sheetContext) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const SizedBox(height: 12),
              Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: AppTheme.textTertiary.withValues(alpha: 0.5),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              const SizedBox(height: 8),
              Padding(
                padding:
                    const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                child: Text(
                  item.title,
                  style: const TextStyle(
                    color: AppTheme.textPrimary,
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              const Divider(color: AppTheme.border),
              if (_isCompleted()) ...[
                ListTile(
                  leading: const Icon(Icons.open_in_new_rounded,
                      color: AppTheme.textSecondary),
                  title: const Text('Abrir',
                      style: TextStyle(color: AppTheme.textPrimary)),
                  onTap: () {
                    Navigator.pop(sheetContext);
                    OpenFile.open(item.outputPath);
                  },
                ),
                ListTile(
                  leading: const Icon(Icons.share_rounded,
                      color: AppTheme.textSecondary),
                  title: const Text('Compartilhar',
                      style: TextStyle(color: AppTheme.textPrimary)),
                  onTap: () async {
                    Navigator.pop(sheetContext);
                    final shareService = SharePlus.instance;
                    await shareService.share(
                      ShareParams(files: [XFile(item.outputPath)]),
                    );
                  },
                ),
                if (item.type == DownloadType.audio)
                  ListTile(
                    leading: const Icon(Icons.edit_rounded,
                        color: AppTheme.textSecondary),
                    title: const Text('Editar metadados',
                        style: TextStyle(color: AppTheme.textPrimary)),
                    onTap: () async {
                      Navigator.pop(sheetContext);
                      // Evita conflito de foco entre o fechamento do bottom sheet
                      // e a abertura do dialog, que causava flicker do teclado.
                      await Future.delayed(const Duration(milliseconds: 350));
                      if (context.mounted) {
                        await _editMetadata(context);
                      }
                    },
                  ),
                if (onExport != null)
                  ListTile(
                    leading: const Icon(Icons.save_alt_rounded,
                        color: AppTheme.textSecondary),
                    title: Text(
                      item.exportStatus == ExportStatus.exported
                          ? 'Exportar novamente'
                          : 'Exportar',
                      style: const TextStyle(color: AppTheme.textPrimary),
                    ),
                    onTap: () async {
                      Navigator.pop(sheetContext);
                      await onExport?.call(item);
                    },
                  ),
              ],
              ListTile(
                leading:
                    const Icon(Icons.delete_rounded, color: AppTheme.error),
                title: const Text('Excluir',
                    style: TextStyle(color: AppTheme.error)),
                onTap: () async {
                  Navigator.pop(sheetContext);
                  if (context.mounted) {
                    await _delete(context);
                  }
                },
              ),
              const SizedBox(height: 8),
            ],
          ),
        );
      },
    );
  }

  Widget _buildProgressBar(double currentProgress) {
    return Semantics(
      label: 'Progresso do download',
      value: '${(currentProgress * 100).toStringAsFixed(1)} por cento',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: LinearProgressIndicator(
              value: currentProgress,
              backgroundColor: AppTheme.surface,
              valueColor: const AlwaysStoppedAnimation(AppTheme.primary),
              minHeight: 6,
            ),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Text(
                '${(currentProgress * 100).toStringAsFixed(1)}%',
                style: const TextStyle(
                  color: AppTheme.primary,
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const Spacer(),
              if (currentProgress > 0 && currentProgress < 1)
                Text(
                  'Baixando...',
                  style: TextStyle(
                    color: AppTheme.textTertiary,
                    fontSize: 12,
                    fontWeight: FontWeight.w500,
                  ),
                ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildError() {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppTheme.error.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppTheme.error.withValues(alpha: 0.2)),
      ),
      child: Row(
        children: [
          const Icon(Icons.error_outline_rounded,
              color: AppTheme.error, size: 18),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              item.errorMessage!,
              style: const TextStyle(
                color: AppTheme.error,
                fontSize: 12,
                fontWeight: FontWeight.w500,
              ),
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFooter() {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Expanded(
          child: _buildFooterMetadata(),
        ),
        const SizedBox(width: 8),
        _buildPlayButton(),
      ],
    );
  }

  Widget _buildFooterMetadata() {
    return Wrap(
      spacing: 12,
      runSpacing: 6,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(
              Icons.access_time_rounded,
              color: AppTheme.textTertiary,
              size: 14,
            ),
            const SizedBox(width: 6),
            Text(
              _compactDateFormat.format(item.createdAt),
              style: const TextStyle(
                color: AppTheme.textTertiary,
                fontSize: 12,
              ),
            ),
          ],
        ),
        if (item.fileSizeBytes != null)
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(
                Icons.folder_zip_rounded,
                color: AppTheme.textTertiary,
                size: 14,
              ),
              const SizedBox(width: 6),
              Text(
                _formatSize(item.fileSizeBytes!),
                style: const TextStyle(
                  color: AppTheme.textTertiary,
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(_exportStatusIcon(), color: _exportStatusColor(), size: 14),
            const SizedBox(width: 6),
            Text(
              item.exportStatusLabel,
              style: TextStyle(
                color: _exportStatusColor(),
                fontSize: 12,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildPlayButton() {
    return Semantics(
      button: true,
      label: 'Reproduzir arquivo ${item.title}',
      hint: 'Abre o arquivo baixado com o player padrão',
      child: Tooltip(
        message: 'Reproduzir arquivo',
        child: Material(
          color: Colors.transparent,
          child: InkWell(
            onTap: () => OpenFile.open(item.outputPath),
            borderRadius: BorderRadius.circular(20),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
              decoration: BoxDecoration(
                color: AppTheme.success.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(20),
              ),
              child: const Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.play_arrow_rounded,
                      color: AppTheme.success, size: 14),
                  SizedBox(width: 4),
                  Text(
                    'Reproduzir',
                    style: TextStyle(
                      color: AppTheme.success,
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
