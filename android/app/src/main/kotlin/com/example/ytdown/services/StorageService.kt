package com.example.ytdown.services

import android.app.Activity
import android.content.Context
import com.example.ytdown.StorageService as RootStorageService
import com.example.ytdown.StorageMediaType
import com.example.ytdown.StorageMimeType
import com.example.ytdown.StoragePath
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageService @Inject constructor() {
    fun exportToPublicCollection(
        context: Context,
        sourcePath: StoragePath,
        displayName: String,
        mediaType: StorageMediaType,
        mimeType: StorageMimeType,
        allowUserInteractionFallback: Boolean
    ) {
        RootStorageService.exportToPublicCollection(
            context,
            sourcePath,
            displayName,
            mediaType,
            mimeType,
            allowUserInteractionFallback
        )
    }

    suspend fun syncEditedFileToExported(
        context: Context,
        sourcePath: String,
        exportedPath: String
    ): Boolean {
        return RootStorageService.syncEditedExportedFile(context, sourcePath, exportedPath)
    }

    fun deleteExportedFile(context: Context, exportedPath: String): Boolean {
        return RootStorageService.deleteExportedFile(context, exportedPath)
    }
}
