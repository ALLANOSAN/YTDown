package com.example.ytdown.core.artwork

import android.content.Context
import com.example.ytdown.utils.MetadataUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtworkCacheManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val albumCacheDir = File(context.cacheDir, "artwork/albums").apply { if (!exists()) mkdirs() }
    private val artistCacheDir = File(context.cacheDir, "artwork/artists").apply { if (!exists()) mkdirs() }

    fun getCacheKey(artist: String, album: String): String = md5("$artist-$album")
    fun getArtistCacheKey(artist: String): String = md5(artist)

    /**
     * Chave da capa de uma faixa. Sem album conhecido (vazio ou sentinela
     * "YTDown") a chave e por faixa: md5("Petra-") era compartilhada por toda
     * faixa do Petra sem album, e a primeira capa baixada ia para todas.
     */
    fun chaveDaCapa(artist: String, album: String, title: String): String =
        if (MetadataUtils.isUnknownMetadata(album)) md5("$artist-faixa:$title")
        else getCacheKey(artist, album)

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
