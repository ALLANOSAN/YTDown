package com.example.ytdown.utils

import android.util.Log

object LocalLogger {
    private const val TAG = "YTDown"

    private fun sanitize(message: String): String {
        return message
            .replace(Regex("https?:\\/\\/[a-zA-Z0-9\\-\\.]+\\.[a-zA-Z]{2,3}(\\/\\S*)?"), "[URL_REMOVIDA]")
            .replace(Regex("\\/data\\/user\\/\\d+\\/[a-zA-Z0-9\\-\\.]+(\\/\\S*)?"), "[PATH_SISTEMA_REMOVIDO]")
            .replace(Regex("\\/storage\\/emulated\\/\\d+(\\/\\S*)?"), "[PATH_SISTEMA_REMOVIDO]")
    }

    fun initialize() {
        Log.i(TAG, "Logger inicializado.")
    }

    fun trace(message: String, error: Throwable? = null) {
        Log.v(TAG, message, error)
    }

    fun debug(message: String, error: Throwable? = null) {
        Log.d(TAG, message, error)
    }

    fun info(message: String, error: Throwable? = null) {
        Log.i(TAG, message, error)
    }

    fun warning(message: String, error: Throwable? = null) {
        Log.w(TAG, message, error)
    }

    fun error(message: String, error: Throwable? = null) {
        Log.e(TAG, message, error)
    }
}
