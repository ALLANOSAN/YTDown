package com.example.ytdown.core.infrastructure

import android.content.Context
import com.chaquo.python.Python
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PythonMetadataBridge - Único ponto de contato com o pipeline Mutagen.
 */
@Singleton
class PythonMetadataBridge @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val py by lazy { Python.getInstance() }
    private val metadataModule by lazy { py.getModule("metadata") }

    suspend fun embedAlbumArtwork(audioPath: String, coverPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = metadataModule.callAttr("embed_album_art", audioPath, coverPath).toString()
            JSONObject(result).optBoolean("success", false)
        } catch (e: Exception) {
            Timber.tag("Artwork").e(e, "Erro ao embutir artwork via Mutagen")
            false
        }
    }

    suspend fun updateMetadata(audioPath: String, metadata: JSONObject): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = metadataModule.callAttr("update_music_metadata", audioPath, metadata.toString()).toString()
            JSONObject(result).optBoolean("success", false)
        } catch (e: Exception) {
            Timber.tag("Artwork").e(e, "Erro ao atualizar metadados via Mutagen")
            false
        }
    }

    fun extractMetadataFromFilename(filename: String): Pair<String, String> {
        return try {
            val result = metadataModule.callAttr("extract_metadata_from_filename", filename).toString()
            val json = JSONObject(result)
            val artist = json.optString("artist", "Unknown")
            val title = json.optString("title", filename)
            artist to title
        } catch (e: Exception) {
            "Unknown" to filename
        }
    }
}