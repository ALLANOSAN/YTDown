package com.example.ytdown.core.artwork

import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class PythonMetadataBridge @Inject constructor() {

    /**
     * Centraliza a escrita de tags e arte de capa via pipeline Python/Mutagen.
     */
    suspend fun updateMusicMetadata(
        audioPath: String,
        title: String,
        artist: String,
        album: String,
        coverPath: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val py = Python.getInstance()
        val module = py.getModule("metadata")
        
        // Chama a função rewrite_file_metadata existente para tags e capa (se fornecida)
        val result = module.callAttr("rewrite_file_metadata", audioPath, title, artist, album, coverPath)
        
        val json = JSONObject(result.toString())
        json.optBoolean("success", false)
    }

    /**
     * Embed de capa de álbum específica usando o pipeline existente.
     */
    suspend fun embedAlbumArtwork(audioPath: String, coverPath: String): Boolean = withContext(Dispatchers.IO) {
        val py = Python.getInstance()
        val module = py.getModule("metadata")
        val result = module.callAttr("embed_album_art", audioPath, coverPath)
        
        val json = JSONObject(result.toString())
        json.optBoolean("success", false)
    }

    /**
     * Extração inteligente de metadata via nome de arquivo como fallback.
     */
    fun extractMetadataFromFilename(filename: String): Map<String, String> {
        val py = Python.getInstance()
        val module = py.getModule("metadata")
        return try {
            val result = module.callAttr("extract_metadata_from_filename", filename).toString()
            val json = JSONObject(result)
            mapOf(
                "artist" to json.optString("artist", "Unknown"),
                "title" to json.optString("title", filename)
            )
        } catch (e: Exception) {
            mapOf("artist" to "Unknown", "title" to filename)
        }
    }
}
