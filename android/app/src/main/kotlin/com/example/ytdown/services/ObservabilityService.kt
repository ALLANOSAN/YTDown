package com.example.ytdown.services

import com.example.ytdown.core.infrastructure.persistence.LibraryDao
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import com.example.ytdown.utils.LocalLogger

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
                LocalLogger.error("Erro ao monitorar logcat", e, "Observability")
            }
        }
    }

    fun trackError(tag: String, message: String, throwable: Throwable? = null, metadata: Map<String, String>? = null) {
        // Metadados extras primeiro — o LocalLogger.error faz log + recordException.
        metadata?.forEach { (key, value) ->
            try {
                FirebaseCrashlytics.getInstance().setCustomKey(key, LocalLogger.sanitize(value))
            } catch (_: Throwable) {
                // Firebase indisponível; o log local abaixo continua valendo.
            }
        }
        LocalLogger.error(message, throwable, tag)
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
