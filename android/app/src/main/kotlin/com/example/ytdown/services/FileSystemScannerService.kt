package com.example.ytdown.services

import android.content.Context
import com.example.ytdown.core.infrastructure.StorageResolver
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.utils.MetadataUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Serviço que escaneia o sistema de arquivos em busca de músicas órfãs
 * e remove do banco itens cujo arquivo foi deletado fora do app.
 */
@Singleton
class FileSystemScannerService @Inject constructor(
    private val storage: StorageResolver,
    private val downloadDao: DownloadDao,
    private val folderService: MusicFolderService,
    @ApplicationContext private val context: Context // Adicionado para acessar SharedPreferences
) {
    private val audioExtensions = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac")
    private val prefs = context.getSharedPreferences("scanner_prefs", Context.MODE_PRIVATE)

    suspend fun scanAndRegisterOrphans(onProgress: (String) -> Unit = {}): Int = withContext(Dispatchers.IO) {
        val audioDir = storage.privateDownloadsDir(isAudio = true)
        val selectedFolders = folderService.folders.value
        
        val scanDirs = mutableListOf<File>()
        if (audioDir.exists()) scanDirs.add(audioDir)
        selectedFolders.forEach { path ->
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) scanDirs.add(dir)
        }

        if (scanDirs.isEmpty()) return@withContext 0

        var registered = 0
        val dbPaths = downloadDao.getAllDownloadsSync().flatMap { listOfNotNull(
            it.outputPath.takeIf { path -> path.isNotBlank() },
            it.exportedPath?.takeIf { uri -> uri.isNotBlank() }
        ) }.toSet()

        scanDirs.forEach { dir ->
            onProgress("Escaneando: ${dir.name}")
            val lastScanned = prefs.getLong("last_scan_${dir.absolutePath}", 0L)
            if (dir.lastModified() <= lastScanned) return@forEach

            val audioFiles = findAudioFiles(dir)
            val orphans = audioFiles.filter { !dbPaths.contains(it.absolutePath) }

            orphans.forEach { file ->
                onProgress("Adicionando: ${file.name}")
                val title = MetadataUtils.normalizeMetadataText(file.nameWithoutExtension)
                val artist = MetadataUtils.guessArtistFromTitle(title) ?: "Desconhecido"

                val item = DownloadItemEntity(
                    id = "orphan_${UUID.randomUUID().toString().take(8)}",
                    url = "",
                    title = MetadataUtils.toTitleCase(title),
                    thumbnailPath = null,
                    type = 0,
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
            prefs.edit().putLong("last_scan_${dir.absolutePath}", System.currentTimeMillis()).apply()
        }
        registered
    }

    private fun findAudioFiles(dir: File): List<File> {
        val result = mutableListOf<File>()
        val files = dir.listFiles() ?: return emptyList()
        
        // Se a pasta tem .nomedia, ignoramos ela (padrão Android)
        if (files.any { it.name == ".nomedia" }) return emptyList()
        
        // Ignora pastas do WhatsApp por padrão se o caminho contiver WhatsApp
        if (dir.absolutePath.contains("WhatsApp", ignoreCase = true)) return emptyList()

        files.forEach { file ->
            if (file.isDirectory) {
                result.addAll(findAudioFiles(file))
            } else if (file.isFile && audioExtensions.contains(file.extension.lowercase())) {
                result.add(file)
            }
        }
        return result
    }

    /**
     * Remove do banco todos os itens "completed" cujo arquivo privado
     * não existe mais no disco (deletado fora do app).
     * Retorna o número de itens removidos.
     */
    suspend fun removeStaleEntries(): Int = withContext(Dispatchers.IO) {
        val allItems = downloadDao.getAllDownloadsSync()
        var removed = 0

        allItems.forEach { item ->
            if (item.status != "completed") return@forEach

            // Se tem caminho exportado (content:// URI), não verificamos — o MediaStore
            // gerencia esse arquivo e pode não ser acessível via File.
            if (!item.exportedPath.isNullOrBlank()) return@forEach

            val outputPath = item.outputPath.takeIf { it.isNotBlank() } ?: return@forEach
            val file = File(outputPath)

            if (!file.exists()) {
                downloadDao.delete(item)
                removed++
                android.util.Log.d("FileSystemScanner", "Entrada removida (arquivo ausente): ${item.title}")
            }
        }
        removed
    }

    /**
     * Executa scan completo: registra órfãos e remove entradas obsoletas.
     */
    suspend fun fullSync(onProgress: (String) -> Unit = {}): Pair<Int, Int> {
        val registered = scanAndRegisterOrphans(onProgress)
        val removed = removeStaleEntries()
        return Pair(registered, removed)
    }
}
