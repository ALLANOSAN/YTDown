package com.example.ytdown.core.infrastructure

import com.example.ytdown.core.domain.ExitCode
import com.example.ytdown.core.domain.ProcessOutput
import java.io.File
import java.io.InputStream

class NativeProcessExecutor(private val environment: Map<String, String>) {

    fun run(
        command: List<String>, 
        workingDir: File, 
        onProgress: ((String) -> Unit)? = null
    ): Pair<ExitCode, ProcessOutput> {
        val process = ProcessBuilder(command)
            .directory(workingDir)
            .apply { 
                environment().putAll(environment)
                redirectErrorStream(true) // Merge stdout and stderr for progress parsing
            }
            .start()

        val output = readStreamLineByLine(process.inputStream, onProgress)
        return ExitCode(process.waitFor()) to ProcessOutput(output, "")
    }

    private fun readStreamLineByLine(inputStream: InputStream, onProgress: ((String) -> Unit)?): String {
        val builder = StringBuilder()
        inputStream.bufferedReader().use { reader ->
            var line: String? = reader.readLine()
            while (line != null) {
                builder.append(line).append("\n")
                onProgress?.invoke(line)
                line = reader.readLine()
            }
        }
        return builder.toString()
    }
}