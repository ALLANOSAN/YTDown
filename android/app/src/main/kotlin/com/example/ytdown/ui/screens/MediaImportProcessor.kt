package com.example.ytdown.core.media

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

import com.example.ytdown.core.domain.SongEntity
import com.example.ytdown.core.infrastructure.persistence.SongDao
import com.example.ytdown.core.metadata.MetadataExtractor
import com.example.ytdown.core.artwork.FanArtTvService
import com.example.ytdown.core.artwork.ArtworkCacheManager
import com.example.ytdown.core.metadata.PythonMetadataBridge
import com.example.ytdown.core.metadata.model.MusicBrainzRecording
import com.example.ytdown.services.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Singleton
class MediaImportProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataExtractor: MetadataExtractor,
    private val musicBrainzService: MusicBrainzService,
    private val coverArtService: CoverArtArchiveService,
    private val fanArtTvService: FanArtTvService,
    private val artworkCacheManager: ArtworkCacheManager,
    private val pythonMetadataBridge: PythonMetadataBridge,
    private val songDao: SongDao
) {
    /**
     * Processa qualquer arquivo de áudio adicionado ao app (Download ou Local)
     */
    suspend fun process(audioPath: String) {
        withContext(Dispatchers.IO) {
        // PASSO 1 — EXTRAIR TAGS LOCAIS
        val localMeta = metadataExtractor.extract(audioPath)
        
        val mbMetadata = musicBrainzService.searchRecording(
            title = localMeta.title,
            artist = localMeta.artist
        )

        val title: String = mbMetadata?.title ?: localMeta.title
        val artist: String = mbMetadata?.artist ?: localMeta.artist
        val album: String = mbMetadata?.album ?: localMeta.album
        val duration = localMeta.duration
        
        val releaseId = mbMetadata?.releaseId
        val artistId = mbMetadata?.artistId

        // PASSO 6 — CACHE INTELIGENTE
        val albumCacheKey = artworkCacheManager.getCacheKey(artist, album)
        val artistCacheKey = artworkCacheManager.getArtistCacheKey(artist)

        // PASSO 4 — COVER ART ARCHIVE (Album Art)
        var albumArtFile = artworkCacheManager.getCachedAlbumArt(albumCacheKey)
        if (albumArtFile == null && releaseId != null) {
            val bytes = coverArtService.downloadAlbumArt(releaseId)
            if (bytes != null) {
                albumArtFile = artworkCacheManager.saveToAlbumCache(albumCacheKey, bytes)
            }
        }
        val albumArtPath: String? = albumArtFile?.absolutePath

        // PASSO 5 — FANART.TV (Artist Art)
        var artistArtFile = artworkCacheManager.getCachedArtistArt(artistCacheKey)
        if (artistArtFile == null && artistId != null) {
            val bytes = fanArtTvService.downloadArtistImage(artistId)
            if (bytes != null) {
                artistArtFile = artworkCacheManager.saveToArtistCache(artistCacheKey, bytes)
            }
        }
        val artistArtPath: String? = artistArtFile?.absolutePath

        // PASSO 7 & 8 — EMBED & WRITE METADATA (Pipeline Python Unificado)
        try {
            // Embed da capa do álbum dentro do arquivo físico
            if (albumArtPath != null) {
                pythonMetadataBridge.embedAlbumArtwork(
                    audioPath = audioPath,
                    coverPath = albumArtPath
                )
            }

            // Escreve tags completas
            pythonMetadataBridge.writeFullMetadata(
                path = audioPath,
                title = title,
                artist = artist,
                album = album,
                albumArt = albumArtPath
            )
        } catch (e: Exception) {
            android.util.Log.e("ImportProcessor", "Erro no pipeline Python para: $audioPath", e)
        }

        // PASSO 9 — ROOM DATABASE (Persistência)
        val entity = SongEntity(
            path = audioPath,
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            albumArtwork = albumArtPath,
            artistArtwork = artistArtPath,
            addedAt = System.currentTimeMillis()
        )
        
        songDao.insert(entity)
    }
    }
    
    suspend fun processFolder(folderPath: String) = withContext(Dispatchers.IO) {
        val extensions = setOf("mp3", "flac", "m4a", "opus", "aac", "wav")
        File(folderPath).walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in extensions }
            .forEach { process(it.absolutePath) }
    }
}