package com.example.ytdown.core.infrastructure

import com.example.ytdown.core.domain.AssetPath
import java.io.File

// Classe auxiliar para respeitar a Regra 8
class ExtractionTools(
    val assets: AssetExtractor,
    val archives: ArchiveExtractor
)

class BinaryOrchestrator(
    private val tools: ExtractionTools,
    private val storage: StorageResolver
) {
    
    fun setupPythonRuntime(runtimeAsset: AssetPath) {
        val targetDir = storage.internalBinariesDir()
        val tmpFile = File(targetDir, "runtime.tar.gz")
        
        tools.assets.extract(runtimeAsset, tmpFile)
        tools.archives.extractTarGz(tmpFile, targetDir)
        
        // Extrair o script principal e o certificado SSL
        tools.assets.extract(AssetPath("python/ytdown.py"), File(targetDir, "ytdown.py"))
        tools.assets.extract(AssetPath("python/cacert.pem"), File(targetDir, "cacert.pem"))

        tmpFile.delete()
    }

    fun getFfmpegExecutable(): File {
        val nativeDir = storage.nativeLibraryDir()
        return File(nativeDir, "libffmpeg_exe.so")
    }
}