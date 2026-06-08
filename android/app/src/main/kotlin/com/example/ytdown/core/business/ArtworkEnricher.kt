package com.example.ytdown.core.business

import com.example.ytdown.core.artwork.ArtworkCacheManager
import com.example.ytdown.core.artwork.FanArtTvService
import com.example.ytdown.core.artwork.PythonMetadataBridge
import com.example.ytdown.services.ArtworkManager
import com.example.ytdown.services.CoverArtArchiveService
import com.example.ytdown.services.DatabaseService
import com.example.ytdown.services.LastfmService
import com.example.ytdown.services.MusicBrainzService
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Botão "Capas" — Baixa capa do álbum e foto do artista.
 *
 * - Capa do álbum: grava no arquivo (Mutagen) + salva no cache
 * - Foto do artista: salva apenas no cache (NÃO grava no arquivo)
 */
@Singleton
class ArtworkEnricher @Inject constructor(
    private val databaseService: DatabaseService,
    private val artworkManager: ArtworkManager,
    private val musicBrainzService: MusicBrainzService,
    private val fanArtTvService: FanArtTvService,
    private val artworkCacheManager: ArtworkCacheManager,
    private val pythonMetadataBridge: PythonMetadataBridge,
    private val coverArtArchiveService: CoverArtArchiveService,
    private val lastfmService: LastfmService
) {
    suspend fun getArtistImageFor(artist: String): String? = artworkManager.getArtistImage(artist)
    suspend fun getAlbumCoverFor(artist: String, album: String): String? = artworkManager.getAlbumCover(artist, album)

    suspend fun enrichAll(
        onProgress: (Float, String) -> Unit
    ): Triple<Int, Int, Int> {
        val items = databaseService.getLibraryAudios()
        if (items.isEmpty()) return Triple(0, 0, 0)

        var updated = 0
        var failed = 0
        var skipped = 0
        var processed = 0

        for (item in items) {
            processed++
            onProgress(processed / items.size.toFloat(), "Capas: ${item.title}")

            val targetPath = item.exportedPath?.takeIf { it.isNotBlank() } ?: item.outputPath
            if (targetPath.isBlank() || !File(targetPath).exists()) {
                failed++
                continue
            }

            val artist = item.artist?.trim() ?: ""
            val album = item.album?.trim() ?: ""
            if (artist.isBlank()) {
                skipped++
                continue
            }

            try {
                // 1. Buscar MusicBrainz para pegar releaseId e artistId
                val mbResult = musicBrainzService.searchRecording(item.title.trim(), artist)

                var albumArtPath: String? = null
                var artistArtPath: String? = null

                // 2. Capa do álbum → arquivo + cache
                val releaseGroupId = mbResult?.releaseGroupId
                val releaseId = mbResult?.releaseId
                if (releaseGroupId != null || releaseId != null) {
                    val albumCacheKey = artworkCacheManager.getCacheKey(artist, album.ifBlank { "YTDown" })
                    val cached = artworkCacheManager.getCachedAlbumArt(albumCacheKey)
                    if (cached != null) {
                        albumArtPath = cached.absolutePath
                    } else {
                        val bytes = if (releaseGroupId != null) {
                            coverArtArchiveService.downloadAlbumArt(releaseGroupId, releaseId)
                        } else {
                            coverArtArchiveService.downloadAlbumArt(releaseId!!)
                        }
                        if (bytes != null) {
                            val saved = artworkCacheManager.saveToAlbumCache(albumCacheKey, bytes)
                            albumArtPath = saved?.absolutePath
                        }
                    }
                }

                // 2.5 Last.fm/iTunes/Deezer (fallback quando CAA nao tem)
                if (albumArtPath == null && artist.isNotBlank()) {
                    val albumCacheKey = artworkCacheManager.getCacheKey(artist, album.ifBlank { "YTDown" })
                    val cached = artworkCacheManager.getCachedAlbumArt(albumCacheKey)
                    if (cached != null) {
                        albumArtPath = cached.absolutePath
                    } else {
                        // Tenta primeiro por album, depois por musica (fallback)
                        var coverUrl: String? = null
                        if (album.isNotBlank()) {
                            coverUrl = lastfmService.getAlbumCover(artist, album)
                        }
                        if (coverUrl == null) {
                            coverUrl = lastfmService.getTrackCover(artist, item.title.trim())
                        }

                        if (coverUrl != null) {
                            try {
                                val url = java.net.URL(coverUrl)
                                val conn = url.openConnection() as java.net.HttpURLConnection
                                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) YTDown/1.0")
                                conn.connectTimeout = 10000
                                conn.readTimeout = 10000
                                conn.doInput = true
                                conn.connect()
                                val bytes = conn.inputStream.readBytes()
                                if (bytes.isNotEmpty()) {
                                    val saved = artworkCacheManager.saveToAlbumCache(albumCacheKey, bytes)
                                    albumArtPath = saved?.absolutePath
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ArtworkEnricher", "Erro ao baixar capa do Last.fm: ${e.message}")
                            }
                        }
                    }
                }

                // 3. Foto do artista → APENAS cache (não grava no arquivo)
                val artistId = mbResult?.artistId
                if (artistId != null) {
                    val artistCacheKey = artworkCacheManager.getArtistCacheKey(artist)
                    val cachedArtist = artworkCacheManager.getCachedArtistArt(artistCacheKey)
                    if (cachedArtist != null) {
                        artistArtPath = cachedArtist.absolutePath
                    } else {
                        val bytes = fanArtTvService.downloadArtistImage(artistId)
                        if (bytes != null) {
                            val saved = artworkCacheManager.saveToArtistCache(artistCacheKey, bytes)
                            artistArtPath = saved?.absolutePath
                        }
                    }
                }

                // 4. Gravar capa do álbum no arquivo via Mutagen (NÃO a foto do artista)
                if (albumArtPath != null) {
                    pythonMetadataBridge.writeFullMetadata(
                        path = targetPath,
                        title = item.title.trim(),
                        artist = artist,
                        album = album.ifBlank { "Álbum Desconhecido" },
                        year = mbResult?.year,
                        albumArt = albumArtPath,
                        trackNumber = mbResult?.trackNumber,
                        discNumber = mbResult?.discNumber
                    )
                }

                // 5. Atualizar banco de dados
                val updatedItem = item.copy(
                    albumArtPath = albumArtPath ?: item.albumArtPath,
                    artistArtPath = artistArtPath ?: item.artistArtPath
                )
                databaseService.updateDownload(updatedItem)
                updated++

                android.util.Log.d("ArtworkEnricher", "✅ Capas: ${item.title} (album=${albumArtPath != null}, artist=${artistArtPath != null})")
            } catch (e: Exception) {
                android.util.Log.e("ArtworkEnricher", "❌ Erro: ${item.title}: ${e.message}")
                failed++
            }
        }
        return Triple(updated, failed, skipped)
    }
}

