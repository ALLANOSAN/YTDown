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

Chaquopy invokes the host interpreter named `python3.14` (`buildPython "python3.14"`); it must be on
PATH. `abiFilters` are `arm64-v8a` and `x86_64` only, so `armeabi-v7a`/`x86` copies of `libbass.so`
in `jniLibs/` are never packaged.

Debug builds carry `applicationIdSuffix ".native"` — the installed package is
**`com.example.ytdown.native`**, not `com.example.ytdown`. The ADB scripts in `scripts/test_automation/`
hardcode the unsuffixed name and need adjusting when driving a debug install.

## Tests

**Python** (the real test suite). No pytest config and no `tests/__init__.py`, so discovery fails —
run files individually from the python source dir:

```bash
cd android/app/src/main/python
PYTHONPATH=. python3 tests/test_runtime.py
PYTHONPATH=. python3 tests/test_metadata.py -k test_id3_injection
```

**Kotlin** — JUnit4 + Mockito + Robolectric, under `android/app/src/test/java/`:

```bash
cd android
./gradlew testDebugUnitTest
./gradlew testDebugUnitTest --tests '*MusicPlayerManagerTest*'
```

**On-device smoke tests** — `python3 scripts/test_automation/run_tests.py` drives a connected device
over ADB (logcat assertions, not instrumentation).

## Architecture

### Kotlin ↔ Python bridge

Python is the source of truth for downloading and tagging. `android/app/src/main/python/ytdown.py` is
the only facade Kotlin should call; it re-exports from `download.py`, `fetch.py`, `metadata.py`,
`runtime.py`, `enrich.py`, `metal_archives.py`.

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

Two independent Room databases:

- `AppDatabase` (`ytdown.db`) — downloads and library; explicit `ALL_MIGRATIONS` plus
  `fallbackToDestructiveMigration()`. Schemas are exported to `app/schemas` and bundled into debug assets.
- `MetalDatabase` (`data/local/metal/`) — the metal-discovery feature (artists, albums, listening
  history, Paging 3 `RemoteMediator` over MusicBrainz + Cover Art Archive).

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
- `ksp.useKSP2=false` in `gradle.properties` is deliberate (Hilt 2.56 + AGP 8 compatibility); Hilt
  versions are force-pinned in `app/build.gradle`. Don't bump one without the other.
- `.gitignore` excludes `*.so`, `build.sh`, `scripts/`, `.planning/`, `.agent/` — several tracked-looking
  files are local-only.

## Stale docs

`.planning/codebase/*.md` and `.agent/ARCHITECTURE.md` are generated snapshots from 2026-05-09. They are
useful for the Python layer but predate most Kotlin work (e.g. TESTING.md claims no tests exist).
`implementation_plan.md` proposes dropping Chaquopy for `youtubedl-android` + `jaudiotagger` — that
migration has **not** started; the Python layer is still live.
