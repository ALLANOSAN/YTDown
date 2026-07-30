# Security Summary: android/app/src/main/kotlin/com/example/ytdown/services

## Directory: android/app/src/main/kotlin/com/example/ytdown/services


### Local Source Files


#### ArtworkCacheService.kt

Declarations:

- `class ArtworkCacheService @Inject constructor(`


#### ArtworkManager.kt

Declarations:

- `class ArtworkManager @Inject constructor(`


#### CacheGuardianWorker.kt

Declarations:

- `class CacheGuardianWorker @AssistedInject constructor(`


#### ChaquoDownloadService.kt

Declarations:

- `class ChaquoDownloadService`

- `fun fetchVideoInfo(url: String): JSONObject {`

- `fun downloadVideo(`


#### CoverArtArchiveService.kt

Declarations:

- `class CoverArtArchiveService @Inject constructor() {`


#### DatabaseService.kt

Declarations:

- `class DatabaseService @Inject constructor(`


#### DownloadFeedService.kt

Declarations:

- `class DownloadFeedService @Inject constructor(`

- `fun stream(): Flow<List<DownloadItemEntity>> = scheduler.stream()`

- `fun streamPaged(query: String = "", typeFilter: Int? = null): Flow<PagingData<DownloadItemEntity>> =`


#### DownloadProgressService.kt

Declarations:

- `class DownloadProgressService @Inject constructor(`

- `fun addUpdate(item: DownloadItemEntity) {`


#### DownloadQueueService.kt

Declarations:

- `class DownloadQueueService @Inject constructor() {`


#### DownloadService.kt

Declarations:

- `class DownloadService @Inject constructor(`

- `fun fetchVideoInfo(url: String): JSONObject = chaquoDownloadService.fetchVideoInfo(url)`


#### DynamicMusicDiscovery.kt

Declarations:

- `class DynamicMusicDiscovery @Inject constructor(`


#### EqualizerManager.kt

Declarations:

- `class EqualizerManager @Inject constructor(`

- `fun getNumberOfBands(): Short = 10`

- `fun getBandLevelRange(): Pair<Short, Short> = Pair(-15, 15) // BASS DX8 EQ suporta +/- 15dB`

- `fun getCenterFreq(band: Short): Int = centerFreqs.getOrElse(band.toInt()) { 0 }`

- `fun getBandLevel(band: Short): Short = (fxEngine.getBandGain(band.toInt()) * 100).toInt().toShort()`

- `fun setBandLevel(band: Short, level: Short) {`

- `fun setBassBoostStrength(strength: Short) {`

- `fun getBassBoostStrength(): Short = (fxEngine.getBandGain(0) / 15f * 1000f).toInt().toShort()`

- `fun setTargetGain(gainmB: Int) {`

- `fun getTargetGain(): Int = ((controller.uiState.value.volume - 1.0f) * 10000f).toInt()`

- `fun release() {`


#### FileSystemScannerService.kt

Declarations:

- `class FileSystemScannerService @Inject constructor(`


#### ForegroundTaskService.kt

Declarations:

- `class ForegroundTaskService @Inject constructor(`

- `fun init(context: Context) {`

- `fun updateCount(context: Context, count: Int) {`

- `fun stop(context: Context) {`


#### LastfmService.kt

Declarations:

- `class LastfmService @Inject constructor(`


#### LibraryService.kt

Declarations:

- `class LibraryService @Inject constructor(`

- `fun getSongs(): Flow<List<DownloadItemEntity>> = downloadDao.getAllDownloads()`


#### LyricsService.kt

Declarations:

- `class LyricsService @Inject constructor() {`

- `data class LyricsResponse(`


#### MetalRecommendationEngine.kt

Declarations:

- `class MetalRecommendationEngine @Inject constructor() {`


#### MusicBrainzService.kt

Declarations:

- `class MusicBrainzService @Inject constructor() {`


#### MusicFolderService.kt

Declarations:

- `class MusicFolderService @Inject constructor(`


#### NotificationService.kt

Declarations:

- `class NotificationService @Inject constructor(`

- `fun showDownloadStarted(id: String, title: String) {`

- `fun showDownloadProgress(id: String, title: String, progress: Int) {`

- `fun showDownloadCompleted(id: String, title: String) {`

- `fun showDownloadFailed(id: String, title: String, error: String) {`

- `fun cancelNotification(id: String) {`


#### ObservabilityService.kt

Declarations:

- `class ObservabilityService @Inject constructor(`

- `fun trackError(tag: String, message: String, throwable: Throwable? = null, metadata: Map<String, String>? = null) {`


#### PermissionHelper.kt

Declarations:

- `class PermissionHelper @Inject constructor() {`

- `fun hasStoragePermission(context: Context): Boolean {`

- `fun hasNotificationPermission(context: Context): Boolean {`

- `fun requestPermissions(activity: Activity) {`

- `fun openAppSettings(context: Context) {`


#### PermissionService.kt

Declarations:

- `class PermissionService @Inject constructor(`

- `fun hasStoragePermission(context: Context): Boolean = helper.hasStoragePermission(context)`

- `fun hasNotificationPermission(context: Context): Boolean = helper.hasNotificationPermission(context)`

- `fun requestPermissions(activity: Activity) = helper.requestPermissions(activity)`

- `fun openAppSettings(context: Context) = helper.openAppSettings(context)`


#### PlayerService.kt

Declarations:

- `class PlayerService @Inject constructor(`

- `fun playTrack(item: DownloadItemEntity) = manager.playTrack(item)`

- `fun playPlaylist(items: List<DownloadItemEntity>, startIndex: Int = 0) = manager.playPlaylist(items, startIndex)`

- `fun pause() = manager.pause()`

- `fun resume() = manager.resume()`

- `fun next() = manager.next()`

- `fun previous() = manager.previous()`

- `fun seekTo(position: Long) = manager.seekTo(position)`

- `fun toggleRepeatMode() = manager.toggleRepeatMode()`

- `fun toggleShuffle() = manager.toggleShuffle()`


#### ProgressBus.kt

Declarations:

- `class ProgressBus @Inject constructor() {`

- `data class ProgressUpdate(`


#### SharingIntentService.kt

Declarations:

- `class SharingIntentService @Inject constructor() {`

- `fun handleIntent(intent: Intent): String? {`

- `fun cleanUrl(url: String): String {`


#### StorageService.kt

Declarations:

- `class StorageService @javax.inject.Inject constructor() {`

- `data class SafPickerRequest(`

- `fun getInstance(): StorageService {`


---
*Generated by mantis-summarize*