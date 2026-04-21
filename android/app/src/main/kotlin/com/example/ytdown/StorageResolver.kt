package com.example.ytdown.core.infrastructure

import android.content.Context
import android.os.Environment
import java.io.File

class StorageResolver(private val context: Context) {

    fun internalBinariesDir(): File = context.filesDir

    fun nativeLibraryDir(): File = File(context.applicationInfo.nativeLibraryDir)

    // Regra 2: Sem ELSE (Early return logic)
    fun publicDownloadsDir(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val appFolder = File(downloads, "YTDown")
        
        if (!appFolder.exists()) appFolder.mkdirs()
        
        return appFolder
    }
}