package com.example.ytdown.core.business

import com.example.ytdown.core.domain.ExitCode
import com.example.ytdown.core.infrastructure.NativeProcessExecutor
import com.example.ytdown.core.infrastructure.PythonEnvironment
import java.io.File

class YtDlpWrapper(
    private val executor: NativeProcessExecutor,
    private val env: PythonEnvironment
) {
    
    // Regra 1: Um nível de indentação
    // Regra 3: Wrap primitives (List de strings para comando)
    fun downloadVideo(url: String, outputDir: File): ExitCode {
        val pythonPath = env.getBinaryPath("python3.13").absolutePath
        val scriptPath = env.getBinaryPath("ytdown.py").absolutePath
        
        val command = listOf(
            pythonPath, 
            scriptPath, 
            "--url", url, 
            "--output", outputDir.absolutePath
        )

        return executor.run(command, outputDir).first
    }
}