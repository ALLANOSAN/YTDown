package com.example.ytdown

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.PyObject
import com.chaquo.python.android.AndroidPlatform

object PythonBridge {
    private var pythonInitialized = false
    lateinit var applicationContext: Context
        private set

    fun initializePython(context: Context) {
        if (pythonInitialized) return

        applicationContext = context.applicationContext

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
        pythonInitialized = true
    }

    fun invokePythonJson(methodName: String, vararg args: Any?): String {
        // Garante que o Python foi inicializado. Se não, lança exceção.
        if (!pythonInitialized) throw IllegalStateException("PythonBridge não inicializado. Chame initializePython(context) primeiro.")
        return ytdownModule().callAttr(methodName, *args).toString()
    }

    private fun ytdownModule(): PyObject = Python.getInstance().getModule("ytdown")

    fun appFilesDirPath(context: Context): String = context.filesDir.absolutePath
}