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
        coverPath: String? = null,
        year: String? = null,
        trackNumber: String? = null,
        discNumber: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val py = Python.getInstance()
        val module = py.getModule("metadata")

        // Chama a função rewrite_file_metadata com todos os campos
        val result = module.callAttr(
            "rewrite_file_metadata",
            audioPath, title, artist, album, coverPath, null, year, trackNumber, discNumber
        )

        val json = JSONObject(result.toString())
        json.optBoolean("success", false)
    }

    /**
     * Escreve metadados completos no arquivo, usado pelo ArtworkEnricher
     * e BatchMetadataFixWorker. Aceita artwork local em vez de URL.
     */
    suspend fun writeFullMetadata(
        path: String,
        title: String,
        artist: String,
        album: String,
        year: String? = null,
        trackNumber: String? = null,
        discNumber: String? = null,
        albumArt: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val py = Python.getInstance()
        val module = py.getModule("metadata")

        // rewrite_file_metadata aceita artwork_url (String?). Se albumArt for local,
        // passamos como artwork_url. O Mutagen embeda a imagem.
        val result = module.callAttr(
            "rewrite_file_metadata",
            path, title, artist, album, albumArt, null, year, trackNumber, discNumber
        )

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

    /**
     * Lê metadados existentes de um arquivo de áudio.
     * Se já tiver título, artista e álbum, o arquivo não precisa ser enriquecido.
     */
    fun readExistingMetadata(path: String): Map<String, String?> {
        val py = Python.getInstance()
        val module = py.getModule("metadata")
        return try {
            val result = module.callAttr("read_file_metadata", path).toString()
            val json = JSONObject(result)
            mapOf(
                "title" to json.optString("title", "").ifBlank { null },
                "artist" to json.optString("artist", "").ifBlank { null },
                "album" to json.optString("album", "").ifBlank { null },
                "track_number" to json.optString("track_number", "").ifBlank { null },
                "disc_number" to json.optString("disc_number", "").ifBlank { null },
                "year" to json.optString("year", "").ifBlank { null }
            )
        } catch (e: Exception) {
            mapOf(
                "title" to null, "artist" to null, "album" to null,
                "track_number" to null, "disc_number" to null, "year" to null
            )
        }
    }
}
