package com.example.ytdown.core.infrastructure

import java.io.File

class ArchiveExtractor {

    /**
     * Regra 1: Um nível de indentação.
     * Regra 2: Sem ELSE.
     */
    fun extractTarGz(archive: File, destinationDir: File) {
        if (!archive.exists()) return
        if (!destinationDir.exists()) destinationDir.mkdirs()

        val process = ProcessBuilder("tar", "-xzf", archive.absolutePath, "-C", destinationDir.absolutePath)
            .start()
        
        val exitCode = process.waitFor()
        
        if (exitCode != 0) {
            throw RuntimeException("Falha ao descompactar: ${archive.name}")
        }
    }
}