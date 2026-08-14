package com.example.ytdown.services

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
import com.example.ytdown.core.infrastructure.persistence.SongDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import com.example.ytdown.utils.LocalLogger

@Singleton
class MusicFolderService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val songDao: SongDao
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("music_folders_prefs", Context.MODE_PRIVATE)
    private val KEY_FOLDERS = "selected_music_folders"

    private val _folders = MutableStateFlow<Set<String>>(getPersistedFolders())
    val folders: StateFlow<Set<String>> = _folders.asStateFlow()

    companion object {
        private const val TAG = "MusicFolderService"
    }

    init {
        // Validar pastas na inicialização - URI do SAF podem expirar no Android 11+
        validateAndCleanFolders()
    }

    private fun getPersistedFolders(): Set<String> {
        return prefs.getStringSet(KEY_FOLDERS, emptySet()) ?: emptySet()
    }

    /**
     * Valida as pastas salvas - URIs do SAF (content://) podem expirar no Android 11+
     * Remove pastas que não são mais acessíveis
     */
    private fun validateAndCleanFolders() {
        val savedFolders = getPersistedFolders()
        val validFolders = mutableSetOf<String>()

        for (folder in savedFolders) {
            if (folder.startsWith("content://")) {
                // Verificar se a URI ainda é válida
                if (isUriValid(folder)) {
                    validFolders.add(folder)
                } else {
                    Log.w(TAG, "Pasta expirou e será removida: $folder")
                }
            } else {
                // File paths são sempre válidos
                validFolders.add(folder)
            }
        }

        // Atualizar se houver diferenças
        if (validFolders.size != savedFolders.size) {
            prefs.edit().putStringSet(KEY_FOLDERS, validFolders).apply()
            _folders.value = validFolders
            Log.d(TAG, "Pastas válidas após validação: ${validFolders.size}")
        }
    }

    private fun isUriValid(uriString: String): Boolean {
        return try {
            val uri = Uri.parse(uriString)
            // Verificar se consegue abrir um documento raiz
            val documentId = DocumentsContract.getTreeDocumentId(uri)
            // Tentar listar arquivos para verificar acesso
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use { 
                it.count >= 0  // Se não lançar exceção, tem acesso
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun addFolder(path: String) {
        val current = getPersistedFolders().toMutableSet()
        
        // Se for uma URI do SAF (content://), tentamos pedir permissão persistente
        if (path.startsWith("content://")) {
            try {
                val uri = Uri.parse(path)
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                Log.d(TAG, "Permissão persistente adquirida para: $path")
            } catch (e: Exception) {
                LocalLogger.error("Falha ao adquirir permissão persistente para $path: ${e.message}", tag = TAG)
            }
        }

        if (current.add(path)) {
            prefs.edit().putStringSet(KEY_FOLDERS, current).apply()
            _folders.value = current
            Log.d(TAG, "Pasta adicionada: $path, total: ${current.size}")
        }
    }


    fun removeFolder(path: String) {
        val current = getPersistedFolders().toMutableSet()
        if (current.remove(path)) {
            prefs.edit().putStringSet(KEY_FOLDERS, current).apply()
            _folders.value = current
            Log.d(TAG, "Pasta removida: $path, total: ${current.size}")

            // Limpar itens do banco que estavam nessa pasta (sem apagar arquivos físicos)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val normalizedPath = path.trimEnd('/') + "/"
                    val allDownloads = downloadDao.getAllDownloadsSync()

                    val toRemove = allDownloads.filter { item ->
                        item.status == "completed" &&
                        item.outputPath.isNotBlank() &&
                        item.url.isNullOrBlank() && // só órfãos (pastas adicionadas), não downloads do app
                        outputPathMatchesFolder(item.outputPath, normalizedPath)
                    }

                    toRemove.forEach { item ->
                        downloadDao.delete(item)
                    }

                    // Remover também do SongDao
                    songDao.deleteByPathPrefix(normalizedPath)

                    Log.d(TAG, "🧹 Removidos ${toRemove.size} itens da biblioteca (pasta: $path)")
                } catch (e: Exception) {
                    LocalLogger.error("Erro ao limpar itens da pasta $path: ${e.message}", tag = TAG)
                }
            }
        }
    }

    /**
     * Verifica se o outputPath de um item pertence à pasta sendo removida.
     * Suporta tanto File API quanto SAF (content://).
     */
    private fun outputPathMatchesFolder(outputPath: String, folderPrefix: String): Boolean {
        return if (folderPrefix.startsWith("content://")) {
            // SAF: comparar tree ID
            try {
                val folderTree = Uri.parse(folderPrefix.trimEnd('/')).path?.substringAfter("/tree/")?.substringBefore("/")
                val fileTree = Uri.parse(outputPath).path?.substringAfter("/tree/")?.substringBefore("/")
                folderTree != null && fileTree != null && folderTree == fileTree
            } catch (e: Exception) {
                false
            }
        } else {
            // File API: prefix match com /
            outputPath.startsWith(folderPrefix)
        }
    }
}
