package com.example.ytdown.core.infrastructure

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageResolver @Inject constructor(private val context: Context) {

    fun internalBinariesDir(): File = context.filesDir

    fun nativeLibraryDir(): File = File(context.applicationInfo.nativeLibraryDir)

    fun privateDownloadsDir(isAudio: Boolean): File {
        var folder = "Videos"
        if (isAudio) folder = "Audios"
        val dir = File(context.filesDir, "YTDown/$folder")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Exporta um arquivo para a coleção pública.
     */
    fun exportToPublicCollection(sourceFile: File, isAudio: Boolean, displayName: String): Uri? {
        if (!sourceFile.exists()) return null

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            var mimeType = "video/mp4"
            if (isAudio) mimeType = "audio/mpeg"
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var relativeFolder = "${Environment.DIRECTORY_MOVIES}/YTDown"
                if (isAudio) relativeFolder = "${Environment.DIRECTORY_MUSIC}/YTDown"
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeFolder)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        var collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        if (isAudio) {
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
        }

        val uri = resolver.insert(collection, contentValues) ?: return null

        try {
            resolver.openOutputStream(uri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return null
        }
    }

    /**
     * Deleção Real do MediaStore (Migrado do Flutter StorageService -> deleteExportedFile).
     * Remove o arquivo da coleção pública de mídia no Android.
     */
    fun deleteFromPublicCollection(uriString: String?) {
        if (uriString == null) return
        try {
            val uri = Uri.parse(uriString)
            context.contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            android.util.Log.e("StorageResolver", "Falha ao deletar arquivo público: $uriString", e)
        }
    }
}
