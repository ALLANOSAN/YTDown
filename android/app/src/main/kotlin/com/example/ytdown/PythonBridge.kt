package com.example.ytdown

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.PyObject
import com.chaquo.python.android.AndroidPlatform

object PythonBridge {
    private var pythonInitialized = false

    fun initializePython(context: Context) {
        if (pythonInitialized) {
            return
        }

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
        pythonInitialized = true
    }

    fun invokePythonJson(context: Context, methodName: String, vararg args: Any?): String {
        initializePython(context)
        return ytdownModule().callAttr(methodName, *args).toString()
    }

    private fun ytdownModule(): PyObject {
        return Python.getInstance().getModule("ytdown")
    }

    fun appFilesDirPath(context: Context): String {
        return context.filesDir.absolutePath
    }
}
