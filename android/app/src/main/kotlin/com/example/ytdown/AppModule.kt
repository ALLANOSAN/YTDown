package com.example.ytdown.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.example.ytdown.*
import com.example.ytdown.core.infrastructure.*
import com.example.ytdown.core.infrastructure.persistence.AppDatabase
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "ytdown.db").build()

    @Provides
    fun provideDownloadDao(db: AppDatabase): DownloadDao = db.downloadDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideStorageResolver(@ApplicationContext context: Context): StorageResolver =
        StorageResolver(context)

    @Provides
    @Singleton
    fun provideMetadataManager(
        @ApplicationContext context: Context,
        scanner: MediaScanner
    ): DownloadMetadataManager {
        val tools = MetadataTools(scanner, MimeTypeResolver())
        return DownloadMetadataManager(tools, MediaInfoParser())
    }

    @Provides
    @Singleton
    fun provideDownloadEngine(
        storage: StorageResolver,
        metadataManager: DownloadMetadataManager
    ): DownloadEngine {
        val executor = NativeProcessExecutor(emptyMap()) // Ajustar env se necessário
        val env = PythonEnvironment(storage.internalBinariesDir())
        val wrapper = YtDlpWrapper(executor, env)
        return DownloadEngine(wrapper, metadataManager)
    }
}