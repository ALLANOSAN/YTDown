package com.example.ytdown

import android.app.Application
import com.example.ytdown.utils.LocalLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class YTDownApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LocalLogger.initialize()
        PythonBridge.initializePython(this)
    }
}