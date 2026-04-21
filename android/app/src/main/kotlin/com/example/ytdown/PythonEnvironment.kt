package com.example.ytdown.core.infrastructure

import java.io.File

class PythonEnvironment(private val rootDir: File) {
    
    // Regra 6: Uma unidade por linha
    fun buildVariables(): Map<String, String> {
        val env = mutableMapOf<String, String>()
        val pythonHome = rootDir.absolutePath + "/python_runtime"
        
        env["PYTHONHOME"] = pythonHome
        env["PYTHONPATH"] = "$pythonHome/lib/python3.13"
        env["SSL_CERT_FILE"] = rootDir.absolutePath + "/cacert.pem"
        env["LD_LIBRARY_PATH"] = rootDir.absolutePath + "/lib"
        
        return env
    }

    fun getBinaryPath(name: String): File {
        return File(rootDir, name)
    }
}