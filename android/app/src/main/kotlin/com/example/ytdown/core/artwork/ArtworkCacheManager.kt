package com.example.ytdown.core.artwork

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtworkCacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val albumCacheDir = File(context.cacheDir, "artwork/albums").apply { if (!exists()) mkdirs() }
    private val artistCacheDir = File(context.cacheDir, "artwork/artists").apply { if (!exists()) mkdirs() }

    fun getCacheKey(artist: String, album: String): String = md5("$artist-$album")
    fun getArtistCacheKey(artist: String): String = md5(artist)

    fun getCachedAlbumArt(cacheKey: String): File? {
        val file = File(albumCacheDir, "$cacheKey.jpg")
        return if (file.exists()) file else null
    }

    fun getCachedArtistArt(cacheKey: String): File? {
        val file = File(artistCacheDir, "$cacheKey.jpg")
        return if (file.exists()) file else null
    }

    suspend fun saveToAlbumCache(cacheKey: String, bytes: ByteArray): File = withContext(Dispatchers.IO) {
        val file = File(albumCacheDir, "$cacheKey.jpg")
        file.writeBytes(bytes)
        file
    }

    suspend fun saveToArtistCache(cacheKey: String, bytes: ByteArray): File = withContext(Dispatchers.IO) {
        val file = File(artistCacheDir, "$cacheKey.jpg")
        file.writeBytes(bytes)
        file
    }

    private fun md5(input: String): String {
        return MessageDigest.getInstance("MD5")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
