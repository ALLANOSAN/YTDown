package com.example.ytdown.core.infrastructure

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolve os diretórios usados pelo yt-dlp: onde o Android extrai as libs nativas
 * (ffmpeg = libffmpeg_exe.so) e o diretório interno da app (cookies.txt, runtime_packages).
 *
 * Não extrai nada: o ffmpeg vem por jniLibs e o Android o instala sozinho.
 */
@Singleton
class BinaryOrchestrator
@Inject
constructor(@param:ApplicationContext private val context: Context) {

    /** Diretório onde o Android extrai as bibliotecas .so (jniLibs). */
    fun getNativeLibDir(): String = context.applicationInfo.nativeLibraryDir

    /** Diretório de arquivos internos da app. */
    fun getAppFilesDir(): String = context.filesDir.absolutePath
}
