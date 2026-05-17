package com.example.ytdown.core.artwork

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtworkCacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val albumsDir = File(context.cacheDir, "artwork/albums")
    private val artistsDir = File(context.cacheDir, "artwork/artists")

    init {
        albumsDir.mkdirs()
        artistsDir.mkdirs()
    }

    fun getAlbumArtworkPath(artist: String, album: String): String? {
        val fileName = "${md5("$artist-$album")}.jpg"
        val file = File(albumsDir, fileName)
        return if (file.exists()) file.absolutePath else null
    }

    fun getArtistArtworkPath(artist: String): String? {
        val fileName = "${md5(artist)}.jpg"
        val file = File(artistsDir, fileName)
        return if (file.exists()) file.absolutePath else null
    }

    // Helper MD5
    private fun md5(input: String): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
