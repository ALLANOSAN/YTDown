package com.example.ytdown

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.IOException

object StorageService {
    private const val TAG = "StorageService"
    private var pendingSafExport: PendingSafExport? = null

    private data class PendingSafExport(
        val sourcePath: FilePath,
        val mimeType: MimeType,
        val diagnostics: MutableMap<String, Any>,
        val result: MethodChannel.Result,
    )

    fun cancelPendingSafExport(context: Context) {
        val pending = pendingSafExport ?: return
        pendingSafExport = null

        val diagnostics = HashMap(pending.diagnostics)
        diagnostics["strategy"] = "saf_create_document"
        diagnostics["stage"] = "activity_destroyed"

        runOnMainThread {
            try {
                pending.result.success(
                    mapOf(
                        "success" to false,
                        "error" to "Exportação interrompida: atividade finalizada",
                        "strategy" to "saf_create_document",
                        "stage" to "activity_destroyed",
                        "diagnostics" to diagnostics,
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao encerrar exportação SAF pendente: ${e.message}", e)
            }
        }
    }

    fun exportToPublicCollection(
        context: Context,
        sourcePath: FilePath,
        displayName: String,
        mediaType: MediaType,
        mimeType: MimeType,
        allowUserInteractionFallback: Boolean,
        result: MethodChannel.Result,
    ) {
        var stage = "init"
        var strategy = "none"
        val diagnostics = buildStorageDiagnostics(
            context,
            mapOf(
                "mediaType" to mediaType.value,
                "mimeType" to mimeType.value,
            )
        )

        try {
            val sourceFile = validateSourceFile(sourcePath, result, diagnostics) ?: return
            diagnostics["sourceSizeBytes"] = sourceFile.length()

            var fileName = displayName
            if (fileName.isBlank()) {
                fileName = sourceFile.name
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                exportToLegacyPublicDir(
                    sourceFile = sourceFile,
                    fileName = fileName,
                    mediaType = mediaType,
                    diagnostics = diagnostics,
                    result = result,
                )
                return
            }

            val targets = buildExportTargets(mediaType)
            val exported = exportToMediaStore(
                context = context,
                sourceFile = sourceFile,
                fileName = fileName,
                mimeType = mimeType.value,
                targets = targets,
                diagnostics = diagnostics,
                result = result,
            )

            if (exported) {
                return
            }

            if (allowUserInteractionFallback) {
                launchSafFallback(
                    activity = context as? Activity,
                    sourcePath = sourcePath,
                    displayName = fileName,
                    mimeType = mimeType,
                    diagnostics = diagnostics,
                    strategyErrors = listOf(diagnostics["strategyErrors"].toString()),
                    result = result,
                )
                return
            }

            throw IOException(
                "Falha em todas as estratégias de exportação: ${diagnostics["strategyErrors"]}"
            )
        } catch (e: Exception) {
            diagnostics["strategy"] = strategy
            diagnostics["stage"] = stage
            Log.e(TAG, "❌ Erro ao exportar arquivo: ${e.message}", e)
            respondWithMap(
                result,
                mapOf(
                    "success" to false,
                    "error" to (e.message ?: "Falha ao exportar arquivo"),
                    "strategy" to strategy,
                    "stage" to stage,
                    "diagnostics" to diagnostics,
                )
            )
        }
    }

    fun syncEditedExportedFile(
        context: Context,
        sourcePath: String,
        exportedPath: String,
        result: MethodChannel.Result,
    ) {
        var stage = "init"
        var strategy = "none"
        val diagnostics = buildStorageDiagnostics(
            context,
            mapOf(
                "exportedPath" to exportedPath,
            )
        )

        try {
            stage = "validate_source"
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                respondWithMap(
                    result,
                    mapOf(
                        "success" to false,
                        "error" to "Arquivo editado não encontrado para sincronização",
                        "strategy" to strategy,
                        "stage" to stage,
                        "diagnostics" to diagnostics,
                    )
                )
                return
            }

            diagnostics["sourceSizeBytes"] = sourceFile.length()
            val isContentUri = exportedPath.startsWith("content://")

            if (isContentUri) {
                strategy = "content_resolver_uri"
                stage = "write_uri"
                writeSourceFileToUri(context, sourceFile, Uri.parse(exportedPath))
            }
            if (!isContentUri) {
                strategy = "filesystem_path"
                stage = "copy_path"
                copySourceFileToPath(sourceFile, exportedPath)
            }

            diagnostics["strategy"] = strategy
            diagnostics["stage"] = "done"
            respondWithMap(
                result,
                mapOf(
                    "success" to true,
                    "exportedPath" to exportedPath,
                    "strategy" to strategy,
                    "stage" to "done",
                    "diagnostics" to diagnostics,
                )
            )
        } catch (e: Exception) {
            diagnostics["strategy"] = strategy
            diagnostics["stage"] = stage
            respondWithMap(
                result,
                mapOf(
                    "success" to false,
                    "error" to (e.message ?: "Falha ao sincronizar arquivo exportado"),
                    "strategy" to strategy,
                    "stage" to stage,
                    "diagnostics" to diagnostics,
                )
            )
        }
    }

    fun deleteExportedFile(
        context: Context,
        exportedPath: String,
        result: MethodChannel.Result,
    ) {
        if (exportedPath.isBlank()) {
            runOnMainThread { result.success(true) }
            return
        }

        try {
            if (exportedPath.startsWith("content://")) {
                val uri = Uri.parse(exportedPath)
                val deletedCount = context.contentResolver.delete(uri, null, null)
                runOnMainThread { result.success(deletedCount > 0) }
                return
            }

            val file = File(exportedPath)
            val success = if (file.exists()) file.delete() else true
            runOnMainThread { result.success(success) }
        } catch (e: Exception) {
            runOnMainThread { result.success(false) }
        }
    }

    fun handleSafExportResult(
        context: Context,
        resultCode: Int,
        data: Intent?,
    ) {
        val pending = pendingSafExport ?: return
        pendingSafExport = null

        val diagnostics = HashMap(pending.diagnostics)
        diagnostics["strategy"] = "saf_create_document"

        if (resultCode != Activity.RESULT_OK || data?.data == null) {
            diagnostics["stage"] = "saf_cancelled"
            runOnMainThread {
                pending.result.success(
                    mapOf(
                        "success" to false,
                        "error" to "Exportação cancelada pelo usuário",
                        "strategy" to "saf_create_document",
                        "stage" to "saf_cancelled",
                        "diagnostics" to diagnostics,
                    )
                )
            }
            return
        }

        val targetUri = data.data!!

        try {
            diagnostics["stage"] = "saf_write"
            val sourceFile = File(pending.sourcePath.value)
            if (!sourceFile.exists()) {
                throw IOException("Arquivo origem não encontrado para exportação SAF")
            }

            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: throw IOException("Falha ao abrir stream SAF de saída")

            diagnostics["stage"] = "saf_done"
            runOnMainThread {
                pending.result.success(
                    mapOf(
                        "success" to true,
                        "contentUri" to targetUri.toString(),
                        "exportedPath" to targetUri.toString(),
                        "strategy" to "saf_create_document",
                        "stage" to "saf_done",
                        "diagnostics" to diagnostics,
                    )
                )
            }
        } catch (e: Exception) {
            diagnostics["stage"] = "saf_failed"
            runOnMainThread {
                pending.result.success(
                    mapOf(
                        "success" to false,
                        "error" to (e.message ?: "Falha ao exportar via SAF"),
                        "strategy" to "saf_create_document",
                        "stage" to "saf_failed",
                        "diagnostics" to diagnostics,
                    )
                )
            }
        }
    }

    private fun buildStorageDiagnostics(
        context: Context,
        extra: Map<String, Any?> = emptyMap(),
    ): MutableMap<String, Any> {
        return mutableMapOf<String, Any>(
            "manufacturer" to (Build.MANUFACTURER ?: "unknown"),
            "brand" to (Build.BRAND ?: "unknown"),
            "model" to (Build.MODEL ?: "unknown"),
            "sdkInt" to Build.VERSION.SDK_INT,
            "androidRelease" to (Build.VERSION.RELEASE ?: "unknown"),
            "packageName" to context.packageName,
        ).apply {
            extra.forEach { (key, value) ->
                if (value != null) {
                    put(key, value)
                }
            }
        }
    }

    private fun buildExportTargets(mediaType: MediaType): List<ExportTarget> {
        if (mediaType.isAudio()) {
            return listOf(
                ExportTarget(
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    "${Environment.DIRECTORY_MUSIC}/YTDown",
                    "mediastore_audio",
                ),
                ExportTarget(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${Environment.DIRECTORY_DOWNLOADS}/YTDown",
                    "mediastore_downloads_fallback",
                ),
            )
        }
        if (mediaType.isVideo()) {
            return listOf(
                ExportTarget(
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    "${Environment.DIRECTORY_MOVIES}/YTDown",
                    "mediastore_video",
                ),
                ExportTarget(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${Environment.DIRECTORY_DOWNLOADS}/YTDown",
                    "mediastore_downloads_fallback",
                ),
            )
        }
        return listOf(
            ExportTarget(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${Environment.DIRECTORY_DOWNLOADS}/YTDown",
                "mediastore_downloads",
            ),
        )
    }

    private fun exportToMediaStore(
        context: Context,
        sourceFile: File,
        fileName: String,
        mimeType: String,
        targets: List<ExportTarget>,
        diagnostics: MutableMap<String, Any>,
        result: MethodChannel.Result,
    ): Boolean {
        val resolver = context.contentResolver
        val strategyErrors = mutableListOf<String>()

        for (target in targets) {
            val strategy = target.strategy
            diagnostics["strategy"] = strategy
            diagnostics["stage"] = "insert"

            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, target.relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = resolver.insert(target.collection, values)
                    ?: throw IOException("Falha ao criar item no MediaStore")

                try {
                    diagnostics["stage"] = "write"
                    resolver.openOutputStream(uri)?.use { output ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    } ?: throw IOException("Falha ao abrir stream de saída")

                    diagnostics["stage"] = "publish"
                    val publishValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(uri, publishValues, null, null)

                    diagnostics["strategy"] = strategy
                    diagnostics["stage"] = diagnostics["stage"] ?: "publish"

                    respondWithMap(
                        result,
                        mapOf(
                            "success" to true,
                            "contentUri" to uri.toString(),
                            "exportedPath" to uri.toString(),
                            "strategy" to strategy,
                            "stage" to diagnostics["stage"],
                            "diagnostics" to diagnostics,
                        )
                    )
                    return true
                } catch (e: Exception) {
                    diagnostics["stage"] = "cleanup"
                    resolver.delete(uri, null, null)
                    throw e
                }
            } catch (e: Exception) {
                strategyErrors.add("${strategy}:${e.message ?: "erro desconhecido"}")
            }
        }

        diagnostics["strategyErrors"] = strategyErrors.joinToString(" | ")
        return false
    }

    private fun exportToLegacyPublicDir(
        sourceFile: File,
        fileName: String,
        mediaType: MediaType,
        diagnostics: MutableMap<String, Any>,
        result: MethodChannel.Result,
    ) {
        diagnostics["strategy"] = "legacy_public_dir"
        diagnostics["stage"] = "legacy_copy"

        var baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (mediaType.isAudio()) {
            baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        }
        if (mediaType.isVideo()) {
            baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        }

        val targetDir = File(baseDir, "YTDown")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val targetFile = File(targetDir, fileName)
        sourceFile.copyTo(targetFile, overwrite = true)

        respondWithMap(
            result,
            mapOf(
                "success" to true,
                "exportedPath" to targetFile.absolutePath,
                "strategy" to diagnostics["strategy"],
                "stage" to diagnostics["stage"],
                "diagnostics" to diagnostics,
            )
        )
    }

    private fun validateSourceFile(
        sourcePath: FilePath,
        result: MethodChannel.Result,
        diagnostics: MutableMap<String, Any>,
    ): File? {
        val file = sourcePath.toFile()
        if (file.exists()) {
            return file
        }

        respondWithMap(
            result,
            mapOf(
                "success" to false,
                "error" to "Arquivo origem não encontrado",
                "stage" to "validate_source",
                "strategy" to "none",
                "diagnostics" to diagnostics,
            )
        )
        return null
    }

    private fun writeSourceFileToUri(
        context: Context,
        sourceFile: File,
        targetUri: Uri,
    ) {
        context.contentResolver.openOutputStream(targetUri)?.use { output ->
            sourceFile.inputStream().use { input ->
                input.copyTo(output)
            }
        } ?: throw IOException("Falha ao abrir stream de saída para URI exportada")

        context.contentResolver.notifyChange(targetUri, null)
    }

    private fun copySourceFileToPath(
        sourceFile: File,
        exportedPath: String,
    ) {
        val targetFile = File(exportedPath)
        val parent = targetFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        sourceFile.copyTo(targetFile, overwrite = true)
    }

    private fun launchSafFallback(
        activity: Activity?,
        sourcePath: FilePath,
        displayName: String,
        mimeType: MimeType,
        diagnostics: MutableMap<String, Any>,
        strategyErrors: List<String>,
        result: MethodChannel.Result,
    ) {
        if (activity == null) {
            diagnostics["strategy"] = "saf_create_document"
            diagnostics["stage"] = "saf_no_activity"
            diagnostics["strategyErrors"] = strategyErrors.joinToString(" | ")
            respondWithMap(
                result,
                mapOf(
                    "success" to false,
                    "error" to "Activity não disponível para fallback SAF",
                    "strategy" to "saf_create_document",
                    "stage" to "saf_no_activity",
                    "diagnostics" to diagnostics,
                )
            )
            return
        }

        if (pendingSafExport != null) {
            val localDiagnostics = HashMap(diagnostics)
            localDiagnostics["strategy"] = "saf_create_document"
            localDiagnostics["stage"] = "saf_busy"
            localDiagnostics["strategyErrors"] = strategyErrors.joinToString(" | ")

            runOnMainThread {
                result.success(
                    mapOf(
                        "success" to false,
                        "error" to "Já existe uma exportação interativa em andamento",
                        "strategy" to "saf_create_document",
                        "stage" to "saf_busy",
                        "diagnostics" to localDiagnostics,
                    )
                )
            }
            return
        }

        try {
            diagnostics["strategy"] = "saf_create_document"
            diagnostics["stage"] = "saf_launch"
            diagnostics["strategyErrors"] = strategyErrors.joinToString(" | ")

            pendingSafExport = PendingSafExport(
                sourcePath = sourcePath,
                mimeType = mimeType,
                diagnostics = diagnostics,
                result = result,
            )

            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeType.value
                putExtra(Intent.EXTRA_TITLE, displayName)
            }

            activity.startActivityForResult(intent, SAF_EXPORT_REQUEST_CODE)
        } catch (e: Exception) {
            pendingSafExport = null
            diagnostics["strategy"] = "saf_create_document"
            diagnostics["stage"] = "saf_launch_failed"
            diagnostics["strategyErrors"] = strategyErrors.joinToString(" | ")

            runOnMainThread {
                result.success(
                    mapOf(
                        "success" to false,
                        "error" to (e.message ?: "Falha ao iniciar seletor SAF"),
                        "strategy" to "saf_create_document",
                        "stage" to "saf_launch_failed",
                        "diagnostics" to diagnostics,
                    )
                )
            }
        }
    }
}
