package com.example.ytdown

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.ryanheise.audioservice.AudioServiceActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : AudioServiceActivity() {
    private companion object {
        const val TAG = "MainActivity"
    }

    private var notificationChannel: MethodChannel? = null
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun launchIo(taskName: String, action: () -> Unit) {
        ioScope.launch {
            try {
                action()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro não tratado em $taskName: ${e.message}", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i("YTDown_Diagnostic", "SENTINELA: Início do onCreate")
        try {
            super.onCreate(savedInstanceState)
            Log.i("YTDown_Diagnostic", "SENTINELA: super.onCreate concluído")
        } catch (e: Exception) {
            Log.e("YTDown_Diagnostic", "SENTINELA: CRITICAL Erro no super.onCreate: ${e.message}")
            throw e
        }
    }

    override fun onDestroy() {
        StorageService.cancelPendingSafExport(this)
        notificationChannel = null
        ioScope.cancel()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SAF_EXPORT_REQUEST_CODE) {
            StorageService.handleSafExportResult(this, resultCode, data)
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        Log.i("YTDown_Diagnostic", "SENTINELA: Início do configureFlutterEngine")
        super.configureFlutterEngine(flutterEngine)
        Log.i("YTDown_Diagnostic", "SENTINELA: super.onConfigureFlutterEngine concluído")

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "com.example.ytdown/chaquo"
        ).setMethodCallHandler { call, result ->
            if (call.method == "initialize") {
                PythonBridge.initializePython(this)
                result.success(null)
                return@setMethodCallHandler
            }

            if (call.method == "fetchVideoInfo") {
                val url = call.argument<String>("url") ?: ""
                launchIo("fetchVideoInfo") {
                    DownloadService.fetchVideoInfo(this@MainActivity, url, result)
                }
                return@setMethodCallHandler
            }

            if (call.method == "downloadVideo") {
                val url = call.argument<String>("url") ?: ""
                val outputPath = call.argument<String>("outputPath") ?: ""
                val type = call.argument<String>("type") ?: "video"
                val format = call.argument<String>("format") ?: ""
                val quality = call.argument<String>("quality") ?: "best"
                val artist = call.argument<String>("artist")?.trim()?.takeIf { it.isNotEmpty() }
                val album = call.argument<String>("album")?.trim()?.takeIf { it.isNotEmpty() }
                val artworkUrl = call.argument<String>("artworkUrl")?.trim()?.takeIf { it.isNotEmpty() }
                launchIo("downloadVideo") {
                    DownloadService.downloadVideo(
                        this@MainActivity,
                        url,
                        outputPath,
                        type,
                        format,
                        quality,
                        artist,
                        album,
                        artworkUrl,
                        result,
                    )
                }
                return@setMethodCallHandler
            }

            if (call.method == "rewriteMetadata") {
                val filePath = call.argument<String>("filePath") ?: ""
                val title = call.argument<String>("title") ?: ""
                val artist = call.argument<String>("artist")?.trim()?.takeIf { it.isNotEmpty() }
                val album = call.argument<String>("album")?.trim()?.takeIf { it.isNotEmpty() }
                val artworkUrl = call.argument<String>("artworkUrl")?.trim()?.takeIf { it.isNotEmpty() }
                launchIo("rewriteMetadata") {
                    DownloadService.rewriteMetadata(
                        this@MainActivity,
                        filePath,
                        title,
                        artist,
                        album,
                        artworkUrl,
                        result,
                    )
                }
                return@setMethodCallHandler
            }

            if (call.method == "checkYtDlpUpdate") {
                val forceRemote = call.argument<Boolean>("forceRemote") ?: false
                launchIo("checkYtDlpUpdate") {
                    DownloadService.checkYtDlpUpdate(this@MainActivity, forceRemote, result)
                }
                return@setMethodCallHandler
            }

            if (call.method == "batchRescanFiles") {
                val paths = call.argument<List<String>>("paths") ?: emptyList()
                launchIo("batchRescanFiles") {
                    DownloadService.batchRescanFiles(this@MainActivity, paths, result)
                }
                return@setMethodCallHandler
            }

            if (call.method == "updateYtDlpIfNeeded") {
                val force = call.argument<Boolean>("force") ?: false
                launchIo("updateYtDlpIfNeeded") {
                    DownloadService.updateYtDlpIfNeeded(this@MainActivity, force, result)
                }
                return@setMethodCallHandler
            }

            if (call.method == "getNativeLibDir") {
                result.success(applicationInfo.nativeLibraryDir)
                return@setMethodCallHandler
            }

            if (call.method == "checkFfmpeg") {
                launchIo("checkFfmpeg") {
                    DownloadService.checkFfmpeg(this@MainActivity, result)
                }
                return@setMethodCallHandler
            }

            result.notImplemented()
        }

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "com.example.ytdown/native_lib"
        ).setMethodCallHandler { call, result ->
            if (call.method == "getNativeLibDir") {
                result.success(applicationInfo.nativeLibraryDir)
                return@setMethodCallHandler
            }
            result.notImplemented()
        }

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "com.example.ytdown/storage"
        ).setMethodCallHandler { call, result ->
            if (call.method == "exportToPublicCollection") {
                val sourcePath = call.argument<String>("sourcePath") ?: ""
                val displayName = call.argument<String>("displayName") ?: ""
                val mediaType = call.argument<String>("mediaType") ?: "downloads"
                val mimeType = call.argument<String>("mimeType") ?: "application/octet-stream"
                val allowUserInteractionFallback = call.argument<Boolean>("allowUserInteractionFallback") ?: false
                launchIo("exportToPublicCollection") {
                    StorageService.exportToPublicCollection(
                        this@MainActivity,
                        FilePath(sourcePath),
                        displayName,
                        MediaType(mediaType),
                        MimeType(mimeType),
                        allowUserInteractionFallback,
                        result,
                    )
                }
                return@setMethodCallHandler
            }

            if (call.method == "syncEditedExportedFile") {
                val sourcePath = call.argument<String>("sourcePath") ?: ""
                val exportedPath = call.argument<String>("exportedPath") ?: ""
                launchIo("syncEditedExportedFile") {
                    StorageService.syncEditedExportedFile(
                        this@MainActivity,
                        sourcePath,
                        exportedPath,
                        result,
                    )
                }
                return@setMethodCallHandler
            }

            if (call.method == "deleteExportedFile") {
                val exportedPath = call.argument<String>("exportedPath") ?: ""
                launchIo("deleteExportedFile") {
                    StorageService.deleteExportedFile(this@MainActivity, exportedPath, result)
                }
                return@setMethodCallHandler
            }

            result.notImplemented()
        }

        val channel = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "com.example.ytdown/notification"
        )
        channel.setMethodCallHandler { call, result ->
            if (call.method == "getInitialIntent") {
                result.success(null)
                return@setMethodCallHandler
            }
            result.notImplemented()
        }
        notificationChannel = channel
        Log.i("YTDown_Diagnostic", "SENTINELA: Fim do configureFlutterEngine")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        try {
            notificationChannel?.invokeMethod("onNotificationClick", null)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao invocar onNotificationClick: ${e.message}", e)
        }
    }
}
