package com.example.ytdown.services

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.ytdown.core.infrastructure.StorageResolver
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.media.MediaImportProcessor
import com.example.ytdown.core.artwork.PythonMetadataBridge
import com.example.ytdown.utils.MetadataUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import com.example.ytdown.utils.LocalLogger

/**
 * Serviço que escaneia o sistema de arquivos em busca de músicas órfãs
 * e remove do banco itens cujo arquivo foi deletado fora do app.
 */
@Singleton
class FileSystemScannerService @Inject constructor(
    private val storage: StorageResolver,
    private val downloadDao: DownloadDao,
    private val folderService: MusicFolderService,
    private val artworkManager: ArtworkManager,
    private val importProcessor: MediaImportProcessor,
    private val pythonMetadataBridge: PythonMetadataBridge,
    @param:ApplicationContext private val context: Context
) {
    private val audioExtensions = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac")

    suspend fun scanAndRegisterOrphans(onProgress: (String) -> Unit = {}): Int = withContext(Dispatchers.IO) {
        val audioDir = storage.privateDownloadsDir(isAudio = true)
        val selectedFolders = folderService.folders.value
        
        var registered = 0
        // Uma única leitura do banco para o scan inteiro. Antes, registerOrphan
        // chamava getAllDownloadsSync() por arquivo (N queries) e comparava contra
        // um retrato que envelhecia a cada inserção — dois arquivos iguais no mesmo
        // scan escapavam da deduplicação.
        val snapshot = downloadDao.getAllDownloadsSync()
        val dbPaths = snapshot.flatMap { listOfNotNull(
            it.outputPath.takeIf { path -> path.isNotBlank() },
            it.exportedPath?.takeIf { uri -> uri.isNotBlank() }
        ) }.toSet()
        val known = snapshot.mapNotNullTo(mutableSetOf()) { item ->
            val file = File(item.outputPath)
            if (item.outputPath.isNotBlank() && file.exists()) {
                item.title.lowercase() to file.length()
            } else null
        }

        // 1. Escanear diretório privado (File API)
        if (audioDir.exists()) {
            registered += scanPhysicalDir(audioDir, dbPaths, known, onProgress)
        }

        // 2. Escanear diretórios externos selecionados
        selectedFolders.forEach { path ->
            if (path.startsWith("content://")) {
                // Escanear via SAF (Storage Access Framework)
                registered += scanDocumentTree(path, dbPaths, known, onProgress)
            } else {
                // Escanear via File API
                val dir = File(path)
                if (dir.exists() && dir.isDirectory) {
                    registered += scanPhysicalDir(dir, dbPaths, known, onProgress)
                }
            }
        }

        registered
    }

    private suspend fun scanPhysicalDir(
        dir: File,
        dbPaths: Set<String>,
        known: MutableSet<Pair<String, Long>>,
        onProgress: (String) -> Unit
    ): Int {
        var registered = 0
        onProgress("Escaneando: ${dir.name}")
        // Sem guard de mtime: o mtime de um diretório só muda quando os filhos
        // DIRETOS mudam. Numa biblioteca Musica/Artista/Album/*.mp3 a raiz nunca
        // muda, então música nova em subpasta nunca era detectada. A varredura
        // abaixo é barata e o filtro por dbPaths já evita reescrever o banco.
        val audioFiles = findAudioFiles(dir)
        val orphans = audioFiles.filter { !dbPaths.contains(it.absolutePath) }

        orphans.forEach { file ->
            onProgress("Adicionando: ${file.name}")
            registerOrphan(file.absolutePath, file.nameWithoutExtension, file.extension, file.lastModified(), known)
            registered++
        }
        return registered
    }

    private suspend fun scanDocumentTree(
        uriString: String,
        dbPaths: Set<String>,
        known: MutableSet<Pair<String, Long>>,
        onProgress: (String) -> Unit
    ): Int {
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
                
                registerOrphan(doc.uri.toString(), title, extension, doc.lastModified(), known)
                registered++
            }
        } catch (e: Exception) {
            LocalLogger.error("Erro ao escanear DocumentTree: $uriString", e, "FileSystemScanner")
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

    private suspend fun registerOrphan(
        path: String,
        nameWithoutExtension: String,
        extension: String,
        lastModified: Long,
        known: MutableSet<Pair<String, Long>>
    ) {
        val title = MetadataUtils.cleanFilenameTitle(nameWithoutExtension)

        // 0. Tentar ler metadados existentes do arquivo
        val existingMeta = pythonMetadataBridge.readExistingMetadata(path)
        val hasExistingMeta = !existingMeta["title"].isNullOrBlank() &&
                               !existingMeta["artist"].isNullOrBlank() &&
                               !existingMeta["album"].isNullOrBlank()

        // 1. Verificação de duplicata: Busca por arquivo com mesmo título e tamanho
        val fileSize = if (path.startsWith("content://")) {
            -1L
        } else {
            File(path).length()
        }

        // Só deduplica quando dá pra comparar o tamanho. Em SAF (content://) o
        // tamanho é desconhecido e o critério caía para "mesmo título" — duas
        // faixas "Intro" de álbuns diferentes e a segunda sumia da biblioteca.
        // O filtro por dbPaths lá em cima já cobre o mesmo arquivo reescaneado.
        if (fileSize != -1L) {
            val chave = title.lowercase() to fileSize
            if (!known.add(chave)) return // Já registrado (banco ou este mesmo scan)
        }

        // 2. Registro rápido no banco (mostra na biblioteca imediatamente)
        val item = DownloadItemEntity(
            id = "orphan_${UUID.randomUUID().toString().take(8)}",
            url = "",
            title = if (hasExistingMeta) existingMeta["title"]!! else MetadataUtils.toTitleCase(title),
            type = 0,
            format = extension,
            quality = "128",
            outputPath = path,
            status = "completed",
            progress = 1.0,
            createdAt = lastModified,
            artist = if (hasExistingMeta) existingMeta["artist"]!! else (MetadataUtils.guessArtistFromTitle(title) ?: "Desconhecido"),
            album = if (hasExistingMeta) existingMeta["album"]!! else "YTDown"
        )
        downloadDao.upsert(item)

        // 3. Pipeline completo de metadados (MusicBrainz → Cover Art → FanArt → Mutagen)
        try {
            // process() resolve SAF sozinho (copia para temp, enriquece, devolve)
            // e propaga o resultado pro DownloadItemEntity via downloadId.
            if (path.startsWith("content://") || File(path).exists()) {
                LocalLogger.debug("Enrichment completo para: $title", tag = "FileSystemScanner")
                importProcessor.process(path, title, downloadId = item.id)
            }
        } catch (e: Exception) {
            android.util.Log.w("FileSystemScanner", "⚠️ Enrichment falhou para $title: ${e.message}")
        }
    }

    /**
     * Compara se uma URI de arquivo SAF pertence a uma pasta SAF monitorada.
     * Exemplo:
     *   folder: content://com.android.externalstorage.documents/tree/primary%3AMusic
     *   file:   content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2Fsong.mp3
     *   → true (pois a tree ID "primary%3AMusic" é a mesma)
     */
    private fun safTreeMatches(folderUri: String, fileUri: String): Boolean {
        return try {
            val folderTree = Uri.parse(folderUri).path?.substringAfter("/tree/")?.substringBefore("/")
            val fileTree = Uri.parse(fileUri).path?.substringAfter("/tree/")?.substringBefore("/")
            folderTree != null && fileTree != null && folderTree == fileTree
        } catch (e: Exception) {
            false
        }
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
     * Remove do banco itens cujo arquivo físico não existe mais.
     *
     * PROTEÇÕES:
     * - NUNCA deleta itens SAF (content://) — a permissão pode estar temporariamente revogada
     * - NUNCA deleta se não há pastas monitoradas (pode ser timing issue no startup)
     * - Só deleta arquivos físicos confirmados como inexistentes no disco
     * - Downloads do app (url não vazia) nunca são removidos
     */
    suspend fun removeStaleEntries(): Int = withContext(Dispatchers.IO) {
        val allItems = downloadDao.getAllDownloadsSync()
        val monitoredFolders = folderService.folders.value
        var removed = 0

        // Se não há pastas monitoradas, NÃO deletar nada (pode ser timing issue)
        if (monitoredFolders.isEmpty()) {
            android.util.Log.d("FileSystemScanner", "⚠️ Nenhuma pasta monitorada — skip removeStaleEntries")
            return@withContext 0
        }

        allItems.forEach { item ->
            if (item.status != "completed") return@forEach
            if (!item.url.isNullOrBlank()) return@forEach // Downloads do app sempre mantidos

            val outputPath = item.outputPath.takeIf { it.isNotBlank() } ?: return@forEach

            // NUNCA deletar itens SAF — permissão pode estar temporariamente revogada
            if (outputPath.startsWith("content://")) return@forEach

            // Só deletar arquivos físicos confirmados como inexistentes
            if (!File(outputPath).exists()) {
                downloadDao.delete(item)
                removed++
                android.util.Log.d("FileSystemScanner", "Entrada removida (arquivo físico não existe): ${item.title}")
            }
        }

        if (removed > 0) android.util.Log.d("FileSystemScanner", "removeStaleEntries: $removed itens removidos")
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
