package com.example.ytdown.core.infrastructure

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orquestra a extração de binários nativos (FFmpeg). O Python agora é gerenciado via Chaquopy,
 * então esta classe foca apenas no FFmpeg.
 */
@Singleton
class BinaryOrchestrator
@Inject
constructor(
        private val assets: AssetExtractor,
        private val storage: StorageResolver,
        @param:ApplicationContext private val context: Context
) {

    /**
     * Garante que os binários nativos necessários (como libc++_shared.so) estejam acessíveis. No
     * Android 10+, eles devem estar no diretório jniLibs e são carregados pelo sistema. Esta função
     * pode ser vazia se o build.sh já cuidar de tudo.
     */
    fun setupNativeBinaries() {
        // O script build_ffmpeg_android.sh já instala libc++_shared.so em jniLibs.
        // O sistema Android se encarrega de carregá-lo.
    }

    /** Retorna o diretório onde o Android extrai as bibliotecas .so (jniLibs) */
    fun getNativeLibDir(): String = context.applicationInfo.nativeLibraryDir

    /** Retorna o diretório de arquivos internos da app */
    fun getAppFilesDir(): String = context.filesDir.absolutePath
}
