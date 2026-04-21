package com.example.ytdown

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class YTDownApplication : Application()
    override fun onCreate() {
        super.onCreate()
        PythonBridge.initializePython(this)
    }