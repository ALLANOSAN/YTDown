# Plano de Migração: Flutter para Kotlin Nativo

## Objetivo
Migração concluída: O YTDown agora é um aplicativo Android 100% Nativo (Kotlin/Compose/Room/WorkManager).

## Sprints de Execução

### Sprint 1: Infraestrutura de Binários (Atual)
- [x] Definir Value Classes para caminhos (Domain).
- [x] Implementar Extrator de Assets (Infrastructure).
- [x] Configurar variáveis de ambiente para o Runtime Python.
- [x] Criar Executor de Processos nativo (Infrastructure).
- [x] Implementar StorageResolver para caminhos Android.
- [x] Implementar ArchiveExtractor para descompactar runtimes.
- [x] Criar BinaryOrchestrator (Substituto do BinaryService.dart).

### Sprint 2: Camada de Dados
- [x] Definir Entidade DownloadItem (Domain).
- [x] Criar Room DAO e Database (Infrastructure).
- [x] Implementar DownloadRepository (Business).

### Sprint 3: Serviço de Download (Business Logic)
- [x] Implementar Wrapper de Comando Python (YtDlp).
- [x] Criar DownloadWorker nativo (Infrastructure).

### Sprint 4: Interface do Usuário (UI)
- [x] Criar componentes atômicos em Jetpack Compose.
- [x] Implementar ViewModel com StateFlow para reatividade.
- [x] Integrar Hilt para Injeção de Dependências.
- [x] Remover dependências e código do framework Flutter.
- [x] Configurar ProGuard/R8 para Release.

## Regras de Qualidade (Object Calisthenics)
1. Apenas um nível de indentação por método.
2. Não usar a palavra-chave `ELSE`.
3. Envolver todos os primitivos e Strings em classes de domínio.
4. Classes não podem ter mais de duas variáveis de instância.