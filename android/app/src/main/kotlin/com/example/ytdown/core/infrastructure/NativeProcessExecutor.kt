package com.example.ytdown.core.infrastructure

import android.util.Log
import com.example.ytdown.core.domain.ExitCode
import com.example.ytdown.core.domain.ProcessOutput
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

class NativeProcessExecutor(private val environment: Map<String, String>) {

    companion object {
        private const val TAG = "NativeProcessExecutor"
        /** FIX #6: Default timeout — 30 minutes for large video downloads */
        private const val DEFAULT_TIMEOUT_SECONDS = 30 * 60L
    }

    /**
     * Runs a native process with timeout protection.
     *
     * FIX #6: Added [timeoutSeconds] parameter. If the process does not terminate
     * within the given time, it is forcibly destroyed and an error ExitCode is returned.
     */
    fun run(
        command: List<String>,
        workingDir: File,
        onProgress: ((String) -> Unit)? = null,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS
    ): Pair<ExitCode, ProcessOutput> {
        val process = ProcessBuilder(command)
            .directory(workingDir)
            .apply {
                environment().putAll(environment)
                redirectErrorStream(true) // Merge stdout and stderr for progress parsing
            }
            .start()

        val output = readStreamLineByLine(process.inputStream, onProgress)

        // FIX #6: Use waitFor with timeout instead of indefinite blocking
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            Log.w(TAG, "Process timed out after ${timeoutSeconds}s, destroying forcibly: ${command.first()}")
            process.destroyForcibly()
            process.waitFor(10, TimeUnit.SECONDS) // give it a moment to die
            return ExitCode(124) to ProcessOutput(output, "Process timed out after ${timeoutSeconds}s")
        }

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
