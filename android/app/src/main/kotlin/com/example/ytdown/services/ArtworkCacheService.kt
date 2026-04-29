package com.example.ytdown.services

import com.example.ytdown.utils.MemoryLruCache
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtworkCacheService @Inject constructor(
    private val lastfmService: LastfmService
) {
    private val artistCache = MemoryLruCache<String, String>(256)
    private val albumCache = MemoryLruCache<String, String>(256)
    private val trackCache = MemoryLruCache<String, String>(256)

    suspend fun getArtistImage(artist: String): String? {
        if (artist.isBlank()) return null
        val key = artist.trim().lowercase()
        return artistCache.get(key) ?: lastfmService.getArtistImage(artist)?.also { artistCache.put(key, it) }
    }

    suspend fun getAlbumCover(artist: String, album: String): String? {
        if (artist.isBlank() || album.isBlank()) return null
        val key = "${artist.trim().lowercase()}::${album.trim().lowercase()}"
        return albumCache.get(key) ?: lastfmService.getAlbumCover(artist, album)?.also { albumCache.put(key, it) }
    }

    suspend fun getTrackCover(artist: String, title: String): String? {
        if (artist.isBlank() || title.isBlank()) return null
        val key = "${artist.trim().lowercase()}::${title.trim().lowercase()}"
        return trackCache.get(key) ?: lastfmService.getTrackCover(artist, title)?.also { trackCache.put(key, it) }
    }

    suspend fun clear(includeUpstream: Boolean = true) {
        artistCache.clear()
        albumCache.clear()
        trackCache.clear()
        if (includeUpstream) lastfmService.clearCache()
    }
}
