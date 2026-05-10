package com.example.ytdown.services

import com.google.gson.Gson
import com.example.ytdown.PythonBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetalArchivesService @Inject constructor() {
    private val gson = Gson()

    suspend fun discoverSimilarBands(bandName: String): MetalDiscoveryResponse = withContext(Dispatchers.IO) {
        try {
            val jsonResponse = PythonBridge.invokePythonJson("get_similar_bands", bandName)
            gson.fromJson(jsonResponse, MetalDiscoveryResponse::class.java)
        } catch (e: Exception) {
            MetalDiscoveryResponse(success = false, error = e.message ?: "Erro ao buscar bandas similares")
        }
    }

    suspend fun getBandDetails(bandName: String): BandDetailsResponse = withContext(Dispatchers.IO) {
        try {
            val jsonResponse = PythonBridge.invokePythonJson("get_band_details", bandName)
            gson.fromJson(jsonResponse, BandDetailsResponse::class.java)
        } catch (e: Exception) {
            BandDetailsResponse(success = false, error = e.message ?: "Erro")
        }
    }

    suspend fun getBandAlbums(bandName: String): BandAlbumsResponse = withContext(Dispatchers.IO) {
        try {
            val jsonResponse = PythonBridge.invokePythonJson("get_band_albums", bandName)
            gson.fromJson(jsonResponse, BandAlbumsResponse::class.java)
        } catch (e: Exception) {
            BandAlbumsResponse(success = false, error = e.message ?: "Erro")
        }
    }
}

data class MetalBand(val name: String, val genre: String? = null, val country: String? = null, val score: String? = null)
data class MetalAlbum(val name: String, val year: String)
data class MetalDiscoveryResponse(val success: Boolean, val bands: List<MetalBand>? = null, val error: String? = null)
data class BandDetailsResponse(val success: Boolean, val name: String? = null, val genre: String? = null, val image_url: String? = null, val error: String? = null)
data class BandAlbumsResponse(val success: Boolean, val albums: List<MetalAlbum>? = null, val error: String? = null)
