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
     * BUG #4 FIX: antes o MIME type era sempre "audio/mpeg" (mp3) ou "video/mp4",
     * independente do formato real. Arquivos m4a, flac, opus etc ficavam inacessíveis
     * para players ou apareciam corrompidos na galeria.
     * Agora resolve o MIME correto a partir da extensão real do arquivo.
     */
    private fun resolveMimeType(format: String, isAudio: Boolean): String {
        return when (format.lowercase().trimStart('.')) {
            "m4a", "aac" -> "audio/mp4"
            "flac"       -> "audio/flac"
            "ogg"        -> "audio/ogg"
            "opus"       -> "audio/ogg"
            "wav"        -> "audio/wav"
            "mp3"        -> "audio/mpeg"
            "mp4"        -> "video/mp4"
            "mkv"        -> "video/x-matroska"
            "webm"       -> if (isAudio) "audio/webm" else "video/webm"
            "avi"        -> "video/x-msvideo"
            else         -> if (isAudio) "audio/mpeg" else "video/mp4"
        }
    }

    /**
     * Exporta um arquivo para a coleção pública.
     * @param format extensão do arquivo (ex: "mp3", "m4a", "mp4"). Usado para
     *               determinar o MIME type correto no MediaStore.
     */
    fun exportToPublicCollection(
        sourceFile: File,
        isAudio: Boolean,
        displayName: String,
        format: String = ""
    ): Uri? {
        if (!sourceFile.exists()) return null

        // Resolve formato a partir do parâmetro ou da extensão real do arquivo
        val resolvedFormat = format.ifBlank {
            sourceFile.extension.ifBlank { if (isAudio) "mp3" else "mp4" }
        }
        val mimeType = resolveMimeType(resolvedFormat, isAudio)

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relativeFolder = if (isAudio)
                    "${Environment.DIRECTORY_MUSIC}/YTDown"
                else
                    "${Environment.DIRECTORY_MOVIES}/YTDown"
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeFolder)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        var collection = if (isAudio) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
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
     * Deleção Real do MediaStore.
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
