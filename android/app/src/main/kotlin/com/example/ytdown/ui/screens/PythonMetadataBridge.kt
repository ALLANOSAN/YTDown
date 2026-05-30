package com.example.ytdown.core.metadata

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ponte de comunicação para executar scripts Python de manipulação de metadados (Mutagen).
 */
@Singleton
class PythonMetadataBridge @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val TAG = "PythonMetadataBridge"

    /**
     * Incorpora a capa do álbum diretamente no arquivo de áudio.
     */
    fun embedAlbumArtwork(audioPath: String, coverPath: String) {
        try {
            val py = Python.getInstance()
            val module = py.getModule("metadata_pipeline")
            Log.d(TAG, "Python: Embedding artwork em $audioPath")
            module.callAttr("embed_artwork", audioPath, coverPath)
        } catch (e: Exception) {
            Log.e(TAG, "Erro Python ao embutir capa: ${e.message}")
        }
    }

    /**
     * Grava metadados ID3/Vorbis completos no arquivo.
     */
    fun writeFullMetadata(path: String, title: String, artist: String, album: String, year: String?, albumArt: String?, trackNumber: String? = null) {
        try {
            val py = Python.getInstance()
            val module = py.getModule("metadata_pipeline")
            Log.d(TAG, "Python: Gravando tags para $title (year=$year, track=$trackNumber)")
            module.callAttr("write_metadata", path, title, artist, album, year, albumArt, trackNumber)
        } catch (e: Exception) {
            Log.e(TAG, "Erro Python ao gravar metadados: ${e.message}")
        }
    }

    /**
     * Extrai metadados do nome do arquivo.
     */
    fun extractMetadataFromFilename(filename: String): Map<String, String?> {
        return try {
            val py = Python.getInstance()
            val module = py.getModule("metadata_pipeline")
            val resultJson = module.callAttr("extract_metadata_from_filename", filename).toString()
            val json = JSONObject(resultJson)
            
            val artist = json.optString("artist").takeIf { it != "Unknown" && it.isNotBlank() }
            val title = json.optString("title").takeIf { it != "Unknown" && it.isNotBlank() }
            val album = json.optString("album").takeIf { it != "Unknown" && it.isNotBlank() }
            
            mapOf(
                "artist" to artist,
                "title" to title,
                "album" to album
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao extrair metadados: ${e.message}")
            mapOf("artist" to null, "title" to filename, "album" to null)
        }
    }
}