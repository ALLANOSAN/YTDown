package com.example.ytdown.core.infrastructure

import java.io.File

/**
 * Gerencia o ambiente Python no Android.
 * Agora integrado com Chaquopy, foca em fornecer caminhos de sistema e libs nativas (FFmpeg).
 */
class PythonEnvironment(
    private val appFilesDir: File,
    private val nativeLibDir: String
) {
    fun getAppFilesDir(): String = appFilesDir.absolutePath

    fun getNativeLibDir(): String = nativeLibDir

    /**
     * O Chaquopy gerencia o runtime, então consideramos sempre pronto 
     * se o plugin estiver ativo.
     */
    fun isRuntimeReady(): Boolean = true
}
