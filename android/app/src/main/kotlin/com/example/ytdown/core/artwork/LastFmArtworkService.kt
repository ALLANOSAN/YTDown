package com.example.ytdown.core.artwork

import com.google.gson.annotations.SerializedName
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LastFmArtworkService @Inject constructor() {
    // Implementação simplificada para o Service, a integração completa com Retrofit
    // ocorreria em um módulo de rede separado, mas aqui definimos o contrato.
    
    suspend fun fetchAlbumArtwork(artist: String, album: String): String? {
        // Exemplo: URL formatada com a API Key de BuildConfig.LASTFM_API_KEY
        return null // Placeholder para implementação real
    }

    suspend fun fetchArtistArtwork(artist: String): String? {
        return null // Placeholder
    }
}
