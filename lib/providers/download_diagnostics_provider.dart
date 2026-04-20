import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../services/chaquo_download_service.dart';
import '../services/database_service.dart';
import '../services/download_service.dart';
import '../services/observability_service.dart';

class DownloadDiagnosticsState {
  static const _unset = Object();

  const DownloadDiagnosticsState({
    required this.telemetry,
    required this.autoExportEnabled,
    required this.isExpanded,
    required this.isCheckingYtDlp,
    required this.isUpdatingYtDlp,
    required this.isRepairingMetadata,
    required this.isSavingAutoExport,
    required this.repairProcessed,
    required this.repairTotal,
    this.currentYtDlpVersion,
    this.latestYtDlpVersion,
    this.updateAvailable = false,
    this.lastMessage,
    this.lastMessageIsError = false,
    this.lastCheckedAt,
  });

  final DownloadFailureTelemetry telemetry;
  final bool autoExportEnabled;
  final bool isExpanded;
  final bool isCheckingYtDlp;
  final bool isUpdatingYtDlp;
  final bool isRepairingMetadata;
  final bool isSavingAutoExport;
  final int repairProcessed;
  final int repairTotal;
  final String? currentYtDlpVersion;
  final String? latestYtDlpVersion;
  final bool updateAvailable;
  final String? lastMessage;
  final bool lastMessageIsError;
  final DateTime? lastCheckedAt;

  bool isBusy() =>
      isCheckingYtDlp ||
      isUpdatingYtDlp ||
      isRepairingMetadata ||
      isSavingAutoExport;

  DownloadDiagnosticsState copyWith({
    DownloadFailureTelemetry? telemetry,
    bool? autoExportEnabled,
    bool? isExpanded,
    bool? isCheckingYtDlp,
    bool? isUpdatingYtDlp,
    bool? isRepairingMetadata,
    bool? isSavingAutoExport,
    int? repairProcessed,
    int? repairTotal,
    String? currentYtDlpVersion,
    String? latestYtDlpVersion,
    bool? updateAvailable,
    Object? lastMessage = _unset,
    bool? lastMessageIsError,
    DateTime? lastCheckedAt,
  }) {
    return DownloadDiagnosticsState(
      telemetry: telemetry ?? this.telemetry,
      autoExportEnabled: autoExportEnabled ?? this.autoExportEnabled,
      isExpanded: isExpanded ?? this.isExpanded,
      isCheckingYtDlp: isCheckingYtDlp ?? this.isCheckingYtDlp,
      isUpdatingYtDlp: isUpdatingYtDlp ?? this.isUpdatingYtDlp,
      isRepairingMetadata: isRepairingMetadata ?? this.isRepairingMetadata,
      isSavingAutoExport: isSavingAutoExport ?? this.isSavingAutoExport,
      repairProcessed: repairProcessed ?? this.repairProcessed,
      repairTotal: repairTotal ?? this.repairTotal,
      currentYtDlpVersion: currentYtDlpVersion ?? this.currentYtDlpVersion,
      latestYtDlpVersion: latestYtDlpVersion ?? this.latestYtDlpVersion,
      updateAvailable: updateAvailable ?? this.updateAvailable,
      lastMessage: identical(lastMessage, _unset)
          ? this.lastMessage
          : lastMessage as String?,
      lastMessageIsError: lastMessageIsError ?? this.lastMessageIsError,
      lastCheckedAt: lastCheckedAt ?? this.lastCheckedAt,
    );
  }
}

class DownloadDiagnosticsNotifier
    extends AsyncNotifier<DownloadDiagnosticsState> {
  StreamSubscription<DownloadFailureTelemetry>? _telemetrySubscription;
  final _observabilityService = ObservabilityService.instance;
  final _databaseService = DatabaseService.instance;
  final _chaquoService = ChaquoDownloadService.instance;

  DownloadDiagnosticsState? _currentState() => state.asData?.value;

  bool _isBusyOrUnavailable() {
    final current = _currentState();
    return current == null || current.isBusy();
  }

  @override
  Future<DownloadDiagnosticsState> build() async {
    await _observabilityService.init();

    _telemetrySubscription =
        _observabilityService.downloadFailureStream.listen((telemetry) {
      _patch((state) => state.copyWith(telemetry: telemetry));
    });
    ref.onDispose(() {
      _telemetrySubscription?.cancel();
    });

    final autoExportEnabled = await _databaseService.getAutoExportEnabled();

    String? currentVersion;
    String? latestVersion;
    var updateAvailable = false;

    final checkResult = await _chaquoService.checkYtDlpUpdate(
      forceRemote: false,
    );
    if (checkResult['success'] == true) {
      currentVersion = checkResult['current_version']?.toString();
      latestVersion = checkResult['latest_version']?.toString();
      updateAvailable = checkResult['update_available'] == true;
    }

    return DownloadDiagnosticsState(
      telemetry: _observabilityService.currentDownloadFailureTelemetry,
      autoExportEnabled: autoExportEnabled,
      isExpanded: false,
      isCheckingYtDlp: false,
      isUpdatingYtDlp: false,
      isRepairingMetadata: false,
      isSavingAutoExport: false,
      repairProcessed: 0,
      repairTotal: 0,
      currentYtDlpVersion: currentVersion,
      latestYtDlpVersion: latestVersion,
      updateAvailable: updateAvailable,
    );
  }

  void setExpanded(bool expanded) {
    _patch((state) => state.copyWith(isExpanded: expanded));
  }

  Future<void> setAutoExportEnabled(bool enabled) async {
    final previous = _currentState();
    if (previous == null) return;

    _patch((current) => current.copyWith(
          autoExportEnabled: enabled,
          isSavingAutoExport: true,
          lastMessage: null,
        ));

    try {
      await _databaseService.setAutoExportEnabled(enabled);
      _patch((current) => current.copyWith(
            isSavingAutoExport: false,
            lastMessage: enabled
                ? 'Auto-exportacao ativada.'
                : 'Auto-exportacao desativada.',
            lastMessageIsError: false,
          ));
    } catch (_) {
      state = AsyncData(previous.copyWith(
        isSavingAutoExport: false,
        lastMessage:
            'Nao foi possivel salvar a preferencia de auto-exportacao.',
        lastMessageIsError: true,
      ));
    }
  }

  Future<void> checkYtDlpUpdate({bool forceRemote = true}) async {
    if (_isBusyOrUnavailable()) {
      return;
    }

    _patch((state) => state.copyWith(
          isCheckingYtDlp: true,
          lastMessage: null,
        ));

    final result = await _chaquoService.checkYtDlpUpdate(
      forceRemote: forceRemote,
    );

    if (result['success'] != true) {
      _patch((state) => state.copyWith(
            isCheckingYtDlp: false,
            lastMessage: result['error']?.toString() ??
                'Falha ao verificar atualizacao do yt-dlp.',
            lastMessageIsError: true,
            lastCheckedAt: DateTime.now(),
          ));
      return;
    }

    final currentVersion = result['current_version']?.toString();
    final latestVersion = result['latest_version']?.toString();
    final updateAvailable = result['update_available'] == true;

    _patch((state) => state.copyWith(
          isCheckingYtDlp: false,
          currentYtDlpVersion: currentVersion,
          latestYtDlpVersion: latestVersion,
          updateAvailable: updateAvailable,
          lastMessage: updateAvailable
              ? 'Nova versao do yt-dlp disponivel.'
              : 'yt-dlp ja esta atualizado.',
          lastMessageIsError: false,
          lastCheckedAt: DateTime.now(),
        ));
  }

  Future<void> updateYtDlpNow() async {
    if (_isBusyOrUnavailable()) {
      return;
    }

    _patch((state) => state.copyWith(
          isUpdatingYtDlp: true,
          lastMessage: null,
        ));

    final result = await _chaquoService.updateYtDlpIfNeeded(force: true);

    if (result['success'] != true) {
      _patch((state) => state.copyWith(
            isUpdatingYtDlp: false,
            lastMessage:
                result['error']?.toString() ?? 'Falha ao atualizar yt-dlp.',
            lastMessageIsError: true,
          ));
      return;
    }

    final updated = result['updated'] == true;
    final currentVersion = result['current_version']?.toString();
    final latestVersion = result['latest_version']?.toString();

    _patch((state) => state.copyWith(
          isUpdatingYtDlp: false,
          currentYtDlpVersion: currentVersion,
          latestYtDlpVersion: latestVersion,
          updateAvailable: false,
          lastMessage: updated
              ? 'yt-dlp atualizado com sucesso.'
              : 'yt-dlp ja estava atualizado.',
          lastMessageIsError: false,
          lastCheckedAt: DateTime.now(),
        ));

    // Sincroniza estado final com a resposta de checagem local (cache/metadata)
    final checkResult = await _chaquoService.checkYtDlpUpdate(
      forceRemote: false,
    );
    if (checkResult['success'] == true) {
      _patch((state) => state.copyWith(
            currentYtDlpVersion: checkResult['current_version']?.toString(),
            latestYtDlpVersion: checkResult['latest_version']?.toString(),
            updateAvailable: checkResult['update_available'] == true,
            lastCheckedAt: DateTime.now(),
          ));
    }
  }

  Future<void> repairMetadataBatch() async {
    if (_isBusyOrUnavailable()) {
      return;
    }

    _patch((state) => state.copyWith(
          isRepairingMetadata: true,
          repairProcessed: 0,
          repairTotal: 0,
          lastMessage: null,
        ));

    try {
      final downloadService = DownloadService.instance;
      final result = await downloadService.repairAudioMetadataBatch(
        onProgress: (processed, total) {
          _patch((state) => state.copyWith(
                repairProcessed: processed,
                repairTotal: total,
              ));
        },
      );

      final message = result.totalCandidates == 0
          ? 'Nenhum audio concluido encontrado para reparo.'
          : 'Reparo concluido: ${result.repairedCount} corrigidos, ${result.failedCount} falhas, ${result.skippedCount} ignorados.';

      _patch((state) => state.copyWith(
            isRepairingMetadata: false,
            repairProcessed: result.totalCandidates,
            repairTotal: result.totalCandidates,
            lastMessage: message,
            lastMessageIsError:
                result.failedCount > 0 && result.repairedCount == 0,
          ));
    } catch (e) {
      _patch((state) => state.copyWith(
            isRepairingMetadata: false,
            lastMessage:
                'Falha ao executar reparo em lote de metadados: ${e.toString()}',
            lastMessageIsError: true,
          ));
    }
  }

  Future<void> addMissingArtworkBatch() async {
    if (_isBusyOrUnavailable()) {
      return;
    }

    _patch((state) => state.copyWith(
          isRepairingMetadata: true,
          repairProcessed: 0,
          repairTotal: 0,
          lastMessage: null,
        ));

    try {
      final downloadService = DownloadService.instance;
      final result = await downloadService.addMissingArtworkBatch(
        onProgress: (processed, total) {
          _patch((state) => state.copyWith(
                repairProcessed: processed,
                repairTotal: total,
              ));
        },
      );

      final message = result.totalCandidates == 0
          ? 'Nenhum audio concluido encontrado.'
          : 'Capas atualizadas: ${result.updatedCount} itens, ${result.failedCount} falhas, ${result.skippedCount} ignorados.';

      _patch((state) => state.copyWith(
            isRepairingMetadata: false,
            repairProcessed: result.totalCandidates,
            repairTotal: result.totalCandidates,
            lastMessage: message,
            lastMessageIsError:
                result.failedCount > 0 && result.updatedCount == 0,
          ));
    } catch (e) {
      _patch((state) => state.copyWith(
            isRepairingMetadata: false,
            lastMessage: 'Falha ao adicionar capas em lote: ${e.toString()}',
            lastMessageIsError: true,
          ));
    }
  }

  void _patch(
    DownloadDiagnosticsState Function(DownloadDiagnosticsState state) updater,
  ) {
    final current = state.asData?.value;
    if (current == null) return;
    state = AsyncData(updater(current));
  }
}

final downloadDiagnosticsProvider = AsyncNotifierProvider.autoDispose<
    DownloadDiagnosticsNotifier, DownloadDiagnosticsState>(
  DownloadDiagnosticsNotifier.new,
);
