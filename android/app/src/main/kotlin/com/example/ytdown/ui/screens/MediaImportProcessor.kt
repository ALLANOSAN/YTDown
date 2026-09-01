package com.example.ytdown.core.media

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

import com.example.ytdown.core.business.LibrarySync
import com.example.ytdown.core.domain.SongEntity
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
import com.example.ytdown.core.infrastructure.persistence.SongDao
import com.example.ytdown.core.metadata.MetadataExtractor
import com.example.ytdown.core.artwork.AcaoDeCapa
import com.example.ytdown.core.artwork.ArtworkPolicy
import com.example.ytdown.core.artwork.FanArtTvService
import com.example.ytdown.core.artwork.ArtworkCacheManager
import com.example.ytdown.core.artwork.PythonMetadataBridge
import com.example.ytdown.core.metadata.model.MusicBrainzRecording
import com.example.ytdown.services.*
import com.example.ytdown.services.LastfmService
import com.example.ytdown.utils.LocalLogger
import com.example.ytdown.utils.MetadataUtils
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
    private val songDao: SongDao,
    private val downloadDao: DownloadDao,
    private val lastfmService: LastfmService
) {
    /**
     * Processa qualquer arquivo de audio adicionado ao app (Download ou Local).
     *
     * Fluxo:
     *   0. Se nao for download forcado, verifica se o arquivo ja tem tags completas
     *   1. Extrai metadados basicos da fonte disponivel (parametros > tags do arquivo > filename)
     *   2. MusicBrainz — fonte de verdade para titulo, artista, album, ano, faixa
     *   3. Cover Art Archive — capa do album via MusicBrainz IDs
     *   4. FanArt.tv — foto do artista via MusicBrainz ID
     *   5. Mutagen — grava as tags no arquivo
     *   6. Database — salva SongEntity
     *
     * @param audioPath Caminho do arquivo fisico
     * @param originalTitle Titulo conhecido (do BottomSheet, do filename, etc.)
     * @param knownArtist Artista conhecido (do BottomSheet, etc.) — evita depender do filename parser
     * @param knownAlbum Album conhecido (do BottomSheet, etc.)
     * @param forceEnrichment Se true, ignora tags existentes no arquivo e busca MusicBrainz
     * @param downloadId Se informado, o item da biblioteca com esse id recebe os
     *   metadados enriquecidos ao final (o SongEntity é chaveado por caminho, e
     *   o caminho pode não bater com o outputPath do download — ex: arquivo temp)
     */
    suspend fun process(
        audioPath: String,
        originalTitle: String? = null,
        knownArtist: String? = null,
        knownAlbum: String? = null,
        forceEnrichment: Boolean = false,
        downloadId: String? = null
    ) {
        withContext(Dispatchers.IO) {
            // SAF (content://) não é regravável por caminho: copia para temp,
            // enriquece o temp e devolve. A capacidade existia presa dentro do
            // FileSystemScannerService, então o botão "Reparar Tags" desistia de
            // toda biblioteca adicionada pelo seletor de pastas do Android.
            if (audioPath.startsWith("content://")) {
                processarViaSaf(audioPath, originalTitle, knownArtist, knownAlbum, forceEnrichment, downloadId)
                return@withContext
            }

            val file = File(audioPath)
            if (!file.exists()) return@withContext

            // -- PASSO 0: VERIFICAR SE O ARQUIVO JA TEM METADADOS COMPLETOS (Apenas para pastas locais) --
            var existingMeta: Map<String, String?> = emptyMap()
            var hasTitle = false
            var hasArtist = false
            var hasAlbum = false
            var hasArtwork = false

            if (!forceEnrichment) {
                existingMeta = pythonMetadataBridge.readExistingMetadata(audioPath)
                // Tag sentinela ("Unknown", "Desconhecido", "YTDown") não conta
                // como metadado: senão arquivo marcado por build antigo nunca
                // se recupera — o PASSO 0 pula o enriquecimento pra sempre.
                hasTitle = !MetadataUtils.isUnknownMetadata(existingMeta["title"])
                hasArtist = !MetadataUtils.isUnknownMetadata(existingMeta["artist"])
                hasAlbum = !MetadataUtils.isUnknownMetadata(existingMeta["album"])
                hasArtwork = existingMeta["has_artwork"] == "true"

                // Mesmo criterio do botao Reparar Tags: campo preenchido nao
                // basta. Titulo que ainda e nome de arquivo ("02 Nome(m4a 128k)")
                // nao e sentinela e passava por aqui como "ja tem metadados".
                val jaLimpo = !MetadataUtils.needsMetadataRepair(
                    existingMeta["title"], existingMeta["artist"], existingMeta["album"],
                    hasArtwork = true // a capa e checada logo abaixo, via cache
                )
                if (jaLimpo) {
                    val artist = existingMeta["artist"]!!
                    val album = existingMeta["album"]!!
                    val cacheKey = artworkCacheManager.getCacheKey(artist, album)
                    var cachedFile = artworkCacheManager.getCachedAlbumArt(cacheKey)

                    // Extrai artwork embutida pro cache se necessario
                    if (hasArtwork && cachedFile == null) {
                        val tempArtFile = File(context.cacheDir, "temp_art_${System.currentTimeMillis()}.jpg")
                        if (pythonMetadataBridge.extractEmbeddedArtwork(audioPath, tempArtFile.absolutePath)) {
                            val bytes = tempArtFile.readBytes()
                            cachedFile = artworkCacheManager.saveToAlbumCache(cacheKey, bytes)
                            tempArtFile.delete()
                        }
                    }

                    val acaoDeCapa = ArtworkPolicy.decidir(
                        tagsLimpas = true,
                        capaNoArquivo = hasArtwork,
                        capaNoCache = cachedFile != null,
                    )

                    // Capa que existe so no cache precisa entrar no arquivo: sem
                    // isso o SongEntity apontava para o cache (e o player do app
                    // mostrava a capa) enquanto o arquivo seguia sem APIC, e em
                    // qualquer outro player a faixa aparecia sem capa nenhuma.
                    if (acaoDeCapa == AcaoDeCapa.EMBUTIR_DO_CACHE && cachedFile != null) {
                        val embutiu = pythonMetadataBridge.embedAlbumArtwork(
                            audioPath, cachedFile.absolutePath
                        )
                        android.util.Log.d("ImportProcessor",
                            "Capa do cache embutida no arquivo: $embutiu — $audioPath")
                        if (!embutiu) {
                            LocalLogger.warning(
                                "Capa ficou so no cache, Mutagen recusou o arquivo: $audioPath",
                                tag = "ImportProcessor"
                            )
                        }
                    }

                    if (cachedFile != null && acaoDeCapa != AcaoDeCapa.ENRIQUECER) {
                        android.util.Log.d("ImportProcessor", "Arquivo com metadados e capa: ${existingMeta["title"]} — pulando enriquecimento")

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
                        persistSong(entity, downloadId)
                        return@withContext
                    }
                }
            }

            // -- PASSO 1: EXTRAIR METADADOS BASICOS DA FONTE DISPONIVEL --
            // Prioridade: parametros explicitos > tags do arquivo > filename parser
            val sourceName = originalTitle ?: file.name
            val filenameMeta = pythonMetadataBridge.extractMetadataFromFilename(sourceName)

            // title: originalTitle > existingMeta > filenameMeta > nome do arquivo
            // Se knownArtist esta preenchido e originalTitle comeca com "Artista - ", remove o prefixo
            // cleanFilenameTitle em TODAS as origens, inclusive na tag existente:
            // ela pode carregar lixo de nome de arquivo ("02 Nome(m4a 128k)") e
            // usar isso como semente da busca no MusicBrainz zera o resultado.
            // A limpeza e idempotente — titulo ja limpo passa intacto.
            var resolvedTitle = MetadataUtils.cleanFilenameTitle(
                originalTitle
                    ?: if (!forceEnrichment && hasTitle) existingMeta["title"]!!
                    else filenameMeta["title"] ?: file.nameWithoutExtension
            )

            // sanitizeArtist: "Unknown"/"Desconhecido" são sentinelas, não artista.
            // Se vazarem, a query do MusicBrainz zera e o guard de fallback
            // rejeita o match correto — sem álbum, ano, faixa nem capa.
            val resolvedArtist = MetadataUtils.sanitizeArtist(
                knownArtist
                    ?: if (!forceEnrichment && hasArtist) existingMeta["artist"]!!
                    else filenameMeta["artist"]
            )

            // Detecta e remove prefixo "Artista - " do titulo quando knownArtist esta presente
            if (resolvedArtist.isNotBlank()) {
                val stripped = MetadataUtils.stripArtistPrefix(resolvedTitle, resolvedArtist)
                if (stripped != resolvedTitle) {
                    android.util.Log.d("ImportProcessor",
                        "Titulo continha prefixo do artista: \"$resolvedTitle\" -> \"$stripped\"")
                    resolvedTitle = stripped
                }
            }

            // album: knownAlbum > existingMeta > vazio (vem do MusicBrainz)
            val resolvedAlbum = knownAlbum
                ?: if (!forceEnrichment && hasAlbum) existingMeta["album"]!!
                else ""

            android.util.Log.d("ImportProcessor",
                "Buscando MusicBrainz para: ${resolvedArtist.ifBlank { "?" }} - $resolvedTitle")

            // -- PASSO 2: MUSICBRAINZ (fonte de verdade para metadados) --
            var mbResult: MusicBrainzRecording? = null
            if (resolvedTitle.isNotBlank()) {
                // Tenta buscar com artista primeiro (mais preciso)
                mbResult = musicBrainzService.searchRecording(resolvedTitle, resolvedArtist)
                android.util.Log.d("ImportProcessor",
                    "MusicBrainz busca com artista: ${mbResult != null} -> título=\"${mbResult?.title}\" artista=\"${mbResult?.artist}\"")

                // Fallback: se nao achou com artista, tenta so com o titulo
                if (mbResult == null && resolvedArtist.isNotBlank()) {
                    android.util.Log.d("ImportProcessor",
                        "MusicBrainz sem resultados c/ artista. Tentando só com título: \"$resolvedTitle\"")
                    val fallback = musicBrainzService.searchRecording(resolvedTitle, "")
                    // Só aceita o fallback se o artista do resultado bater com o conhecido
                    // Comparacao normalizada: o MusicBrainz mistura apostrofo U+2019
                    // com ASCII e alterna caixa no catalogo da mesma banda.
                    if (fallback != null && MetadataUtils.normalizeForMatch(resolvedArtist) ==
                        MetadataUtils.normalizeForMatch(fallback.artist)
                    ) {
                        mbResult = fallback
                        android.util.Log.d("ImportProcessor",
                            "MusicBrainz fallback aceito: \"${fallback.title}\" artista=\"${fallback.artist}\"")
                    } else if (fallback != null) {
                        android.util.Log.d("ImportProcessor",
                            "MusicBrainz fallback REJEITADO: artista diverge (esperado=\"$resolvedArtist\", obtido=\"${fallback.artist}\")")
                    }
                }
            }

            // Se MusicBrainz achou, usa os dados dele como fonte principal
            val finalTitle = mbResult?.title
                ?: resolvedTitle
                .trim()

            val finalArtist = mbResult?.artist
                ?: resolvedArtist
                .trim()
                .ifEmpty { "" }

            val finalAlbum = mbResult?.album
                ?: resolvedAlbum
                .trim()
                .ifEmpty { "" }

            val duration = metadataExtractor.extract(audioPath).duration

            // -- PASSO 3: COVER ART ARCHIVE (Album Art via MusicBrainz IDs) --
            var albumArtPath: String? = null
            // So tenta se tiver pelo menos um ID do MusicBrainz E artista+album nao vazios
            if (mbResult != null && (mbResult.releaseGroupId != null || mbResult.releaseId != null)) {
                val albumCacheKey = artworkCacheManager.getCacheKey(finalArtist, finalAlbum)
                val cachedFile = artworkCacheManager.getCachedAlbumArt(albumCacheKey)

                if (cachedFile != null) {
                    albumArtPath = cachedFile.absolutePath
                } else {
                    val coverService = coverArtService
                    val bytes = if (mbResult.releaseGroupId != null) {
                        coverService.downloadAlbumArt(mbResult.releaseGroupId, mbResult.releaseId)
                    } else if (mbResult.releaseId != null) {
                        // Fallback: se so tem releaseId, usa o endpoint de release direto
                        coverService.downloadAlbumArt(mbResult.releaseId!!)
                    } else null

                    if (bytes != null) {
                        val savedFile = artworkCacheManager.saveToAlbumCache(albumCacheKey, bytes)
                        albumArtPath = savedFile?.absolutePath
                    }
                }
            }

            // -- PASSO 3.5: LAST.FM / ITUNES / DEEZER (Fallback de capa quando CAA nao tem) --
            if (albumArtPath == null && finalArtist.isNotBlank()) {
                val albumCacheKey = artworkCacheManager.getCacheKey(finalArtist, finalAlbum)
                val cachedFile = artworkCacheManager.getCachedAlbumArt(albumCacheKey)

                if (cachedFile != null) {
                    albumArtPath = cachedFile.absolutePath
                    android.util.Log.d("ImportProcessor",
                        "Capa encontrada no cache (Last.fm fallback): $albumCacheKey")
                } else {
                    // Tenta primeiro por album, depois por musica (fallback)
                    var coverUrl: String? = null
                    if (finalAlbum.isNotBlank()) {
                        coverUrl = lastfmService.getAlbumCover(finalArtist, finalAlbum)
                    }
                    if (coverUrl == null) {
                        coverUrl = lastfmService.getTrackCover(finalArtist, finalTitle)
                    }

                    if (coverUrl != null) {
                        android.util.Log.d("ImportProcessor",
                            "Last.fm/fallback retornou URL: $coverUrl")
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
                                val savedFile = artworkCacheManager.saveToAlbumCache(albumCacheKey, bytes)
                                albumArtPath = savedFile?.absolutePath
                                android.util.Log.d("ImportProcessor",
                                    "Capa salva do Last.fm/fallback: ${bytes.size} bytes")
                            }
                        } catch (e: Exception) {
                            LocalLogger.error(
                                "Erro ao baixar capa do fallback: ${e.message}", e, "ImportProcessor")
                        }
                    } else {
                        android.util.Log.d("ImportProcessor",
                            "Last.fm/fallback: nenhuma URL encontrada para $finalArtist - $finalTitle")
                    }
                }
            }

            // -- PASSO 4: FANART.TV (Artist Art via MusicBrainz ID) --
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

            // -- PASSO 5: MUTAGEN (Gravar tags no arquivo) --
            if (finalTitle.isNotBlank() || finalArtist.isNotBlank()) {
                try {
                    android.util.Log.d("ImportProcessor",
                        "Gravando metadados via Mutagen: $finalArtist - $finalTitle")
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
                    LocalLogger.error(
                        "Erro ao gravar metadados: ${e.message}", e, "ImportProcessor")
                }
            }

            // -- PASSO 6: DATABASE (SongEntity) --
            val entity = SongEntity(
                path = audioPath,
                title = finalTitle.ifBlank { MetadataUtils.cleanFilenameTitle(file.nameWithoutExtension) },
                artist = finalArtist.ifBlank { "" },
                album = finalAlbum.ifBlank { "" },
                duration = duration,
                albumArtwork = albumArtPath,
                artistArtwork = artistArtPath,
                addedAt = System.currentTimeMillis()
            )
            persistSong(entity, downloadId)
            android.util.Log.d("ImportProcessor",
                "Processamento concluido: ${finalArtist.ifBlank { "?" }} - $finalTitle | capa=${albumArtPath != null}")
        }
    }

    /**
     * Enriquece um arquivo acessível só por `content://`.
     *
     * Copia para o cache, roda o pipeline normal sobre a cópia e devolve o
     * resultado para a URI. Se a devolução falhar (permissão revogada, provedor
     * somente-leitura), os metadados ainda ficam no banco — melhor que nada.
     */
    private suspend fun processarViaSaf(
        safUri: String,
        originalTitle: String?,
        knownArtist: String?,
        knownAlbum: String?,
        forceEnrichment: Boolean,
        downloadId: String?
    ) = withContext(Dispatchers.IO) {
        val uri = android.net.Uri.parse(safUri)
        val entrada = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            LocalLogger.error("SAF ilegível: $safUri", e, "ImportProcessor")
            null
        } ?: return@withContext

        val ext = safUri.substringAfterLast(".").takeIf { it.length in 1..4 } ?: "mp3"
        val temp = File.createTempFile("enrich_", ".$ext", context.cacheDir)
        try {
            entrada.use { input -> temp.outputStream().use { input.copyTo(it) } }

            process(temp.absolutePath, originalTitle, knownArtist, knownAlbum, forceEnrichment, downloadId)

            try {
                context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    temp.inputStream().use { it.copyTo(output) }
                } ?: LocalLogger.warning("SAF sem stream de escrita: $safUri", tag = "ImportProcessor")
            } catch (e: Exception) {
                LocalLogger.error(
                    "Metadados ficaram só no banco, SAF não aceitou escrita: $safUri",
                    e, "ImportProcessor"
                )
            }
        } finally {
            temp.delete()
        }
    }

    /**
     * Grava o SongEntity e, quando o chamador informou um downloadId, propaga os
     * metadados para o item da biblioteca — senão o arquivo fica enriquecido mas
     * a lista continua mostrando "Desconhecido".
     */
    private suspend fun persistSong(entity: SongEntity, downloadId: String?) {
        songDao.insert(entity)
        if (downloadId == null) return
        val download = downloadDao.getById(downloadId) ?: return
        downloadDao.upsert(LibrarySync.merge(download, entity))
        android.util.Log.d("ImportProcessor",
            "Sync biblioteca: ${entity.artist} - ${entity.title} | capa=${entity.albumArtwork != null}")
    }

    suspend fun processFolder(folderPath: String) = withContext(Dispatchers.IO) {
        val extensions = setOf("mp3", "flac", "m4a", "opus", "aac", "wav")
        File(folderPath).walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in extensions }
            .forEach { process(it.absolutePath) }
    }
}
