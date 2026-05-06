package com.example.ytdown.core.infrastructure

import android.content.Context
import com.example.ytdown.core.domain.AssetPath
import java.io.File
import java.io.FileOutputStream

class AssetExtractor(private val context: Context) {

    fun extract(asset: AssetPath, destination: File) {
        if (destination.exists()) return
        destination.parentFile?.mkdirs()
        performCopy(asset, destination)
    }

    private fun performCopy(asset: AssetPath, destination: File) {
        context.assets.open(asset.value).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
        destination.setExecutable(true)
    }
}
