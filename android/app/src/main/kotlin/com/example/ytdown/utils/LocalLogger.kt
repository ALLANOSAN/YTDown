package com.example.ytdown.utils

import android.util.Log

object LocalLogger {
    private const val TAG = "YTDown"

    private val urlPattern = Regex("https?:\\/\\/[a-zA-Z0-9\\-\\.]+\\.[a-zA-Z]{2,3}(\\/\\S*)?")
    private val appPathPattern = Regex("\\/data\\/user\\/\\d+\\/[a-zA-Z0-9\\-\\.]+(\\/\\S*)?")
    private val externalPathPattern = Regex("\\/storage\\/emulated\\/\\d+(\\/\\S*)?")

    /**
     * Tira URL (que pode carregar api_key no query string) e caminho do
     * dispositivo antes de qualquer coisa sair para o Crashlytics.
     * Fonte única — o ObservabilityService reusa esta.
     */
    fun sanitize(message: String): String =
        message
            .replace(urlPattern, "[URL_REMOVIDA]")
            .replace(appPathPattern, "[PATH_SISTEMA_REMOVIDO]")
            .replace(externalPathPattern, "[PATH_SISTEMA_REMOVIDO]")

    fun initialize() {
        Log.i(TAG, "Logger inicializado.")
    }

    fun trace(message: String, error: Throwable? = null, tag: String = TAG) {
        Log.v(tag, message, error)
    }

    fun debug(message: String, error: Throwable? = null, tag: String = TAG) {
        Log.d(tag, message, error)
    }

    fun info(message: String, error: Throwable? = null, tag: String = TAG) {
        Log.i(tag, message, error)
    }

    fun warning(message: String, error: Throwable? = null, tag: String = TAG) {
        Log.w(tag, message, error)
    }

    /**
     * Erro que também vai para o Crashlytics.
     *
     * Antes, 66 chamadas de `Log.e` espalhadas pelo app morriam no logcat e a
     * falha nunca saía do aparelho — contra 17 que usavam o ObservabilityService,
     * que é o que o CLAUDE.md manda. Este funil não precisa de injeção de
     * dependência (`object` + singleton estático do Crashlytics), então
     * qualquer classe consegue reportar.
     */
    fun error(message: String, error: Throwable? = null, tag: String = TAG) {
        val safe = sanitize(message)
        Log.e(tag, safe, error)
        reportToCrashlytics(tag, safe, error)
    }

    private fun reportToCrashlytics(tag: String, sanitizedMessage: String, error: Throwable?) {
        try {
            val crashlytics = com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey("tag", tag)
            crashlytics.log("[$tag] $sanitizedMessage")
            error?.let { crashlytics.recordException(it) }
        } catch (e: Throwable) {
            // Firebase ausente (teste de unidade) ou ainda não inicializado:
            // o logcat acima já registrou, não vale derrubar o chamador.
            Log.w(TAG, "Crashlytics indisponivel: ${e.message}")
        }
    }
}
