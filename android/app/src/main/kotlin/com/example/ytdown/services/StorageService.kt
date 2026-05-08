package com.example.ytdown.services

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import com.example.ytdown.core.domain.*
import com.example.ytdown.utils.LocalLogger

private data class ExportTarget(
    val collection: Uri,
    val relativePath: String,
    val strategy: String
)

/**
 * FIX #2: Converted from `object` to `class` injected via Hilt.
 * This allows proper unit testing with mocked dependencies.
 * Kept companion object accessor for backward compatibility during migration.
 */
class StorageService @javax.inject.Inject constructor() {

    companion object {
        private const val TAG = "StorageService"

        /** FIX #8: Deprecated — use ActivityResultContracts.CreateDocument instead */
        @Deprecated("Use ActivityResultContracts.CreateDocument from Compose UI layer")
        const val SAF_EXPORT_REQUEST_CODE = 1001

        // Backward-compatible singleton for any code still referencing StorageService directly
        @Volatile
        private var instance: StorageService? = null

        /** Backward-compatible accessor — prefer injection via Hilt */
        fun getInstance(): StorageService {
            return instance ?: synchronized(this) {
                instance ?: StorageService().also { instance = it }
            }
        }
    }

    private var pendingSafExport: PendingSafExport? = null

    private data class PendingSafExport(
        val sourcePath: StoragePath,
        val mimeType: StorageMimeType,
        val diagnostics: MutableMap<String, Any>,
    )

    fun cancelPendingSafExport(context: Context) {
        val pending = pendingSafExport ?: return
        pendingSafExport = null
        LocalLogger.info("SAF export cancelled for ${context.packageName}: ${pending.sourcePath.value}")
    }

    fun exportToPublicCollection(
        context: Context,
        sourcePath: StoragePath,
        displayName: String,
        mediaType: StorageMediaType,
        mimeType: StorageMimeType,
        allowUserInteractionFallback: Boolean,
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
            val sourceFile = validateSourceFile(sourcePath) ?: return
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
                )
                return
            }

            throw IOException(
                "Falha em todas as estratégias de exportação: ${diagnostics["strategyErrors"]}"
            )
        } catch (e: Exception) {
            diagnostics["strategy"] = strategy
            diagnostics["stage"] = stage
            LocalLogger.error("Erro ao exportar arquivo: ${e.message}", e)
        }
    }

    suspend fun syncEditedExportedFile(
        context: Context,
        sourcePath: String,
        exportedPath: String,
    ): Boolean {
        return try {
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                android.util.Log.e(TAG, "Source file not found: $sourcePath")
                return false
            }

            val isContentUri = exportedPath.startsWith("content://")

            if (isContentUri) {
                writeSourceFileToUri(context, sourceFile, Uri.parse(exportedPath))
            } else {
                copySourceFileToPath(sourceFile, exportedPath)
            }

            android.util.Log.d(TAG, "Sync successful: $sourcePath -> $exportedPath")
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Sync failed: ${e.message}", e)
            false
        }
    }

    fun deleteExportedFile(
        context: Context,
        exportedPath: String,
    ): Boolean {
        if (exportedPath.isBlank()) {
            return true
        }

        return try {
            var isSuccess = false
            if (exportedPath.startsWith("content://")) {
                val uri = Uri.parse(exportedPath)
                isSuccess = context.contentResolver.delete(uri, null, null) > 0
            }
            if (!exportedPath.startsWith("content://")) {
                val file = File(exportedPath)
                isSuccess = !file.exists() || file.delete()
            }
            isSuccess
        } catch (e: Exception) {
            false
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
        } catch (e: Exception) {
            diagnostics["stage"] = "saf_failed"
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

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun buildExportTargets(mediaType: StorageMediaType): List<ExportTarget> {
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

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun exportToMediaStore(
        context: Context,
        sourceFile: File,
        fileName: String,
        mimeType: String,
        targets: List<ExportTarget>,
        diagnostics: MutableMap<String, Any>,
    ): Boolean {
        val resolver = context.contentResolver
        val strategyErrors = mutableListOf<String>()

        for (target in targets) {
            val strategy = target.strategy
            diagnostics["strategy"] = strategy
            diagnostics["stage"] = "insert"

            try {
                val existingUri = findExistingInMediaStore(context, target.collection, fileName, target.relativePath)
                if (existingUri != null) {
                    diagnostics["stage"] = "duplicate_overwrite"
                    LocalLogger.info("Duplicata detectada no MediaStore, sobrescrevendo: $fileName")
                    resolver.openOutputStream(existingUri, "wt")?.use { output ->
                        sourceFile.inputStream().use { input -> input.copyTo(output) }
                    } ?: throw IOException("Falha ao abrir stream para sobrescrita")
                    val publishValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(existingUri, publishValues, null, null)
                    diagnostics["stage"] = "duplicate_overwrite_done"
                    return true
                }

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

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun findExistingInMediaStore(
        context: Context,
        collection: Uri,
        fileName: String,
        relativePath: String
    ): Uri? {
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(fileName, "$relativePath/")

        return resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                Uri.withAppendedPath(collection, id.toString())
            } else null
        }
    }

    private fun exportToLegacyPublicDir(
        sourceFile: File,
        fileName: String,
        mediaType: StorageMediaType,
        diagnostics: MutableMap<String, Any>,
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
        if (targetFile.exists()) {
            diagnostics["stage"] = "legacy_duplicate_overwrite"
            LocalLogger.info("Duplicata encontrada (legacy), sobrescrevendo: $fileName")
        }
        sourceFile.copyTo(targetFile, overwrite = true)
    }

    private fun validateSourceFile(
        sourcePath: StoragePath,
    ): File? {
        val file = sourcePath.toFile()
        if (file.exists()) {
            return file
        }
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

    @Deprecated("Use ActivityResultContracts.CreateDocument from Compose UI layer")
    fun launchSafFallback(
        activity: Activity?,
        sourcePath: StoragePath,
        displayName: String,
        mimeType: StorageMimeType,
        diagnostics: MutableMap<String, Any>,
        strategyErrors: List<String>,
    ) {
        if (activity == null) {
            diagnostics["strategy"] = "saf_create_document"
            diagnostics["stage"] = "saf_no_activity"
            diagnostics["strategyErrors"] = strategyErrors.joinToString(" | ")
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
            )

            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeType.value
                putExtra(Intent.EXTRA_TITLE, displayName)
            }

            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, SAF_EXPORT_REQUEST_CODE)
        } catch (e: Exception) {
            pendingSafExport = null
            diagnostics["strategy"] = "saf_create_document"
            diagnostics["stage"] = "saf_launch_failed"
            diagnostics["strategyErrors"] = strategyErrors.joinToString(" | ")

            LocalLogger.error("Erro ao iniciar seletor SAF", e)
        }
    }
}
