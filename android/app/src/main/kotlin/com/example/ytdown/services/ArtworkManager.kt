package com.example.ytdown.services

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtworkManager @Inject constructor(
    private val cacheService: ArtworkCacheService
) {
    private val artistCache = mutableMapOf<String, String?>()
    private val albumCache = mutableMapOf<String, String?>()
    private val trackCache = mutableMapOf<String, String?>()


    suspend fun getArtistImage(artist: String): String? {
        val key = normalize(artist)
        return cacheService.getArtistImage(artist)
            ?: artistCache[key]
            ?: cacheAndReturn(artistCache, key) { cacheService.getArtistImage(artist) }
    }

    suspend fun getAlbumCover(artist: String, album: String): String? {
        val key = normalizePair(artist, album)
        return cacheService.getAlbumCover(artist, album)
            ?: albumCache[key]
            ?: cacheAndReturn(albumCache, key) { cacheService.getAlbumCover(artist, album) }
    }

    suspend fun getTrackCover(artist: String, title: String): String? {
        val key = normalizePair(artist, title)
        return cacheService.getTrackCover(artist, title)
            ?: trackCache[key]
            ?: cacheAndReturn(trackCache, key) { cacheService.getTrackCover(artist, title) }
    }

    suspend fun clear(includeService: Boolean = false) {
        artistCache.clear()
        albumCache.clear()
        trackCache.clear()
        if (includeService) cacheService.clear()
    }

    private suspend fun cacheAndReturn(
        cache: MutableMap<String, String?>,
        key: String,
        loader: suspend () -> String?
    ): String? {
        val value = loader()
        cache[key] = value
        return value
    }

    private fun normalize(value: String?): String {
        return value?.trim()?.lowercase() ?: ""
    }

    private fun normalizePair(first: String, second: String): String {
        return "${normalize(first)}|${normalize(second)}"
    }
}
