package com.example.ytdown.core.infrastructure

import com.example.ytdown.core.domain.AssetPath
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orquestra a extração de binários nativos (FFmpeg).
 * O Python agora é gerenciado via Chaquopy, então esta classe foca apenas no FFmpeg.
 */
@Singleton
class BinaryOrchestrator @Inject constructor(
    private val assets: AssetExtractor,
    private val storage: StorageResolver
) {

    /**
     * Garante que o FFmpeg esteja extraído e pronto para uso.
     */
    fun setupNativeBinaries() {
        val targetDir = storage.internalBinariesDir()
        if (!targetDir.exists()) targetDir.mkdirs()

        val ffmpegBin = File(targetDir, "ffmpeg")
        
        // Extrai apenas se não existir (otimização de performance)
        if (!ffmpegBin.exists()) {
            assets.extract(AssetPath("binaries/ffmpeg"), ffmpegBin)
            ffmpegBin.setExecutable(true, false)
        }
    }

    /**
     * Retorna o caminho absoluto do FFmpeg para ser usado pelo yt-dlp.
     */
    fun getFfmpegPath(): String {
        return File(storage.internalBinariesDir(), "ffmpeg").absolutePath
    }
}
