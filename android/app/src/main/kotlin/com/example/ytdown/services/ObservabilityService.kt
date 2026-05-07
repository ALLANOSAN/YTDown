package com.example.ytdown.services

import com.example.ytdown.core.infrastructure.persistence.LibraryDao
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

/**
 * Serviço de Observabilidade com Sanitização de Dados Sensíveis.
 * Migrado do Flutter (lib/utils/logger.dart).
 */
@Singleton
class ObservabilityService @Inject constructor(
    private val libraryDao: LibraryDao
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // Regex para limpar dados sensíveis de produção
    private val urlPattern = Pattern.compile("https?:\\/\\/[a-zA-Z0-9\\-\\.]+\\.[a-zA-Z]{2,3}(\\/\\S*)?")
    private val pathPattern = Pattern.compile("/data/user/\\d+/[a-zA-Z0-9\\-\\.]+(/\\S*)?|/storage/emulated/\\d+(/\\S*)?")

    init {
        startLogcatMonitor()
    }

    private fun startLogcatMonitor() {
        scope.launch(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec("logcat -s System.out")
                val reader = process.inputStream.bufferedReader()
                reader.forEachLine { line ->
                    if (line.contains("[FIREBASE_REPORT]")) {
                        val report = line.substringAfter("[FIREBASE_REPORT] ")
                        trackError("PythonBackend", report)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("Observability", "Erro ao monitorar logcat", e)
            }
        }
    }

    fun trackError(tag: String, message: String, throwable: Throwable? = null, metadata: Map<String, String>? = null) {
        val sanitizedMsg = sanitize(message)
        
        // Log Remoto (Firebase)
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCustomKey("tag", tag)
        
        // Adiciona metadados extras para busca via MCP
        metadata?.forEach { (key, value) ->
            crashlytics.setCustomKey(key, value)
        }

        crashlytics.log("[$tag] $sanitizedMsg")
        throwable?.let { crashlytics.recordException(it) }

        // Log Local
        android.util.Log.e(tag, sanitizedMsg, throwable)
    }

    fun info(tag: String, message: String) {
        val sanitizedMsg = sanitize(message)
        // info() só vai para o logcat local — não polui os relatórios do Crashlytics
        android.util.Log.i(tag, sanitizedMsg)
    }

    private fun sanitize(message: String): String {
        var clean = urlPattern.matcher(message).replaceAll("[URL_REMOVIDA]")
        clean = pathPattern.matcher(clean).replaceAll("[PATH_SISTEMA_REMOVIDO]")
        return clean
    }
}
