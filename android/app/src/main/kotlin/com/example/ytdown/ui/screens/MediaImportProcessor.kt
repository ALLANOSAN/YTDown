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
     *
     * @param audioPath Caminho do arquivo físico
     * @param originalTitle Título original (opcional), útil quando o arquivo tem nome genérico (ex: temp)
     * @param forceEnrichment Se true, pula a verificação de metadados existentes (usado em downloads)
     */
    suspend fun process(
        audioPath: String,
        originalTitle: String? = null,
        forceEnrichment: Boolean = false
    ) {
        withContext(Dispatchers.IO) {
            val file = File(audioPath)
            if (!file.exists()) return@withContext

            // PASSO 0 — VERIFICAR SE O ARQUIVO JÁ TEM METADADOS COMPLETOS (Apenas para pastas locais)
            if (!forceEnrichment) {
                val existingMeta = pythonMetadataBridge.readExistingMetadata(audioPath)
                val hasTitle = !existingMeta["title"].isNullOrBlank()
                val hasArtist = !existingMeta["artist"].isNullOrBlank()
                val hasAlbum = !existingMeta["album"].isNullOrBlank()
                val hasArtwork = existingMeta["has_artwork"] == "true"

                // Definimos "completo" como tendo os 3 principais campos
                if (hasTitle && hasArtist && hasAlbum) {
                    val artist = existingMeta["artist"]!!
                    val album = existingMeta["album"]!!
                    val cacheKey = artworkCacheManager.getCacheKey(artist, album)
                    var cachedFile = artworkCacheManager.getCachedAlbumArt(cacheKey)

                    // Se tem artwork no arquivo mas não no cache, vamos extrair agora!
                    if (hasArtwork && cachedFile == null) {
                        val tempArtFile = File(context.cacheDir, "temp_art_${System.currentTimeMillis()}.jpg")
                        if (pythonMetadataBridge.extractEmbeddedArtwork(audioPath, tempArtFile.absolutePath)) {
                            val bytes = tempArtFile.readBytes()
                            cachedFile = artworkCacheManager.saveToAlbumCache(cacheKey, bytes)
                            tempArtFile.delete()
                        }
                    }

                    // Se agora temos TUDO (incluindo a capa no cache), podemos pular o MusicBrainz
                    if (cachedFile != null) {
                        android.util.Log.d("ImportProcessor", "⏭️ Arquivo com metadados e capa: ${existingMeta["title"]} — pulando enriquecimento")
                        
                        val duration = metadataExtractor.extract(audioPath).duration
                        val entity = SongEntity(
                            path = audioPath,
                            title = existingMeta["title"]!!,
                            artist = artist,
                            album = album,
                            duration = duration,
                            albumArtwork = cachedFile.absolutePath,
                            artistArtwork = artworkCacheManager.getCachedArtistArt(artworkCacheManager.getArtistCacheKey(artist))?.absolutePath,
                            addedAt = System.currentTimeMillis()
                        )
                        songDao.insert(entity)
                        return@withContext
                    }
                }
            }

            // PASSO 1 — DEFINIR FONTE DE DADOS PARA BUSCA
            val sourceName = originalTitle ?: file.name
            val filenameMeta = pythonMetadataBridge.extractMetadataFromFilename(sourceName)

            // Se for download (forceEnrichment), ignoramos o que está no arquivo e usamos o título passado/extraído.
            // Se for importação local, priorizamos o que já está nas tags do arquivo.
            val finalTitleFromSource = if (!forceEnrichment && hasTitle) existingMeta["title"]!! else (filenameMeta["title"] ?: originalTitle ?: file.nameWithoutExtension).trim()
            val finalArtistFromSource = if (!forceEnrichment && hasArtist) existingMeta["artist"]!! else filenameMeta["artist"]?.trim()
            val finalAlbumFromSource = if (!forceEnrichment && hasAlbum) existingMeta["album"]!! else null

            android.util.Log.d("ImportProcessor", "🔍 Buscando metadados para: $finalArtistFromSource - $finalTitleFromSource")

            // PASSO 2 — MUSICBRAINZ (Metadados Reais / IDs de Capa)
            val mbResult = musicBrainzService.searchRecording(finalTitleFromSource, finalArtistFromSource ?: "")
            
            // Definição final dos campos:
            // No download (force), confiamos no MusicBrainz. 
            // Na importação (local), confiamos no Arquivo e o MB só preenche se o arquivo estiver vazio.
            val finalTitle = if (forceEnrichment) (mbResult?.title ?: finalTitleFromSource) 
                             else (if (hasTitle) existingMeta["title"]!! else (mbResult?.title ?: finalTitleFromSource))
            
            val finalArtist = if (forceEnrichment) (mbResult?.artist ?: finalArtistFromSource ?: "Artista Desconhecido")
                              else (if (hasArtist) existingMeta["artist"]!! else (mbResult?.artist ?: finalArtistFromSource ?: "Artista Desconhecido"))
            
            val finalAlbum = if (forceEnrichment) (mbResult?.album ?: finalAlbumFromSource ?: "Álbum Desconhecido")
                             else (if (hasAlbum) existingMeta["album"]!! else (mbResult?.album ?: finalAlbumFromSource ?: "Arquivo Local"))
            
            val duration = metadataExtractor.extract(audioPath).duration

            // PASSO 3 — COVER ART ARCHIVE (Album Art)
            var albumArtPath: String? = null
            if (mbResult?.releaseGroupId != null || mbResult?.releaseId != null) {
                val albumCacheKey = artworkCacheManager.getCacheKey(finalArtist, finalAlbum)
                val cachedFile = artworkCacheManager.getCachedAlbumArt(albumCacheKey)
                
                if (cachedFile != null) {
                    albumArtPath = cachedFile.absolutePath
                } else {
                    val bytes = if (mbResult?.releaseGroupId != null) {
                        coverArtService.downloadAlbumArt(mbResult.releaseGroupId, mbResult.releaseId)
                    } else {
                        coverArtService.downloadAlbumArt(mbResult?.releaseId!!)
                    }
                    
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