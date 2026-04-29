package com.example.ytdown.services

import com.example.ytdown.core.infrastructure.StorageResolver
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.utils.MetadataUtils
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Serviço que escaneia o sistema de arquivos em busca de músicas órfãs.
 */
@Singleton
class FileSystemScannerService @Inject constructor(
    private val storage: StorageResolver,
    private val downloadDao: DownloadDao
) {
    private val audioExtensions = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac")

    suspend fun scanAndRegisterOrphans(): Int = withContext(Dispatchers.IO) {
        val audioDir = storage.privateDownloadsDir(isAudio = true)
        if (!audioDir.exists()) return@withContext 0

        val files = audioDir.listFiles { file -> 
            file.isFile && audioExtensions.contains(file.extension.lowercase())
        } ?: emptyArray()

        val dbPaths = downloadDao.getAllDownloadsSync().flatMap { listOfNotNull(
            it.outputPath.takeIf { path -> path.isNotBlank() },
            it.exportedPath?.takeIf { uri -> uri.isNotBlank() }
        ) }.toSet()
        val orphans = files.filter { !dbPaths.contains(it.absolutePath) }

        var registered = 0
        orphans.forEach { file ->
            val title = MetadataUtils.normalizeMetadataText(file.nameWithoutExtension)
            val artist = MetadataUtils.guessArtistFromTitle(title) ?: "Desconhecido"
            
            val item = DownloadItemEntity(
                id = "orphan_${UUID.randomUUID().toString().take(8)}",
                url = "",
                title = MetadataUtils.toTitleCase(title),
                thumbnailPath = null,
                type = 0, // Audio
                format = file.extension,
                quality = "128",
                outputPath = file.absolutePath,
                status = "completed",
                progress = 1.0,
                createdAt = file.lastModified(),
                artist = artist,
                album = "YTDown"
            )
            downloadDao.upsert(item)
            registered++
        }
        registered
    }
}
