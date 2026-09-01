# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

YTDown — a native Android app (Kotlin + Jetpack Compose) that downloads YouTube audio/video, tags it,
and plays it back with a native BASS audio engine. The download/metadata logic lives in **Python**,
embedded in the APK via **Chaquopy** and called from Kotlin. There is no Flutter code left (only stale
Flutter entries in `.gitignore` / `local.properties`).

## Build & run

Everything Gradle lives under `android/`. The wrapper is at `android/gradlew`.

```bash
./build.sh                  # clean + assembleDebug
./build.sh debug install    # + adb install -r
./build.sh release          # assembleRelease (signed with the debug keystore)

cd android
./gradlew assembleDebug
./gradlew ktlintCheck ktlintFormat
```

Two files are required for the build to succeed and both are git-ignored:

- `.secrets.json` at repo root — parsed by regex in `android/app/build.gradle` for `LASTFM_API_KEY`,
  exposed as `BuildConfig.LASTFM_API_KEY`. `build.sh` aborts if it's missing.
- `android/app/google-services.json` — required by the Firebase/Crashlytics Gradle plugins.

Plugin versions are declared once in `android/build.gradle.kts` (the root build is Kotlin DSL; the
app module `android/app/build.gradle` is Groovy): AGP 9.3.1, Kotlin Compose plugin 2.3.21, KSP 2.3.11,
Hilt 2.60.1, Chaquopy 17.0.0. `kotlin.jvm.target=21` in `gradle.properties`.

Chaquopy invokes the host interpreter named `python3.14` (`buildPython "python3.14"`); it must be on
PATH. `abiFilters` are `arm64-v8a` and `x86_64` only, so `armeabi-v7a`/`x86` copies of `libbass.so`
in `jniLibs/` are never packaged.

Debug builds carry `applicationIdSuffix ".native"` — the installed package is
**`com.example.ytdown.native`**, not `com.example.ytdown`. The Activity class does *not* move with it:
it stays `com.example.ytdown.MainActivity`, so `am start` needs the absolute component
(`com.example.ytdown.native/com.example.ytdown.MainActivity`) — the relative `/.MainActivity` form
resolves to `com.example.ytdown.native.MainActivity`, which does not exist.

The ADB scripts in `scripts/test_automation/` already handle both sides:
`common.resolve_package()` prefers the installed `.native` variant and `common.start_app_activity()`
forces the absolute name. `test_common.py` (10 tests, pure `unittest`, no device needed) locks that
down — run it after touching `common.py`.

## Tests

**Python** — 76 tests across 11 files. No pytest config and no `tests/__init__.py`, so discovery
fails — run files individually from the python source dir:

```bash
cd android/app/src/main/python
PYTHONPATH=. python3 tests/test_runtime.py
PYTHONPATH=. python3 tests/test_metadata.py -k test_id3_injection
```

`tests/test_playlist_network.py` hits YouTube for real and self-skips unless `YTDOWN_NETWORK_TESTS=1`
is set. It exists to catch yt-dlp behaviour changes in `extract_flat`/`noplaylist` that mocks can't.

**Kotlin** — 133 tests across 26 files; JUnit4 + Mockito + Robolectric, under
`android/app/src/test/java/`:

```bash
cd android
./gradlew testDebugUnitTest
./gradlew testDebugUnitTest --tests '*MusicPlayerManagerTest*'
```

**On-device smoke tests** — `python3 scripts/test_automation/run_tests.py` drives a connected device
over ADB (logcat assertions, not instrumentation). It does *not* download anything; it only checks app
state.

It gives 3/3 on a healthy app — verified on Android 16 (SDK 36) on 2026-09-01. It used to give 1/3
on a healthy app; the four checks that lied were fixed the same day, and the shape of those bugs is
worth knowing before writing a new check:

- **`dumpsys window windows` no longer prints `mCurrentFocus` on Android 16.** Plain `dumpsys window`
  does. Use `common.get_focused_package()`.
- **logcat truncates the process name to 15 chars**, so `com.example.ytdown.native` appears as
  `e.ytdown.native` and grepping the full package never matches. Filter by PID:
  `common.get_app_logcat()`.
- **`adb pull` prints "1 file pulled"**, not the file. A check asserting on that output was asserting
  on the pull message. Read the file on the device: `common.dump_ui_hierarchy()`.
- **A device preference is not an app health signal.** `lock_screen_show_media` returns `null` when
  untouched and used to fail the whole suite. `common.resumir_checks(obrigatorios, informativos)`
  decides the exit code from required checks only and prints the rest as `INFO`.

Those four helpers live in `common.py` and are covered by `test_common.py` — 23 pure `unittest` tests
that mock `run_adb` and need no device. Run it after touching `common.py`.

A red result still deserves `adb logcat -b crash`: an empty crash buffer plus a focused
`MainActivity` means the app is fine and the script is not.

## Architecture

### Kotlin ↔ Python bridge

Python is the source of truth for downloading and tag *writing*. `android/app/src/main/python/ytdown.py`
is the only facade Kotlin should call; it re-exports from `download.py`, `fetch.py`, `metadata.py`
and `runtime.py`.

Metadata *lookup* is Kotlin-side, not Python: `MusicBrainzService` → `CoverArtArchiveService` /
`LastfmService` → `FanArtTvService`, orchestrated by `MediaImportProcessor` (package
`com.example.ytdown.core.media`, but the file sits at `ui/screens/MediaImportProcessor.kt` — see
*Package vs. directory* below), which then calls Python (Mutagen) to write the tags. The old
`enrich.py::search_metadata` path was removed — it only returned title/artist/album, with no track
number, year, or cover-art IDs.

- Callers: `core/business/YtDlpWrapper.kt`, `PythonBridge.kt`, `core/artwork/PythonMetadataBridge.kt`
  (`Python.getInstance().getModule("ytdown")`).
- **Every public Python function returns a `json.dumps(...)` string.** Kotlin parses it with `JSONObject`.
  Failures come back as `_failure_payload()` shapes: `{success, error, stage, retryable, ...}`.
- Adding a Python entry point means adding it to both the module and `ytdown.py`'s `__all__`.

`runtime.py` can download a newer yt-dlp wheel at runtime into `filesDir/runtime_packages/yt_dlp/`
(SHA256-verified, zip-slip guarded) and reload it over the pip-bundled copy — so the yt-dlp version at
runtime is not necessarily the one Chaquopy installed at build time.

FFmpeg ships as `jniLibs/arm64-v8a/libffmpeg_exe.so`; `download.py::_resolve_ffmpeg_binary()` resolves
it from the native lib dir because Android 10+ refuses to exec anything outside it. Keep the `lib*.so`
naming for any new bundled executable.

### Download pipeline

`DownloadViewModel` → `DownloadScheduler` (enqueues a unique `OneTimeWorkRequest`) → `DownloadWorker`
(foreground, drains `repository.nextPending()` in a loop) → `DownloadEngine`/`YtDlpWrapper` → Python →
`DownloadMetadataManager` writes tags and rescans media. Progress and status are persisted to Room,
never held only in memory, so the queue survives process death.

The manifest removes `WorkManagerInitializer` from `androidx.startup` — `YTDownApplication` implements
`Configuration.Provider` with `HiltWorkerFactory`. A new worker must be `@HiltWorker`.

**Playlists expand exactly once, in `fetch_video_info`.** `download_video` writes to a single fixed
`output_path` with no per-track placeholder, so if yt-dlp re-expands the playlist there the N tracks
all land on the *same* file and silently overwrite each other. Hence `noplaylist: True` in
`download.py` (non-negotiable) and an explicit refusal of bare `/playlist?list=X` URLs, which ignore
that flag. `fetch.py` uses `extract_flat: "in_playlist"` — not `True`, which returns
`{_type: "url", title: None}` and collapses a shared album link into one "Sem título" item. Mix/radio
(`list=RD...`) never expands: YouTube generates those entries indefinitely.

### Artwork and tag repair

The two buttons in `SettingsScreen.kt` ("Reparar Tags" → `repairAllMetadata()`, "Capas" →
`enrichAllArtwork()`) run over the whole library, so they are the easiest place to corrupt every file
at once. Two rules hold the design:

- `ArtworkEnricher` and `MetadataRepairer` depend on **ports**, not concrete services —
  `RecordingLookup`, `CoverSource`, `TagWriter`, `BibliotecaDeAudio`, `TagRewriter`
  (`core/business/ArtworkPorts.kt`, bound in `di/ArtworkModule.kt`). The point is testability:
  `PythonMetadataBridge` calls `Python.getInstance()` and cannot run in a unit test, which used to
  make the entire scan unverifiable.
- The *decisions* live in pure objects with no I/O: `ArtworkPolicy`, `ReparoDeCapaPolicy`
  (`core/artwork/`) and `ReparoDeTagsPolicy` (`core/business/`). `AcaoDeReparo.SEM_FONTE` exists
  because MusicBrainz silence under rate limit is not evidence — acting on it rewrote covers from
  stale album names.

`MusicBrainzService.pickOriginalRecording` picks the oldest non-compilation release, not
`recordings[0].releases[0]`; the latter wrote greatest-hits albums and their covers into the library.
`parseSearchResponse` distinguishes a throttled response (`{"error": ...}`, sometimes with HTTP 200)
from a genuine miss, and `searchRecording` retries with backoff instead of reporting "not found".

### Playback (BASS, not ExoPlayer)

- `BassCore` — singleton over `com.un4seen.bass.BASS` (JNI, `java/com/un4seen/bass/BASS.java` +
  `jniLibs/*/libbass.so`), initialized in `YTDownApplication.onCreate()`.
- `BassPlaybackEngine` / `BassFXEngine` — playback and the equalizer DSP.
- `BassMediaSessionAdapter` — a Media3 `SimpleBasePlayer` that fronts the BASS engine so
  `MediaPlaybackService` (a `MediaSessionService`) can expose a real `MediaSession` for the
  notification, lock screen, Bluetooth buttons and the Android 16 Now Bar.
- `PlaybackController` — the app-side `MediaController` client; `PlaybackUiState` is the single source
  of truth for UI.

Bluetooth A2DP can suspend the audio device during long pauses, which makes BASS fail with
`BASS_ERROR_START`. `BassCore.isDeviceRelatedError()` / `reinitialize()` exist for that — see
`android/docs/BT_LONG_PAUSE_BUG.md` before touching resume logic.

### Persistence

One Room database: `AppDatabase` (`ytdown.db`) — downloads and library; explicit `ALL_MIGRATIONS`
plus `fallbackToDestructiveMigration()`. Schemas are exported to `app/schemas` and bundled into debug
assets.

(There used to be a second, `MetalDatabase`, for a metal-discovery feature. That whole feature —
screens, ViewModels, repositories, the Paging 3 `RemoteMediator` and `metal_archives.py` — was removed
on 2026-09-01 after its tab and route were deleted. Don't reintroduce references to it.)

### UI layering

Compose + Hilt. `MainActivity` → `RootApp` → `MainNavigation` (routes in `ui/navigation/Screen.kt`).
ViewModels in `ui/` own `StateFlow` state; `providers/` holds smaller injected state holders shared
between screens (`DownloadProvider`, `LibraryProvider`, `PlayerProvider`, …). `services/` is a flat bag
of app services (storage, scanning, MusicBrainz, Last.fm, artwork, observability).

Errors go through `ObservabilityService.trackError(tag, message, throwable, extras)` (Crashlytics +
logcat), not bare `Log.e`.

## Conventions

- Comments, KDoc, log strings and commit messages are in **Portuguese**; keep matching the surrounding file.
- Conventional-commit prefixes (`feat:`, `fix:`, `refactor:`), lowercase, no accents in the subject line.
- Domain primitives are `@JvmInline value class`es in `core/domain/BinaryTypes.kt` (`VideoUrl`,
  `FilePath`, `ExitCode`) — pass those, not raw `String`.
- The stack is AGP 9.3.1 + KSP 2.3.11 + Hilt 2.60.1, all pinned in `android/build.gradle.kts`. There
  is no `resolutionStrategy`/`force` block and no `ksp.useKSP2` flag any more — KSP2 is on by
  default. `gradle.properties` still carries a comment about the old KSP2 workaround; it documents a
  setting that no longer exists.
- `.gitignore` excludes `*.so`, `build.sh`, `.planning/`, `.agent/` and most of `scripts/` — several
  tracked-looking files are local-only. The exception is `scripts/test_automation/`, re-included via
  `/scripts/*` + `!/scripts/test_automation/` (the negation only works because the parent pattern
  ends in `/*`, not `/`).

### Package vs. directory

Six files declare a package that does not match the directory they live in. Kotlin allows it, but
`find`/`Read` on the package path fails and greps for the "expected" location come back empty:

| File on disk | Declared package |
|---|---|
| `ui/screens/MediaImportProcessor.kt` | `core.media` |
| `ui/screens/MetadataExtractor.kt` | `core.metadata` |
| `ui/screens/SongEntity.kt` | `core.domain` |
| `ui/screens/SongDao.kt` | `core.infrastructure.persistence` |
| `ui/screens/DatabaseModule.kt` | `di` |
| `core/business/MusicBrainzRecording.kt` | `core.metadata.model` |

Locate these by class name, not by path.

## Stale docs

`.planning/codebase/*.md` and `.agent/ARCHITECTURE.md` are git-ignored local snapshots from 2026-05-09
that **contradict the current code** — TESTING.md claims no tests exist (there are 209: 133 Kotlin +
76 Python). Do not trust them; this file is the source of truth. `implementation_plan.md` (proposing
a drop of Chaquopy for `youtubedl-android` + `jaudiotagger`, a migration that never started) was
deleted for the same reason.

## Errors and logging

`LocalLogger` is the single funnel: `LocalLogger.error(msg, throwable, tag)` writes to logcat **and**
Crashlytics, after redacting URLs (which carry `api_key`) and device paths via `LocalLogger.sanitize`.
It is an `object`, so it needs no DI — do not call `android.util.Log.e` directly. `ObservabilityService.
trackError` delegates to it and only adds Crashlytics custom keys. Flow tracing belongs in
`LocalLogger.debug`, not in `error`, or it floods the crash reports.
