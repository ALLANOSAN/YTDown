package com.example.ytdown.core.infrastructure

import com.example.ytdown.core.domain.ExitCode
import com.example.ytdown.core.domain.ProcessOutput
import java.io.File

class NativeProcessExecutor(private val environment: Map<String, String>) {

    fun run(command: List<String>, workingDir: File): Pair<ExitCode, ProcessOutput> {
        val process = ProcessBuilder(command)
            .directory(workingDir)
            .apply { environment().putAll(environment) }
            .start()

        val output = readStreams(process)
        return ExitCode(process.waitFor()) to output
    }

    private fun readStreams(process: Process): ProcessOutput {
        val out = process.inputStream.bufferedReader().use { it.readText() }
        val err = process.errorStream.bufferedReader().use { it.readText() }
        return ProcessOutput(out, err)
    }
}