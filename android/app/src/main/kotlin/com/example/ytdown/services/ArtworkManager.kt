package com.example.ytdown.services

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtworkManager @Inject constructor(
    private val cacheService: ArtworkCacheService
) {
    // ConcurrentHashMap — thread-safe sem precisar de synchronized/mutex
    // mutableMapOf() não é thread-safe com coroutines concorrentes
    private val artistCache = ConcurrentHashMap<String, String?>()
    private val albumCache  = ConcurrentHashMap<String, String?>()
    private val trackCache  = ConcurrentHashMap<String, String?>()


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
        if (value != null) cache[key] = value
        return value
    }

    private fun normalize(value: String?): String {
        return value?.trim()?.lowercase() ?: ""
    }

    private fun normalizePair(first: String, second: String): String {
        return "${normalize(first)}|${normalize(second)}"
    }
}
