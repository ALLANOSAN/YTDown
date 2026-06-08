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
import com.example.ytdown.core.artwork.PythonMetadataBridge
import com.example.ytdown.core.metadata.model.MusicBrainzRecording
import com.example.ytdown.services.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Singleton
class MediaImportProcessor @Inject constructor(
    @param:ApplicationContext private val context: Context,
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
     * Seguindo o fluxo: MusicBrainz -> Cover Art Archive -> FanArt.tv -> Mutagen (Write)
     *
     * @param audioPath Caminho do arquivo físico
     * @param originalTitle Título original (opcional), útil quando o arquivo tem nome genérico (ex: temp)
     */
    suspend fun process(audioPath: String, originalTitle: String? = null) {
        withContext(Dispatchers.IO) {
            val file = File(audioPath)
            if (!file.exists()) return@withContext

            // PASSO 0 — VERIFICAR SE O ARQUIVO JÁ TEM METADADOS
            val existingMeta = pythonMetadataBridge.readExistingMetadata(audioPath)
            val hasTitle = !existingMeta["title"].isNullOrBlank()
            val hasArtist = !existingMeta["artist"].isNullOrBlank()
            val hasAlbum = !existingMeta["album"].isNullOrBlank()

            if (hasTitle && hasArtist && hasAlbum) {
                // Já tem metadados completos — só registrar no banco, PULAR enriquecimento
                android.util.Log.d("ImportProcessor", "⏭️ Arquivo já tem metadados: ${existingMeta["title"]} — pulando enriquecimento")
                val duration = metadataExtractor.extract(audioPath).duration
                val entity = SongEntity(
                    path = audioPath,
                    title = existingMeta["title"]!!,
                    artist = existingMeta["artist"]!!,
                    album = existingMeta["album"]!!,
                    duration = duration,
                    albumArtwork = null,
                    artistArtwork = null,
                    addedAt = System.currentTimeMillis()
                )
                songDao.insert(entity)
                return@withContext
            }

            // PASSO 1 — EXTRAIR METADADOS DO NOME DO ARQUIVO OU TÍTULO ORIGINAL
            val sourceName = originalTitle ?: file.name
            val filenameMeta = pythonMetadataBridge.extractMetadataFromFilename(sourceName)
            
            // Usar o título original ou o extraído do nome
            val searchTitle = (filenameMeta["title"] ?: originalTitle ?: file.nameWithoutExtension).trim()
            val searchArtist = filenameMeta["artist"]?.trim()

            android.util.Log.d("ImportProcessor", "🔍 Buscando metadados para: $searchArtist - $searchTitle")

            // PASSO 2 — MUSICBRAINZ (Metadados Reais)
            val mbResult = musicBrainzService.searchRecording(searchTitle, searchArtist ?: "")
            
            val finalTitle = mbResult?.title ?: searchTitle
            val finalArtist = mbResult?.artist ?: searchArtist ?: "Artista Desconhecido"
            val finalAlbum = mbResult?.album ?: "Arquivo Local"
            val duration = metadataExtractor.extract(audioPath).duration

            // PASSO 3 — COVER ART ARCHIVE (Album Art)
            var albumArtPath: String? = null
            if (mbResult?.releaseId != null) {
                val albumCacheKey = artworkCacheManager.getCacheKey(finalArtist, finalAlbum)
                val cachedFile = artworkCacheManager.getCachedAlbumArt(albumCacheKey)
                
                if (cachedFile != null) {
                    albumArtPath = cachedFile.absolutePath
                } else {
                    val bytes = coverArtService.downloadAlbumArt(mbResult.releaseId)
                    if (bytes != null) {
                        val savedFile = artworkCacheManager.saveToAlbumCache(albumCacheKey, bytes)
                        albumArtPath = savedFile?.absolutePath
                    }
                }
            }

            // PASSO 4 — FANART.TV (Artist Art - Apenas Cache)
            var artistArtPath: String? = null
            if (mbResult?.artistId != null) {
                val artistCacheKey = artworkCacheManager.getArtistCacheKey(finalArtist)
                val cachedArtistFile = artworkCacheManager.getCachedArtistArt(artistCacheKey)
                
                if (cachedArtistFile != null) {
                    artistArtPath = cachedArtistFile.absolutePath
                } else {
                    val bytes = fanArtTvService.downloadArtistImage(mbResult.artistId)
                    if (bytes != null) {
                        val savedArtistFile = artworkCacheManager.saveToArtistCache(artistCacheKey, bytes)
                        artistArtPath = savedArtistFile?.absolutePath
                    }
                }
            }

            // PASSO 5 — MUTAGEN (Gravar no arquivo)
            try {
                android.util.Log.d("ImportProcessor", "✍️ Gravando metadados no arquivo via Mutagen: $audioPath")
                pythonMetadataBridge.writeFullMetadata(
                    path = audioPath,
                    title = finalTitle,
                    artist = finalArtist,
                    album = finalAlbum,
                    year = mbResult?.year,
                    albumArt = albumArtPath,
                    trackNumber = mbResult?.trackNumber,
                    discNumber = mbResult?.discNumber
                )
            } catch (e: Exception) {
                android.util.Log.e("ImportProcessor", "❌ Erro ao gravar metadados: ${e.message}")
            }

            // PASSO 6 — DATABASE (SongEntity)
            val entity = SongEntity(
                path = audioPath,
                title = finalTitle,
                artist = finalArtist,
                album = finalAlbum,
                duration = duration,
                albumArtwork = albumArtPath,
                artistArtwork = artistArtPath,
                addedAt = System.currentTimeMillis()
            )
            songDao.insert(entity)
            android.util.Log.d("ImportProcessor", "✅ Processamento concluído para: $finalTitle")
        }
    }
    
    suspend fun processFolder(folderPath: String) = withContext(Dispatchers.IO) {
        val extensions = setOf("mp3", "flac", "m4a", "opus", "aac", "wav")
        File(folderPath).walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in extensions }
            .forEach { process(it.absolutePath) }
    }
}