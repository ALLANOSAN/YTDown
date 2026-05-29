package com.example.ytdown

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.PyObject
import com.chaquo.python.android.AndroidPlatform

object PythonBridge {
    private var pythonInitialized = false
    private const val TAG = "CHAQUOPY_DEBUG"

    lateinit var applicationContext: Context
        private set

    /**
     * Interface que será passada para o Python para reporte de progresso.
     * O Chaquopy mapeia interfaces Kotlin para objetos chamáveis no Python.
     */
    interface PythonProgressCallback {
        fun onProgress(percent: Int)
    }

    fun initializePython(context: Context) {
        if (pythonInitialized) return

        applicationContext = context.applicationContext
        android.util.Log.d(TAG, "🚀 Inicializando Chaquopy em ${context.packageName}")

        try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
                android.util.Log.d(TAG, "✅ Chaquopy iniciado com sucesso.")
            }
            pythonInitialized = true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Falha crítica ao iniciar Chaquopy: ${e.message}", e)
        }
    }

    fun invokePythonJson(methodName: String, vararg args: Any?): String {
        if (!pythonInitialized) {
            android.util.Log.e(TAG, "❌ ERRO: Tentativa de chamar Python antes da inicialização!")
            throw IllegalStateException("PythonBridge não inicializado. Chame initializePython(context) primeiro.")
        }
        
        return try {
            android.util.Log.d(TAG, "🐍 Ponte Kotlin -> Python: chamando '$methodName' com ${args.size} argumentos")
            val result = ytdownModule().callAttr(methodName, *args).toString()
            android.util.Log.d(TAG, "🐍 Ponte Python -> Kotlin: '$methodName' concluído.")
            result
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Erro fatal na ponte Python ($methodName): ${e.message}", e)
            "{\"success\": false, \"error\": \"Bridge error: ${e.message}\", \"stage\": \"bridge_invocation\"}"
        }
    }

    private fun ytdownModule(): PyObject = Python.getInstance().getModule("ytdown")

    fun appFilesDirPath(context: Context): String = context.filesDir.absolutePath
}