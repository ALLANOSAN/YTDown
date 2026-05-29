package com.example.ytdown.core.infrastructure.di

import com.example.ytdown.core.media.MediaImportProcessor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ImportProcessorEntryPoint {
    fun mediaImportProcessor(): MediaImportProcessor
}
