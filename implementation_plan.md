# Goal: Atualizar Bibliotecas Kotlin e Migrar do Chaquopy para `youtubedl-android` com TDD

O objetivo desta refatoração é remover a dependência do Chaquopy (que embute o interpretador Python no APK) para destravar e atualizar **todas as bibliotecas do Kotlin** (e o AGP) para as suas últimas versões. A funcionalidade de download será substituída pela biblioteca nativa `yausername/youtubedl-android`. Isso irá reduzir o tamanho do APK drasticamente, melhorar o tempo de compilação e estabilizar o app.

## Como as funções atuais serão mantidas sem o Python?

1. **Atualização Dinâmica do `yt-dlp`:**
   A biblioteca `youtubedl-android` já possui uma função nativa para isso: `YoutubeDL.getInstance().updateYoutubeDL(context)`. Quando o usuário clicar no botão, ela vai no GitHub, baixa a versão mais nova e atualiza sozinha.
2. **Substituto para o `mutagen` (Metadados e Capas):**
   Usaremos a biblioteca nativa Java **`jaudiotagger`**. Ela faz exatamente a mesma coisa que o `mutagen` faz no Python: edita as tags ID3 dos arquivos de áudio para embutir Artista, Álbum e a Capa da música no arquivo final.
3. **Substituto para a busca de metadados:**
   Vamos reescrever a busca do MusicBrainz nativamente em Kotlin usando o `OkHttp` (já presente no projeto).

## Proposed Changes

A migração será feita usando **TDD (Test-Driven Development)** para garantir que a nova lógica não quebre o aplicativo.

### 1. Limpeza de Funcionalidades Não Utilizadas (Metal Archives)

#### [DELETE] Aba e Lógica do Metal Archives
- Remover arquivos e classes relacionados à aba "Metal" da UI (`MetalScreen.kt`, `MetalViewModel.kt`, etc).
- Remover rotas e navegações relacionadas ao Metal Archives.
- Remover dependência de scraping complexo (`cloudscraper`).

### 2. Atualização Total de Dependências (Gradle)

#### [MODIFY] `gradle/libs.versions.toml` e `build.gradle`
- Remover o plugin `com.chaquo.python` e o bloco `python { ... }`.
- **Atualizar o Android Gradle Plugin (AGP)** para a versão mais recente.
- **Atualizar o Kotlin** para a versão mais recente (ex: 2.x).
- **Atualizar Compose, Coroutines, OkHttp, Hilt** e todas as demais bibliotecas do projeto para a última versão estável disponível. (O engine de áudio BASS é nativo e não entra nessa atualização).
- Adicionar as dependências do `youtubedl-android` (core e ffmpeg).
- Adicionar o `jaudiotagger` para edição de tags de áudio nativa.

### 3. Refatoração do Motor de Download

#### [MODIFY] [YtDlpWrapper.kt](file:///mnt/Dados1/Estudos/APPDOWNLOADYOUTUBE/android/app/src/main/kotlin/com/example/ytdown/core/business/YtDlpWrapper.kt)
- **`downloadVideo`**: Substituir a chamada Python por `YoutubeDL.getInstance().execute(request)`.
- **`fetchVideoInfo`**: Substituir pela API nativa `YoutubeDL.getInstance().getInfo(url)`.
- **`checkUpdate` / `performUpdate`**: Usar `YoutubeDL.getInstance().updateYoutubeDL(context)`.

### 4. Substituição de Funcionalidades Python Restantes

#### [NEW] `MetadataWriter.kt` (O novo "mutagen")
- Criar um serviço nativo usando `jaudiotagger` para injetar Artista, Álbum e Capa diretamente nos arquivos de áudio pós-download.

#### [NEW] `MusicScraper.kt` (O novo "enrich.py")
- Mover a lógica de consumo da API do MusicBrainz (via `OkHttp`) para Kotlin para buscar metadados das músicas.

### 5. Limpeza Final (Remoção do Python)

#### [DELETE] `src/main/python/` e Wrappers Python
- Excluir a pasta inteira contendo os scripts Python (`ytdown.py`, `enrich.py`, `metal_archives.py`).
- Excluir `PythonBridge.kt`, `PythonEnvironment.kt`, `BinaryOrchestrator.kt`, etc.

## Verification Plan

### Automated Tests (Ciclo Red-Green-Refactor)
1. Escrever testes unitários verificando a integração com `YoutubeDL.getInstance()` (usando mockk).
2. Escrever testes validando se o `MetadataWriter.kt` consegue alterar corretamente as tags de um arquivo MP3 de teste (validando que o `jaudiotagger` funciona perfeitamente como o `mutagen`).
3. Testes para o `MusicScraper.kt` garantindo que parseia os JSONs do MusicBrainz.

---

# Revisão Técnica do Plano — 2026-08-13

Auditoria do plano acima contra o código real. Direção geral é válida, mas **7 premissas
estão erradas** e o ciclo TDD proposto não é TDD (não define o teste que falha primeiro,
nem o contrato a preservar).

## A. Erros factuais (verificados no repositório)

| # | Plano afirma | Realidade | Evidência |
|---|---|---|---|
| 1 | Editar `gradle/libs.versions.toml` | Arquivo **não existe**. Versões estão inline em `android/build.gradle.kts` (plugins) e `android/app/build.gradle` (deps, Groovy, não KTS) | `android/gradle/` só tem `wrapper/` |
| 2 | "Reescrever MusicBrainz em Kotlin" (item 4, `MusicScraper.kt`) | **Já existe e está em produção**: `services/MusicBrainzService.kt` (598 linhas) + `services/CoverArtArchiveService.kt` (280 linhas), consumidos por 9 classes | `grep -rln MusicBrainzService` |
| 3 | Testes com `mockk` | `mockk` não está no projeto. Stack atual: JUnit4 + Mockito 5 + `mockito-inline` + Robolectric + `kotlinx-coroutines-test` | `android/app/build.gradle` (bloco de testes) |
| 4 | Deletar `MetalScreen.kt`, `MetalViewModel.kt` "etc" | Esses nomes **não existem**. Escopo real: **30 arquivos**, 5 ViewModels (`EnhancedMetal`, `DynamicMetal`, `MetalDiscovery`, `BandDetails`, `Discovery`), 1 banco Room inteiro (`MetalDatabase` + 4 DAOs + 4 entities), Paging 3 `MetalArtistsRemoteMediator`, `MetalRecommendationEngine`, 2 repositórios em `core/infrastructure/`, rotas em `Screen.kt`/`MainNavigation.kt` e 4 providers no `AppModule` | `grep -rli metal --include=*.kt` |
| 5 | Deletar Python = deletar `ytdown.py`, `enrich.py`, `metal_archives.py` | Kotlin também chama o módulo `metadata` **direto**, fora da fachada `ytdown.py` | `core/artwork/PythonMetadataBridge.kt:27` |
| 6 | "Reduzir o tamanho do APK drasticamente" | `youtubedl-android` **também embute um interpretador Python** ("yt-dlp executable and python are bundled in the library"). A migração troca um Python embutido por outro | README do `yausername/youtubedl-android` |
| 7 | "`jaudiotagger` faz exatamente o que o mutagen faz" | `jaudiotagger` upstream **não roda em Android**. É preciso um fork (`hexise`, `maxbruecken`, `RouHim`) e **os forks Android removem/alteram justamente as classes de artwork** — a parte que o plano mais precisa (capa embutida) | Ver Riscos Abertos, item R2 |

### A.1 Armadilha de deleção (Metal)

`grep -i metal` casa com `core/audio/EqualizerPreset.kt:28` — o preset de equalizador
"Metal". **Não é do módulo Metal Archives.** Deletar por grep quebra o equalizador.

## B. Superfície Python real (o que precisa de substituto)

O plano cobre download/fetch/tags. A superfície real chamada do Kotlin é maior:

| Função Python | Chamador Kotlin | Substituto proposto no plano? |
|---|---|---|
| `download_video` | `YtDlpWrapper.kt:93` | Sim (`YoutubeDL.execute`) |
| `fetch_video_info` | `YtDlpWrapper.kt` | Sim (`YoutubeDL.getInfo`) |
| `check_yt_dlp_update` / `update_yt_dlp_if_needed` | `YtDlpWrapper.kt` | Sim (`updateYoutubeDL`) |
| `rewrite_file_metadata` | `YtDlpWrapper.kt:187`, `PythonMetadataBridge.kt:31,59` | Parcial (`MetadataWriter.kt`) |
| `search_metadata` (`enrich.py`) | `YtDlpWrapper.kt:209` | Redundante — já existe `MusicBrainzService.kt` |
| `read_file_metadata` | módulo `metadata` direto | **Não** |
| `embed_album_art` | módulo `metadata` direto | **Não** |
| `extract_embedded_artwork` | módulo `metadata` direto | **Não** |
| `extract_metadata_from_filename` | módulo `metadata` direto | **Não** |
| `_apply_cookies_file` (`helpers.py:439`) | fluxo iniciado em `SettingsScreen.kt:58` (importa `cookies.txt` Netscape para desbloquear vídeos com login) | **Não** — feature de usuário, não pode sumir |

Também não citados no plano: `metadata_pipeline.py`, `logger.py`, e a lógica de
atualização do yt-dlp em runtime (`runtime.py` baixa wheel do PyPI, valida SHA256 e
recarrega o módulo — comportamento diferente do `updateYoutubeDL`, que baixa binário do
GitHub).

## C. Fase 0 — 53 MB de graça, sem migrar nada

Achado independente da migração, aplicável **hoje**:

- `assets/binaries/` pesa **54 MB** e está **morto**: `AssetExtractor.extract()` não tem
  nenhum chamador, e `BinaryOrchestrator.setupNativeBinaries()` é um método vazio
  (`core/infrastructure/BinaryOrchestrator.kt:26`).
- `assets/binaries/ffmpeg` e `jniLibs/arm64-v8a/libffmpeg_exe.so` são **o mesmo arquivo**
  de 44,5 MB (md5 `912de446577960bd6f5f323a73464dcb`).
- `libc++_shared.so` (8,9 MB) também está duplicado em `assets/binaries/` e `jniLibs/`.

Distribuição atual: `assets/python` 89 MB · `assets/binaries` 54 MB · `jniLibs/arm64-v8a` 54 MB.

**Ação:** deletar `assets/binaries/`, `AssetExtractor.kt` e `BinaryOrchestrator` (após
remover a chamada em `YtDlpWrapper.kt:64`). Ganho imediato ≈ 53 MB, risco ≈ 0, e diminui
o escopo da migração.

**Teste que prova (RED antes):** teste instrumentado/Robolectric que abre `context.assets.list("binaries")`
e afirma lista vazia. Falha hoje, passa após a remoção.

## D. Plano revisado com TDD de verdade

O ciclo do plano original ("escrever testes verificando a integração com `YoutubeDL`") não
é TDD: testa a biblioteca de terceiros, não o comportamento do app, e não define o contrato
que a migração precisa preservar. Substituir por:

### Fase 1 — Golden master do contrato Python (antes de deletar qualquer coisa)

Sem isso, a migração não tem como provar equivalência.

1. Capturar as respostas JSON reais de `fetch_video_info`, `download_video` e
   `rewrite_file_metadata` em fixtures versionadas
   (`android/app/src/test/resources/contract/*.json`).
2. Escrever os testes de contrato **contra o Python atual** — devem passar hoje:
   ```bash
   cd android/app/src/main/python
   PYTHONPATH=. python3 tests/test_contract_fetch.py
   ```
3. Escrever os **mesmos** asserts em Kotlin contra a implementação nativa futura. Eles
   falham (RED) porque a implementação ainda não existe. É esse o teste que dirige a porta:
   ```bash
   cd android && ./gradlew testDebugUnitTest --tests '*ContractFetchTest*'
   ```

Fixtures obrigatórias: vídeo simples, playlist com ordem preservada, vídeo com
caractere Unicode/emoji no título, vídeo indisponível sem login (caminho de cookies),
MP3 com ID3v2.0 corrompido (já coberto em `tests/test_metadata.py`).

### Fase 2 — Strangler fig, não big bang

Não trocar o motor no lugar. Extrair a interface primeiro:

```kotlin
interface MediaSource {      // fetchInfo / download / update
interface TagWriter {        // write / readTags / embedArt / extractArt
```

- `PythonMediaSource` / `PythonTagWriter` — adaptadores do código atual, **sem mudar
  comportamento** (refactor puro, testes da Fase 1 continuam verdes).
- `NativeMediaSource` / `NativeTagWriter` — implementações novas, dirigidas pelos testes
  de contrato em RED.
- Flag em DataStore (`use_native_engine`) para alternar em runtime e comparar no aparelho
  antes de deletar o Python.

Benefício direto: com a interface no lugar, **`mockk` deixa de ser necessário**. Usa-se
`FakeMediaSource` (a stack do projeto já é Mockito, e a regra de TDD é preferir fakes a
mocks). Nenhuma dependência nova de teste.

### Fase 3 — Remoção do Metal como commit isolado

Antes de deletar, rodar a checagem de acoplamento — `MusicBrainzService` e
`CoverArtArchiveService` **ficam** (usados por `ArtworkEnricher`, `MetadataFixWorker`,
`BatchMetadataFixWorker`, `MediaImportProcessor`, `DynamicMusicDiscovery`, `SystemViewModel`).
Só some o que é exclusivo de Metal: `data/local/metal/`, `data/repository/metal/`,
`ui/screens/metal/`, `ui/components/metal/`, os 5 ViewModels, `MetalRecommendationEngine`,
os 2 `*MetalDiscoveryRepository`, as rotas e os `@Provides` do `AppModule`.

Migração Room: `MetalDatabase` é um banco separado — deletar a classe **não apaga o arquivo
do dispositivo**. Incluir `context.deleteDatabase("<nome>")` numa rotina de limpeza única.

Preservar `EqualizerPreset.Metal`.

### Fase 4 — Bump de dependências separado da mudança de arquitetura

Nunca no mesmo commit. Ordem: (a) AGP/Gradle, (b) Kotlin/KSP/Compose, (c) Hilt, (d) resto.
Depois de cada passo: `./gradlew assembleDebug testDebugUnitTest`.

Atenção ao que está pinado de propósito e quebra junto:
- `ksp.useKSP2=false` (`gradle.properties`) — existe por incompatibilidade Hilt 2.56 + AGP 8.
- `configurations.all { force "com.google.dagger:hilt-android:2.56.2" }` — subir Hilt exige
  mexer nos dois.
- `jniLibs.useLegacyPackaging = true` — o `youtubedl-android` depende disso; manter.
- `abiFilters "arm64-v8a", "x86_64"` — o `youtubedl-android` publica 4 ABIs; sem manter o
  filtro (ou usar ABI splits) o APK **cresce**.

### Fase 5 — Verificação no aparelho (o que os unit tests não pegam)

1. Download de playlist longa com a tela desligada (regressão já corrigida em `aa032a0`).
2. `cookies.txt` importado → vídeo com restrição de login baixa.
3. Tags + capa visíveis no player nativo do Android **e** no app.
4. `du -h` do APK antes/depois, por ABI.
5. `./gradlew ktlintCheck`.

## E. Critérios de aceite e rollback

- **Aceite:** todos os testes de contrato da Fase 1 verdes na implementação nativa, com as
  mesmas fixtures usadas no Python.
- **Rollback:** enquanto a flag `use_native_engine` existir, voltar é trocar um boolean.
  Só deletar `src/main/python/` e o plugin Chaquopy **depois** de uma versão em uso real com
  a flag nativa ligada.
- **Ordem de commits:** Fase 0 → Fase 3 → Fase 1 → Fase 2 → Fase 4 → deleção do Python.
  (Fase 0 e 3 são reduções de escopo puras; fazê-las antes diminui o que precisa ser portado.)

## F. Riscos abertos (verificar antes de começar)

- **R1** — Versão do Python embutida no `youtubedl-android` vs. requisito do yt-dlp atual
  (yt-dlp exige Python ≥ 3.9). O README cita 3.8; confirmar na release usada.
- **R2** — Fork de `jaudiotagger` para Android: escolher um, e validar **escrita de capa**
  (`APIC`/`covr`) com teste sobre MP3 e M4A reais antes de assumir paridade com mutagen.
- **R3** — `runtime.py` (update via wheel do PyPI + SHA256) vs. `updateYoutubeDL` (binário do
  GitHub): comportamento e superfície de erro diferentes; os testes de `tests/test_runtime.py`
  não se traduzem 1:1.
- **R4** — Ganho real de APK após a migração é **incerto** (item A6). Medir com um APK de
  protótipo antes de comprometer o argumento de tamanho.

*Revisão gerada com a skill `test-driven-development` (superpowers).*

---

# Execução — 2026-08-13

## Fase 0 aplicada (feito)

APK debug: **~218 MB → 164,7 MB**.

- `android/app/src/main/assets/binaries/` removido (`git rm`). Continha `ffmpeg` de 44,5 MB
  **versionado no git** com md5 idêntico ao `jniLibs/arm64-v8a/libffmpeg_exe.so`
  (`912de446577960bd6f5f323a73464dcb`), mais `libc++_shared.so` duplicado e um stub
  `python3.13` de 6,6 KB. Nada lia essa pasta.
- Removidos por ausência de chamador: `AssetExtractor.kt`, `ArchiveExtractor.kt`,
  `BinaryConfig`, `AssetPath`, e o método vazio `BinaryOrchestrator.setupNativeBinaries()`
  (com sua chamada em `YtDlpWrapper`).
- `BinaryOrchestrator` reduzido a `getNativeLibDir()` / `getAppFilesDir()` — usados de
  verdade (caminho do ffmpeg e do `cookies.txt`).
- `scripts/build_ffmpeg_android.sh` não recria mais `assets/binaries`.

## Premissa do plano: derrubada por build verde

**Chaquopy nunca foi o teto.** Chaquopy 17.0 suporta AGP 7.3 – 9.2; o projeto estava em
AGP 8.10. O bloqueio real era o `force "com.google.dagger:hilt-android:2.56.2"` em
`app/build.gradle`, que existia por causa do `ksp.useKSP2=false`.

Removido o `force`, este conjunto compilou (`BUILD SUCCESSFUL`, AGP 8.10, Chaquopy 17
intacto):

| | Antes | Depois |
|---|---|---|
| Hilt | 2.56.2 (pinado) | **2.58** |
| androidx.hilt | 1.2.0 | **1.3.0** |
| Kotlin / compose plugin | 2.2.0 | **2.2.21** |
| KSP | 2.2.0-2.0.2 | **2.2.21-2.0.5** |

## Tetos reais encontrados (nenhum é do Chaquopy)

1. **Hilt ≥ 2.59 exige AGP ≥ 9.0.0.** Erro: *"The Hilt Android Gradle plugin is only
   compatible with AGP version 9.0.0 or higher"*. Hilt 2.58 é o máximo sob AGP 8.
2. **AndroidX de 2026 exige AGP ≥ 9.1.0.** `core-ktx:1.19.0`, Compose BOM `2026.08.00`
   (compose 1.12.0), `lifecycle:2.11.0` — 26 falhas de AAR metadata sob AGP 8.10.
3. **`androidx.hilt:1.4.0` exige `compileSdk = 37`** (projeto estava em 36).
4. **AGP 9 remove o plugin `org.jetbrains.kotlin.android`** (Kotlin passa a ser embutido) e
   **desliga o KSP1**. Convivência exige `android.builtInKotlin=false` +
   `android.newDsl=false` — ambos já marcados como deprecated, removidos no AGP 10.
5. **Gradle**: AGP 9.2.1 exige Gradle ≥ 9.4.1.
6. **KSP2 falha neste projeto**: `IllegalStateException: unexpected jvm signature V` no
   `KotlinSymbolProcessing.kt:566`, tanto no KSP 2.2.21-2.0.5 quanto na tentativa seguinte.
   O stacktrace **não nomeia o arquivo culpado**. É bug do KSP AA, não do Hilt nem do
   Chaquopy. Suspeito principal: `@JvmInline value class` (`VideoUrl`, `FilePath`,
   `ExitCode`, `MimeType`) cruzando um processador. **Isto é um bloqueio de verdade**, pois
   o KSP1 é removido no Kotlin 2.3.

## Conclusão para a decisão Chaquopy

Trocar o Chaquopy **não resolve** nenhum dos tetos 1–6: todos são AGP/Gradle/KSP/compileSdk.
O que a saída do Chaquopy dá, de concreto:

- −89 MB de `assets/python`;
- `armeabi-v7a` de volta (Chaquopy com Python ≥ 3.12 é 64-bit only — é a origem real do
  `abiFilters "arm64-v8a", "x86_64"`);
- build sem exigir `python3.14` na máquina;
- teto de AGP sobe de 9.2 para o que o resto do stack aguentar.

Não dá: nada relacionado a versões de bibliotecas Kotlin/AndroidX.

## Stack final que compilou — `BUILD SUCCESSFUL`, com Chaquopy dentro

| | Antes | Agora |
|---|---|---|
| AGP | 8.10.0 | **9.2.1** |
| Gradle | 8.11.1 | **9.4.1** |
| Kotlin / compose plugin | 2.2.0 | **2.3.21** |
| KSP | 2.2.0-2.0.2 (KSP1 forçado) | **2.3.11** (KSP2) |
| Hilt | 2.56.2 (pinado via `force`) | **2.60.1** |
| androidx.hilt | 1.2.0 | **1.4.0** |
| compileSdk | 36 | **37** |
| Room | 2.6.1 | **2.8.4** |
| Media3 | 1.5.0 | **1.11.0** |
| Compose BOM | 2025.12.00 | **2026.08.00** |
| Material3 | 1.3.1 | **1.4.0** |
| Lifecycle | 2.8.7 | **2.11.0** |
| Navigation | 2.8.5 | **2.9.8** |
| Paging | 3.3.5 | **3.5.1** |
| WorkManager | 2.10.0 | **2.11.2** |
| DataStore | 1.1.2 | **1.2.1** |
| core-ktx | 1.15.0 | **1.19.0** |
| activity-compose | 1.10.0 | **1.13.0** |
| documentfile | 1.0.1 | **1.1.0** |
| Chaquopy | 17.0.0 | **17.0.0 (inalterado)** |

Chaves novas em `gradle.properties`, exigidas pelo AGP 9 para conviver com KSP:

```properties
android.builtInKotlin=false   # KSP nao funciona com o Kotlin embutido do AGP 9
android.newDsl=false          # o plugin kotlin.android nao aceita a DSL nova
```

Ambas já vêm marcadas como deprecated e **somem no AGP 10** — é dívida datada, não solução
permanente. A saída definitiva é o KSP passar a funcionar com o Kotlin embutido do AGP.

Sobre o KSP2: o erro `unexpected jvm signature V` visto no KSP 2.2.21-2.0.5 é bug conhecido
(google/ksp#2957, #2177; google/dagger#4505), dispara em DAO Room com `suspend fun` que
retorna `Unit` — este projeto tem **44** dessas. No KSP **2.3.11** o build passou. Se
reaparecer, o contorno é dar retorno às funções (`@Insert suspend fun insert(x): Long`).

## Estado da verificação

| Verificação | Resultado |
|---|---|
| `./gradlew assembleDebug` | ✅ BUILD SUCCESSFUL |
| Testes Python (5 arquivos) | ✅ todos OK |
| `./gradlew testDebugUnitTest` | ❌ 3 falhas — **pré-existentes**, não do bump |
| Teste em aparelho | ⛔ **não executado** |

As 3 falhas são do `MusicPlayerManagerTest.kt`, que está *untracked* no git e sem
`@RunWith(RobolectricTestRunner)` nem `testOptions { unitTests.returnDefaultValues = true }`.
A falha `Method d in android.util.Log not mocked` independe de versão de biblioteca —
falharia igual na stack antiga. É WIP de TDD em RED junto com `MusicPlayerManager.kt`
modificado. Não foi tocado.

**Compilar não é funcionar.** Room 2.6→2.8, Media3 1.5→1.11 e Compose 1.7→1.12 são saltos
com mudança de comportamento. Antes de considerar isto pronto, rodar no aparelho: player,
ilha/notificação Media3, download com tela desligada, `cookies.txt`, equalizador BASS,
gravação de tag e capa.

## Decisão sobre o Chaquopy, revisada

A migração para `youtubedl-android` deixa de ser desbloqueio e vira **otimização**. Só vale
se os itens abaixo importarem para você:

- APK: −89 MB (`assets/python`).
- `armeabi-v7a`: só volta sem Chaquopy.
- Build: sem exigir `python3.14` na máquina, e sem o passo de pip a cada build limpo.

O que ela **custa**, e continua valendo o que está na Revisão Técnica acima: portar a
escrita de tag/capa (10 funções Python, não 1), o update de yt-dlp muda de mecanismo, e
`youtubedl-android` embute o próprio Python — o ganho de APK é menor que os 89 MB brutos.
