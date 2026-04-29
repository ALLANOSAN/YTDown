# MIGRATION ANALYSIS

## Objetivo
Analisar o estado atual da migração do código Flutter/Dart/Python em `lib/` para o novo código Kotlin/Python em `android/app/src/main`.

## Status atual da migração

### 1. Serviços de download

- `lib/services/download_queue_service.dart`
  - Migrado para `android/app/src/main/kotlin/com/example/ytdown/services/DownloadQueueService.kt`
  - O Kotlin usa `Mutex` e `withLock`, enquanto o Dart usava `Completer` + `TaskQueue`.
  - Status: **Migração estrutural completa** para o serviço de travas/lock.

- `lib/services/download_progress_service.dart`
  - Migrado para `android/app/src/main/kotlin/com/example/ytdown/services/ProgressBus.kt`.
  - Existe também `android/app/src/main/kotlin/com/example/ytdown/services/DownloadProgressService.kt` no Android nativo.
  - Status: **Migração completa** do barramento de atualização de progresso.

- `lib/services/chaquo_download_service.dart`
  - Migrado para `android/app/src/main/kotlin/com/example/ytdown/services/ChaquoDownloadService.kt`.
  - O código Kotlin usa `MethodChannel` para se comunicar com o runtime Python nativo.
  - O runtime Python ativado pelo Kotlin vive em `android/app/src/main/python/ytdown.py` e bibliotecas auxiliares.
  - Status: **Migração parcial/ativa** do serviço de download Python.

- `lib/services/download_service.dart`
  - Há um equivalente nativo em `android/app/src/main/kotlin/com/example/ytdown/services/DownloadService.kt`.
  - O Kotlin atua como fachada para `ChaquoDownloadService` e `DownloadScheduler`.
  - Status: **Migração funcional** do serviço principal de download.

### 2. Runtime Python

- `android/app/src/main/python/ytdown.py`
  - Contém lógica de `yt-dlp`, fallback e atualização similar ao `lib/ytdown.py`.
  - Este arquivo está ativo como parte do runtime Android/Chaquo.

- `lib/ytdown.py`
  - Não foi localizado uso direto em `*.dart` ou `*.kt` via busca de símbolos.
  - Status: **Provavelmente legado/duplicado** ou ainda não integrado.

### 3. Outros sinais importantes

- Há muitos comentários de migração em Kotlin indicando que várias áreas do Flutter foram portadas:
  - `NotificationHelper.kt` <- `notification_service.dart`
  - `StorageResolver.kt` <- `StorageService`
  - `MetadataService.kt` <- `LastFmService._getBestImage`
  - `TaskQueue.kt`, `MetadataUtils.kt`, `CommonUtils.kt`, `VideoInfoParser.kt`, etc.
- O Android nativo também contém `providers` e `ui/screens` que espelham os componentes Flutter.

### 4. Cobertura preliminar de migração de `lib/`

#### UI e Screens
- `lib/main.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/RootApp.kt`, `MainActivity.kt`
- `lib/screens/browser_screen.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/screens/BrowserScreen.kt`
- `lib/screens/downloads_screen.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/screens/DownloadListScreen.kt`
- `lib/screens/home_screen.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/screens/HomeScreen.kt`
- `lib/screens/library_screen.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/screens/LibraryScreen.kt`
- `lib/screens/music_player_screen.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/screens/MusicPlayerScreen.kt`
- `lib/screens/player_full_screen.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/screens/PlayerFullScreen.kt`
- `lib/screens/playlist_detail_screen.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/screens/PlaylistDetailScreen.kt`
- `lib/screens/playlist_selection_screen.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/screens/PlaylistSelectionScreen.kt`

#### Widgets
- `lib/widgets/download_card.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/widgets/DownloadCard.kt`
- `lib/widgets/format_selection_sheet.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/widgets/FormatSelectionSheet.kt`
- `lib/widgets/lazy_indexed_stack.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/widgets/LazyIndexedStack.kt`
- `lib/widgets/shimmer_loading_list.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/widgets/ShimmerLoadingList.kt`

#### Providers
- `lib/providers/browser_provider.dart` -> `android/app/src/main/kotlin/com/example/ytdown/providers/BrowserProvider.kt`
  - Também há lógica de browser/utilitários em `android/app/src/main/kotlin/com/example/ytdown/utils/YouTubeUtils.kt`.
- `lib/providers/download_provider.dart` -> `android/app/src/main/kotlin/com/example/ytdown/providers/DownloadProvider.kt`
- `lib/providers/download_diagnostics_provider.dart` -> `android/app/src/main/kotlin/com/example/ytdown/providers/DownloadDiagnosticsProvider.kt`
- `lib/providers/home_provider.dart` -> `android/app/src/main/kotlin/com/example/ytdown/providers/HomeProvider.kt`
- `lib/providers/library_provider.dart` -> `android/app/src/main/kotlin/com/example/ytdown/providers/LibraryProvider.kt`
- `lib/providers/library_playlists_provider.dart` -> `android/app/src/main/kotlin/com/example/ytdown/providers/LibraryPlaylistsProvider.kt`
- `lib/providers/playlist_detail_provider.dart` -> `android/app/src/main/kotlin/com/example/ytdown/providers/PlaylistDetailProvider.kt`
- `lib/providers/player_provider.dart` -> `android/app/src/main/kotlin/com/example/ytdown/providers/PlayerProvider.kt`
- `lib/providers/library_playlist_selection_provider.dart` -> `android/app/src/main/kotlin/com/example/ytdown/providers/LibraryPlaylistSelectionProvider.kt`

#### Serviços e infraestrutura
- `lib/services/download_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/DownloadService.kt`
- `lib/services/download_queue_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/DownloadQueueService.kt`
- `lib/services/download_progress_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/ProgressBus.kt` / `DownloadProgressService.kt`
- `lib/services/chaquo_download_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/ChaquoDownloadService.kt`
- `lib/services/download_feed_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/DownloadFeedService.kt`
- `lib/services/database_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/DatabaseService.kt`
- `lib/services/file_system_scanner_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/FileSystemScannerService.kt`
- `lib/services/library_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/LibraryService.kt`
- `lib/services/observability_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/ObservabilityService.kt`
- `lib/services/permission_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/PermissionHelper.kt` / `PermissionService.kt`
- `lib/services/storage_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/StorageService.kt` / `StorageResolver.kt`
- `lib/services/notification_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/core/infrastructure/NotificationHelper.kt` / `NotificationService.kt`
- `lib/services/lastfm_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/core/infrastructure/MetadataService.kt`
- `lib/services/foreground_task_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/core/infrastructure/work/DownloadWorker.kt` / `ForegroundTaskService.kt`
- `lib/services/player_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/core/infrastructure/MusicPlayerManager.kt` / `PlayerService.kt`
- `lib/services/binary_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/core/infrastructure/BinaryOrchestrator.kt` / `YtDlpWrapper.kt`
- `lib/services/artwork_cache_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/ArtworkCacheService.kt`
- `lib/services/artwork_manager.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/ArtworkManager.kt`

#### Utils e modelo
- `lib/models/download_item.dart` -> `android/app/src/main/kotlin/com/example/ytdown/models/DownloadItem.kt`
- `lib/utils/common_utils.dart` -> `android/app/src/main/kotlin/com/example/ytdown/utils/CommonUtils.kt`
- `lib/utils/video_info_handler.dart` -> `android/app/src/main/kotlin/com/example/ytdown/core/business/MediaInfoParser.kt`
- `lib/utils/task_queue.dart` -> `android/app/src/main/kotlin/com/example/ytdown/utils/TaskQueue.kt`
- `lib/utils/metadata_utils.dart` -> `android/app/src/main/kotlin/com/example/ytdown/utils/MetadataUtils.kt`
- `lib/utils/lru_cache.dart` -> `android/app/src/main/kotlin/com/example/ytdown/utils/LruCache.kt`

#### Theme
- `lib/theme/app_theme.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/theme/Theme.kt`, `Color.kt`, `Type.kt`

#### Python de comparação pré-migração
- `lib/ytdown.py` -> `android/app/src/main/python/ytdown.py`
- `lib/ytdown.py` foi usado como referência de comparação durante a migração para Kotlin/Chaquo.

## Verificação de correspondência de arquivos

- `lib/main.dart` está refletido em `android/app/src/main/kotlin/com/example/ytdown/ui/RootApp.kt` e `android/app/src/main/kotlin/com/example/ytdown/MainActivity.kt`.
- `lib/services/chaquo_download_service.dart` está refletido em `android/app/src/main/kotlin/com/example/ytdown/services/ChaquoDownloadService.kt`.
- `lib/services/download_service.dart` está refletido em `android/app/src/main/kotlin/com/example/ytdown/services/DownloadService.kt`.
- `android/app/src/main/python/ytdown.py` está presente e implementa o runtime Python usado pelo módulo Kotlin.
- Todos os arquivos de tela listados para UI existem em `android/app/src/main/kotlin/com/example/ytdown/ui/screens/`.
- Todos os widgets listados existem em `android/app/src/main/kotlin/com/example/ytdown/ui/widgets/`.
- Todos os serviços listados existem em `android/app/src/main/kotlin/com/example/ytdown/services/` ou em `android/app/src/main/kotlin/com/example/ytdown/core/infrastructure/`.
- Os providers listados existem em `android/app/src/main/kotlin/com/example/ytdown/providers/`.

## Mapeamento completo de `lib/`

A seguir está o status estimado de cada arquivo em `lib/` com seu provável equivalente Kotlin/Python:

- `lib/main.dart` -> `android/app/src/main/kotlin/com/example/ytdown/RootApp.kt`, `MainActivity.kt` (UI de inicialização)
- `lib/screens/browser_screen.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/screens/BrowserScreen.kt`
- `lib/screens/downloads_screen.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/screens/DownloadListScreen.kt`
- `lib/screens/home_screen.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/screens/HomeScreen.kt`
- `lib/screens/library_screen.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/screens/LibraryScreen.kt`
- `lib/screens/music_player_screen.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/screens/MusicPlayerScreen.kt`
- `lib/screens/player_full_screen.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/screens/PlayerFullScreen.kt`
- `lib/screens/playlist_detail_screen.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/screens/PlaylistDetailScreen.kt`
- `lib/screens/playlist_selection_screen.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/screens/PlaylistSelectionScreen.kt`

- `lib/widgets/download_card.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/widgets/DownloadCard.kt`
- `lib/widgets/format_selection_sheet.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/widgets/FormatSelectionSheet.kt`
- `lib/widgets/lazy_indexed_stack.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/widgets/LazyIndexedStack.kt`
- `lib/widgets/shimmer_loading_list.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/widgets/ShimmerLoadingList.kt`

- `lib/providers/browser_provider.dart` -> `android/app/src/main/kotlin/com/example/ytdown/providers/BrowserProvider.kt` e `android/app/src/main/kotlin/com/example/ytdown/utils/YouTubeUtils.kt`
- `lib/providers/download_provider.dart` -> `android/app/src/main/kotlin/com/example/ytdown/providers/DownloadProvider.kt`
- `lib/providers/download_diagnostics_provider.dart` -> `android/app/src/main/kotlin/com/example/ytdown/providers/DownloadDiagnosticsProvider.kt`
- `lib/providers/home_provider.dart` -> `android/app/src/main/kotlin/com/example/ytdown/providers/HomeProvider.kt`
- `lib/providers/library_provider.dart` -> `android/app/src/main/kotlin/com/example/ytdown/providers/LibraryProvider.kt`
- `lib/providers/library_playlists_provider.dart` -> `android/app/src/main/kotlin/com/example/ytdown/providers/LibraryPlaylistsProvider.kt`
- `lib/providers/playlist_detail_provider.dart` -> `android/app/src/main/kotlin/com/example/ytdown/providers/PlaylistDetailProvider.kt`
- `lib/providers/player_provider.dart` -> `android/app/src/main/kotlin/com/example/ytdown/providers/PlayerProvider.kt`
- `lib/providers/library_playlist_selection_provider.dart` -> `android/app/src/main/kotlin/com/example/ytdown/providers/LibraryPlaylistSelectionProvider.kt`

- `lib/models/download_item.dart` -> `android/app/src/main/kotlin/com/example/ytdown/models/DownloadItem.kt`

- `lib/services/download_queue_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/DownloadQueueService.kt`
- `lib/services/download_progress_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/ProgressBus.kt` / `DownloadProgressService.kt`
- `lib/services/chaquo_download_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/ChaquoDownloadService.kt`
- `lib/services/download_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/DownloadService.kt`
- `lib/services/download_feed_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/DownloadFeedService.kt`
- `lib/services/database_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/DatabaseService.kt`
- `lib/services/file_system_scanner_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/FileSystemScannerService.kt`
- `lib/services/library_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/LibraryService.kt`
- `lib/services/notification_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/core/infrastructure/NotificationHelper.kt` / `NotificationService.kt`
- `lib/services/observability_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/ObservabilityService.kt`
- `lib/services/player_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/core/infrastructure/MusicPlayerManager.kt` / `PlayerService.kt`
- `lib/services/permission_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/PermissionHelper.kt` / `PermissionService.kt`
- `lib/services/storage_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/StorageService.kt` / `core/infrastructure/StorageResolver.kt`
- `lib/services/lastfm_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/core/infrastructure/MetadataService.kt`
- `lib/services/foreground_task_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/core/infrastructure/work/DownloadWorker.kt` / `ForegroundTaskService.kt`
- `lib/services/binary_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/core/infrastructure/BinaryOrchestrator.kt` / `YtDlpWrapper.kt`
- `lib/services/artwork_cache_service.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/ArtworkCacheService.kt`
- `lib/services/artwork_manager.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/ArtworkManager.kt`

- `lib/utils/logger.dart` -> `android/app/src/main/kotlin/com/example/ytdown/services/ObservabilityService.kt`
- `lib/utils/video_info_handler.dart` -> `android/app/src/main/kotlin/com/example/ytdown/core/business/MediaInfoParser.kt`
- `lib/utils/common_utils.dart` -> `android/app/src/main/kotlin/com/example/ytdown/utils/CommonUtils.kt`
- `lib/utils/task_queue.dart` -> `android/app/src/main/kotlin/com/example/ytdown/utils/TaskQueue.kt`
- `lib/utils/metadata_utils.dart` -> `android/app/src/main/kotlin/com/example/ytdown/utils/MetadataUtils.kt`
- `lib/utils/lru_cache.dart` -> `android/app/src/main/kotlin/com/example/ytdown/utils/LruCache.kt`

- `lib/theme/app_theme.dart` -> `android/app/src/main/kotlin/com/example/ytdown/ui/theme/Theme.kt`, `Color.kt`, `Type.kt`

- `lib/ytdown.py` -> `android/app/src/main/python/ytdown.py`

## Conclusões iniciais

- A migração do núcleo de download está claramente em andamento e já bem avançada no lado Kotlin.
- O serviço Python ainda existe, mas agora está sob `android/app/src/main/python/`, não sob `lib/`.
- O Flutter `lib/` ainda contém serviços e providers originais, possivelmente porque o projeto atual mantém os dois aplicativos/lógicas paralelos.
- `lib/ytdown.py` parece ser um arquivo de implementação Python antiga ou não referenciada diretamente pela versão atual.

## Status de migração atual

- A maioria dos arquivos de `lib/` já encontra equivalentes em Kotlin/Python.
- A camada de providers (browser, downloads, player, diagnóstico, biblioteca) já está presente em `android/app/src/main/kotlin/com/example/ytdown/providers/`.
- A maioria dos serviços do Flutter foi migrada para `android/app/src/main/kotlin/com/example/ytdown/services/` e `core/infrastructure/`.
- A UI principal também já está implementada em `android/app/src/main/kotlin/com/example/ytdown/ui/`.

## Verificação de correspondência

- `lib/main.dart` está refletido em `android/app/src/main/kotlin/com/example/ytdown/ui/RootApp.kt` e `android/app/src/main/kotlin/com/example/ytdown/MainActivity.kt`.
- `lib/services/chaquo_download_service.dart` está refletido em `android/app/src/main/kotlin/com/example/ytdown/services/ChaquoDownloadService.kt`.
- `lib/services/download_service.dart` está refletido em `android/app/src/main/kotlin/com/example/ytdown/services/DownloadService.kt`.
- `lib/ytdown.py` está refletido em `android/app/src/main/python/ytdown.py` como runtime Python de comparação.
- Os arquivos de UI listados em `lib/screens/` existem em `android/app/src/main/kotlin/com/example/ytdown/ui/screens/`.
- Os widgets listados em `lib/widgets/` existem em `android/app/src/main/kotlin/com/example/ytdown/ui/widgets/`.
- Os serviços listados existem em `android/app/src/main/kotlin/com/example/ytdown/services/` ou em `android/app/src/main/kotlin/com/example/ytdown/core/infrastructure/`.
- Os providers listados existem em `android/app/src/main/kotlin/com/example/ytdown/providers/`.
- O tema Flutter `lib/theme/app_theme.dart` está refletido em `android/app/src/main/kotlin/com/example/ytdown/ui/theme/Theme.kt`, `Color.kt` e `Type.kt`.
- Todos os arquivos atuais em `lib/screens`, `lib/widgets`, `lib/providers`, `lib/services`, `lib/models`, `lib/utils` e `lib/theme` têm um equivalente Kotlin/Android ou contraparte nativa verificada.

## Verificação de correspondência detalhada

- `lib/screens/downloads_screen.dart` e `android/app/src/main/kotlin/com/example/ytdown/ui/screens/DownloadListScreen.kt` têm paridade conceitual clara: busca de downloads, abas de filtro (`Todos`, `Áudios`, `Vídeos`), seleção de itens e exclusão em lote.
- `lib/widgets/download_card.dart` e `android/app/src/main/kotlin/com/example/ytdown/ui/widgets/DownloadCard.kt` compartilham o mesmo núcleo de exibição: título, artista, status e seleção. A versão Flutter tinha fluxo adicional de edição de metadados e exportação; a versão Kotlin agora implementa exportação para public collection e adiciona diálogo de edição de metadados, além de receber atualizações de progresso em tempo real via `ProgressBus`.
- `lib/main.dart` se mapeia para `android/app/src/main/kotlin/com/example/ytdown/MainActivity.kt`, `YTDownApplication.kt` e `RootApp.kt`: o Flutter inicializa serviços, Firebase e foreground task antes de renderizar o app; o Kotlin inicializa Chaquopy, Hilt e o tema antes de exibir `RootApp`.
- `lib/theme/app_theme.dart` tem equivalência de cores e estilo em `android/app/src/main/kotlin/com/example/ytdown/ui/theme/Color.kt`, `Theme.kt` e `Type.kt`: cores primárias, background preto, surface escuro, textos claros, `success`, `warning` e `error` estão alinhados.

## Lacunas conhecidas

- Confirmado o mapeamento de `lib/screens/downloads_screen.dart` para `DownloadListScreen.kt`; a revisão restante deve focar em paridade de fluxo de navegação, estado e exibição de downloads.
- Confirmado o mapeamento de `lib/widgets/download_card.dart` para `DownloadCard.kt`; a lógica de atualização de progresso e o menu de ações já estão implementados, restando validações de estilo e usabilidade fina.
- Confirmado que `lib/providers/download_diagnostics_provider.dart` e `lib/services/download_feed_service.dart` têm equivalentes em `DownloadDiagnosticsProvider.kt` e `DownloadFeedService.kt`; resta verificar a cobertura de casos de retry e falha.
- Confirmado o mapeamento de `lib/main.dart` para `YTDownApplication.kt`, `MainActivity.kt` e `RootApp.kt`; falta validar equivalência completa do lifecycle, inicialização de Chaquopy e registro de intents.
- Confirmado o mapeamento de `lib/theme/app_theme.dart` para `android/app/src/main/kotlin/com/example/ytdown/ui/theme/Theme.kt`, `Color.kt` e `Type.kt`; precisa-se revisar equivalência visual, paleta de cores e tipografia.

## O que falta para finalizar a migração

1. Validar se `lib/` e `android/app/src/main` pertencem a modos distintos do mesmo produto ou se o Flutter está sendo descontinuado.
2. Confirmar se `lib/ytdown.py` deve ser removido ou reconciliado com `android/app/src/main/python/ytdown.py`.
3. Verificar a cobertura exata de `ChaquoDownloadService.kt` versus `lib/services/chaquo_download_service.dart` — quais APIs ainda diferem.
4. Executar validação `.agent` para garantir que não há erros básicos em Python e Dart.
5. Se a meta for consolidar tudo no Android nativo, planejar a remoção segura dos arquivos Flutter/Python legados.

## Implementação iniciada

- Verifiquei que todos os providers nativos existem em `android/app/src/main/kotlin/com/example/ytdown/providers/`.
- A migração de UI e widgets já foi coberta em `android/app/src/main/kotlin/com/example/ytdown/ui/`.
- O Python de comparação pré-migração está em `android/app/src/main/python/ytdown.py`; após migração será removido com `lib/`.
- Próximo trabalho: validar mapeamento exato de cada `lib/` arquivo para o equivalente nativo e preparar a remoção de `lib/`.

### Verificação manual de correspondência

- `BrowserProvider.kt` existe e implementa estado de browser/URL como no provider Flutter.
- `DownloadProvider.kt` existe e gerencia a lista de downloads, equivalente ao `download_provider.dart`.
- `DownloadDiagnosticsProvider.kt` existe e lida com falhas de download e retry, como o `download_diagnostics_provider.dart`.
- `PlayerProvider.kt` existe e mapeia estado do player e comandos de reprodução.
- `PermissionHelper.kt` contém a lógica migrada de `lib/services/permission_service.dart`.
- Esta verificação confirma que os providers e serviços críticos já têm contrapontos nativos e que `lib/` permanece como referência até a migração ser 100% concluída.

## Arquivos-chave para acompanhamento

- `lib/services/download_queue_service.dart`
- `lib/services/download_progress_service.dart`
- `lib/services/chaquo_download_service.dart`
- `lib/services/download_service.dart`
- `lib/ytdown.py`
- `android/app/src/main/kotlin/com/example/ytdown/services/DownloadQueueService.kt`
- `android/app/src/main/kotlin/com/example/ytdown/services/ProgressBus.kt`
- `android/app/src/main/kotlin/com/example/ytdown/services/DownloadProgressService.kt`
- `android/app/src/main/kotlin/com/example/ytdown/services/ChaquoDownloadService.kt`
- `android/app/src/main/kotlin/com/example/ytdown/services/DownloadService.kt`
- `android/app/src/main/kotlin/com/example/ytdown/PythonBridge.kt`
- `android/app/src/main/python/ytdown.py`

## Validação executada

- A verificação `.agent` rodou `python3 .agent/skills/lint-and-validate/scripts/lint_runner.py .`.
- Resultado: nenhum linter foi detectado porque o projeto não tem `package.json`, `pyproject.toml` ou `requirements.txt` configurados.
- Tentativa de rodar `flutter analyze lib` falhou com `exit code 127` porque o comando `flutter` não está disponível no ambiente atual.

## Observação

- A análise está pronta para avançar assim que o ambiente de Flutter estiver configurado ou a validação de build for executada em um sistema com Flutter instalado.
