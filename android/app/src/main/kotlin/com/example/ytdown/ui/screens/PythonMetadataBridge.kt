package com.example.ytdown.core.metadata

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ponte de comunicação para executar scripts Python de manipulação de metadados (Mutagen).
 */
@Singleton
class PythonMetadataBridge @Inject constructor(
    @ApplicationContext private val context: Context
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
    fun writeFullMetadata(path: String, title: String, artist: String, album: String, year: String?, albumArt: String?) {
        try {
            val py = Python.getInstance()
            val module = py.getModule("metadata_pipeline")
            Log.d(TAG, "Python: Gravando tags para $title ($year)")
            module.callAttr("write_metadata", path, title, artist, album, year, albumArt)
        } catch (e: Exception) {
            Log.e(TAG, "Erro Python ao gravar metadados: ${e.message}")
        }
    }

    /**
     * Extrai metadados do nome do arquivo.
     */
    fun extractMetadataFromFilename(filename: String): Map<String, String> {
        return try {
            val py = Python.getInstance()
            val module = py.getModule("metadata_pipeline")
            val resultJson = module.callAttr("extract_metadata_from_filename", filename).toString()
            val json = JSONObject(resultJson)
            mapOf(
                "artist" to json.optString("artist", "Unknown"),
                "title" to json.optString("title", "Unknown"),
                "album" to json.optString("album", "Unknown")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao extrair metadados: ${e.message}")
            mapOf("artist" to "Unknown", "title" to filename, "album" to "Unknown")
        }
    }
}