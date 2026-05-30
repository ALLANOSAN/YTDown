package com.example.ytdown.services

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.ytdown.core.infrastructure.StorageResolver
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
import com.example.ytdown.core.infrastructure.persistence.SongDao
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.media.MediaImportProcessor
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
    private val songDao: SongDao,
    private val folderService: MusicFolderService,
    private val artworkManager: ArtworkManager,
    private val importProcessor: MediaImportProcessor,
    @param:ApplicationContext private val context: Context
) {
    private val audioExtensions = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac")
    private val prefs = context.getSharedPreferences("scanner_prefs", Context.MODE_PRIVATE)

    suspend fun scanAndRegisterOrphans(onProgress: (String) -> Unit = {}): Int = withContext(Dispatchers.IO) {
        val audioDir = storage.privateDownloadsDir(isAudio = true)
        val selectedFolders = folderService.folders.value
        
        var registered = 0
        val dbPaths = downloadDao.getAllDownloadsSync().flatMap { listOfNotNull(
            it.outputPath.takeIf { path -> path.isNotBlank() },
            it.exportedPath?.takeIf { uri -> uri.isNotBlank() }
        ) }.toSet()

        // 1. Escanear diretório privado (File API)
        if (audioDir.exists()) {
            registered += scanPhysicalDir(audioDir, dbPaths, onProgress)
        }

        // 2. Escanear diretórios externos selecionados
        selectedFolders.forEach { path ->
            if (path.startsWith("content://")) {
                // Escanear via SAF (Storage Access Framework)
                registered += scanDocumentTree(path, dbPaths, onProgress)
            } else {
                // Escanear via File API
                val dir = File(path)
                if (dir.exists() && dir.isDirectory) {
                    registered += scanPhysicalDir(dir, dbPaths, onProgress)
                }
            }
        }

        registered
    }

    private suspend fun scanPhysicalDir(dir: File, dbPaths: Set<String>, onProgress: (String) -> Unit): Int {
        var registered = 0
        onProgress("Escaneando: ${dir.name}")
        val lastScanned = prefs.getLong("last_scan_${dir.absolutePath}", 0L)
        if (dir.lastModified() <= lastScanned) return 0

        val audioFiles = findAudioFiles(dir)
        val orphans = audioFiles.filter { !dbPaths.contains(it.absolutePath) }

        orphans.forEach { file ->
            onProgress("Adicionando: ${file.name}")
            registerOrphan(file.absolutePath, file.nameWithoutExtension, file.extension, file.lastModified())
            registered++
        }
        prefs.edit().putLong("last_scan_${dir.absolutePath}", System.currentTimeMillis()).apply()
        return registered
    }

    private suspend fun scanDocumentTree(uriString: String, dbPaths: Set<String>, onProgress: (String) -> Unit): Int {
        var registered = 0
        try {
            val treeUri = Uri.parse(uriString)
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
            
            onProgress("Escaneando: ${rootDoc.name ?: "Pasta externa"}")
            
            val audioDocs = findAudioDocuments(rootDoc)
            val orphans = audioDocs.filter { !dbPaths.contains(it.uri.toString()) }

            orphans.forEach { doc ->
                onProgress("Adicionando: ${doc.name}")
                val name = doc.name ?: "Sem nome"
                val extension = name.substringAfterLast(".", "mp3")
                val title = name.substringBeforeLast(".")
                
                registerOrphan(doc.uri.toString(), title, extension, doc.lastModified())
                registered++
            }
        } catch (e: Exception) {
            android.util.Log.e("FileSystemScanner", "Erro ao escanear DocumentTree: $uriString", e)
        }
        return registered
    }

    private fun findAudioDocuments(root: DocumentFile): List<DocumentFile> {
        val result = mutableListOf<DocumentFile>()
        val files = root.listFiles()
        
        // Ignora se houver .nomedia
        if (files.any { it.name == ".nomedia" }) return emptyList()

        files.forEach { file ->
            if (file.isDirectory) {
                result.addAll(findAudioDocuments(file))
            } else if (file.isFile) {
                val name = file.name?.lowercase() ?: ""
                if (audioExtensions.any { name.endsWith(".$it") }) {
                    result.add(file)
                }
            }
        }
        return result
    }

    private suspend fun registerOrphan(path: String, nameWithoutExtension: String, extension: String, lastModified: Long) {
        val title = MetadataUtils.normalizeMetadataText(nameWithoutExtension)

        // 1. Verificação de duplicata: Busca por arquivo com mesmo título e tamanho
        val fileSize = if (path.startsWith("content://")) {
            -1L
        } else {
            File(path).length()
        }

        val existing = downloadDao.getAllDownloadsSync().find {
            it.title.equals(title, ignoreCase = true) &&
            (fileSize == -1L || it.outputPath.let { p -> p.isNotBlank() && File(p).exists() && File(p).length() == fileSize })
        }

        if (existing != null) return // Arquivo já existe, não duplicar

        // 2. Registro rápido no banco (mostra na biblioteca imediatamente)
        val item = DownloadItemEntity(
            id = "orphan_${UUID.randomUUID().toString().take(8)}",
            url = "",
            title = MetadataUtils.toTitleCase(title),
            type = 0,
            format = extension,
            quality = "128",
            outputPath = path,
            status = "completed",
            progress = 1.0,
            createdAt = lastModified,
            artist = MetadataUtils.guessArtistFromTitle(title) ?: "Desconhecido",
            album = "YTDown"
        )
        downloadDao.upsert(item)

        // 3. Pipeline completo de metadados (MusicBrainz → Cover Art → FanArt → Mutagen)
        try {
            if (path.startsWith("content://")) {
                // SAF: copiar → processar com Mutagen → copiar de volta
                processSafFile(path, title)
            } else {
                // Arquivo físico: processar direto
                if (File(path).exists()) {
                    android.util.Log.d("FileSystemScanner", "🔄 Enrichment completo para: $title")
                    importProcessor.process(path)
                }
            }

            // 4. Atualizar DownloadItemEntity com metadados enriquecidos do SongEntity
            syncEnrichedMetadataToDownload(item.id, path)
        } catch (e: Exception) {
            android.util.Log.w("FileSystemScanner", "⚠️ Enrichment falhou para $title: ${e.message}")
        }
    }

    /**
     * Sincroniza metadados do SongEntity (enriquecido pelo MediaImportProcessor)
     * para o DownloadItemEntity (usado pela biblioteca).
     */
    private suspend fun syncEnrichedMetadataToDownload(downloadId: String, audioPath: String) {
        withContext(Dispatchers.IO) {
            val song = songDao.getByPath(audioPath) ?: return@withContext
            val download = downloadDao.getById(downloadId) ?: return@withContext

            val updated = download.copy(
                title = song.title,
                artist = song.artist,
                album = song.album,
                albumArtPath = song.albumArtwork ?: download.albumArtPath,
                artistArtPath = song.artistArtwork ?: download.artistArtPath
            )
            downloadDao.upsert(updated)
            android.util.Log.d("FileSystemScanner", "🔗 Sync: ${song.artist} - ${song.album} | art=${song.albumArtwork != null} | artist=${song.artistArtwork != null}")
        }
    }

    /**
     * Processa arquivo SAF (content://): copia para temp → Mutagen grava metadados → copia de volta.
     */
    private suspend fun processSafFile(safUri: String, title: String) = withContext(Dispatchers.IO) {
        val uri = android.net.Uri.parse(safUri)
        val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext

        val ext = safUri.substringAfterLast(".").takeIf { it.length <= 4 } ?: "mp3"
        val tempFile = File.createTempFile("scanner_enrich_", ".$ext", context.cacheDir)

        // 1. Copiar SAF → temp
        inputStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        android.util.Log.d("FileSystemScanner", "📎 SAF copiado para temp: ${tempFile.name}")

        // 2. Processar temp com pipeline completo (MusicBrainz → Cover Art → FanArt → Mutagen)
        importProcessor.process(tempFile.absolutePath)
        android.util.Log.d("FileSystemScanner", "✅ Enrichment SAF concluído para: $title")

        // 3. Copiar temp enriquecido de volta para SAF
        try {
            val outputStream = context.contentResolver.openOutputStream(uri, "w")
            if (outputStream != null) {
                outputStream.use { output ->
                    tempFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                android.util.Log.d("FileSystemScanner", "✅ Metadados gravados no arquivo SAF: $title")
            }
        } catch (e: Exception) {
            android.util.Log.w("FileSystemScanner", "⚠️ Não foi possível gravar no SAF (metadados ficam só no banco): ${e.message}")
        }

        // 4. Limpar temp
        tempFile.delete()
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
     * não existe mais no disco ou que pertence a uma pasta não monitorada.
     */
    suspend fun removeStaleEntries(): Int = withContext(Dispatchers.IO) {
        val allItems = downloadDao.getAllDownloadsSync()
        val monitoredFolders = folderService.folders.value
        var removed = 0

        allItems.forEach { item ->
            // Não removemos downloads realizados internamente pelo app (tipo 0 ou sem path de pasta monitorada?)
            // Se o item foi um download (url não vazia), mantemos. Se for "orphan", removemos se a pasta não for mais monitorada.
            if (item.status != "completed") return@forEach
            
            // Itens de download próprio (não órfãos) geralmente mantemos
            if (!item.url.isNullOrBlank()) return@forEach

            val outputPath = item.outputPath.takeIf { it.isNotBlank() } ?: return@forEach
            
            // Verifica se o arquivo ainda existe
            val fileExists = if (outputPath.startsWith("content://")) {
                DocumentFile.fromSingleUri(context, Uri.parse(outputPath))?.exists() == true
            } else {
                File(outputPath).exists()
            }

            // Verifica se o arquivo pertence a alguma pasta monitorada
            val isMonitored = monitoredFolders.any { folder ->
                if (folder.startsWith("content://")) {
                    outputPath.startsWith(folder)
                } else {
                    outputPath.startsWith(folder)
                }
            }

            if (!fileExists || !isMonitored) {
                downloadDao.delete(item)
                removed++
                android.util.Log.d("FileSystemScanner", "Entrada removida: ${item.title} (Status: ${if(!fileExists) "Arquivo sumiu" else "Pasta não monitorada"})")
            }
        }
        removed
    }

    /**
     * Tenta restaurar o exportedPath de downloads antigos procurando o arquivo nas pastas monitoradas.
     * Isso "cura" músicas que pararam de tocar após migrações ou perda de permissão.
     */
    suspend fun repairDownloadedItems(): Int = withContext(Dispatchers.IO) {
        val completedDownloads = downloadDao.getAllDownloadsSync().filter { 
            it.status == "completed" && (it.exportedPath.isNullOrBlank() || !isPathAccessible(it.exportedPath))
        }
        
        if (completedDownloads.isEmpty()) return@withContext 0
        
        val monitoredFolders = folderService.folders.value
        var repaired = 0

        completedDownloads.forEach { item ->
            // Procurar por este título em todas as pastas monitoradas
            for (folderUri in monitoredFolders) {
                if (!folderUri.startsWith("content://")) continue
                
                val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(folderUri)) ?: continue
                val foundFile = findFileInTree(rootDoc, item.title, item.format)
                
                if (foundFile != null) {
                    downloadDao.upsert(item.copy(exportedPath = foundFile.uri.toString()))
                    repaired++
                    android.util.Log.d("FileSystemScanner", "✅ Item reparado: ${item.title} -> ${foundFile.uri}")
                    break
                }
            }
        }
        repaired
    }

    private fun isPathAccessible(path: String): Boolean {
        return try {
            if (path.startsWith("content://")) {
                DocumentFile.fromSingleUri(context, Uri.parse(path))?.exists() == true
            } else {
                File(path).exists()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun findFileInTree(root: DocumentFile, title: String, extension: String): DocumentFile? {
        val files = root.listFiles()
        // Tenta match exato primeiro
        val match = files.find { 
            it.isFile && it.name?.startsWith(title, ignoreCase = true) == true && it.name?.endsWith(extension, ignoreCase = true) == true
        }
        if (match != null) return match

        // Busca recursiva
        for (dir in files.filter { it.isDirectory }) {
            val found = findFileInTree(dir, title, extension)
            if (found != null) return found
        }
        return null
    }

    /**
     * Executa scan completo: registra órfãos, remove entradas obsoletas e repara links quebrados.
     */
    suspend fun fullSync(onProgress: (String) -> Unit = {}): Triple<Int, Int, Int> {
        val registered = scanAndRegisterOrphans(onProgress)
        val repaired = repairDownloadedItems()
        val removed = removeStaleEntries()
        return Triple(registered, repaired, removed)
    }

}
