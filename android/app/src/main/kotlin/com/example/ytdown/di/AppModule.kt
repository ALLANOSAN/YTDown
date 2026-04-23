package com.example.ytdown.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.example.ytdown.DownloadMetadataManager
import com.example.ytdown.MetadataTools
import com.example.ytdown.core.business.DownloadEngine
import com.example.ytdown.core.business.DownloadRepository
import com.example.ytdown.core.business.MediaInfoParser
import com.example.ytdown.core.business.YtDlpWrapper
import com.example.ytdown.core.infrastructure.*
import com.example.ytdown.core.infrastructure.persistence.AppDatabase
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
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
        Room.databaseBuilder(context, AppDatabase::class.java, "ytdown.db")
            .fallbackToDestructiveMigration()
            .build()

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
    fun provideMediaScanner(@ApplicationContext context: Context): MediaScanner =
        MediaScanner(context)

    @Provides
    @Singleton
    fun provideMimeTypeResolver(): MimeTypeResolver = MimeTypeResolver()

    @Provides
    @Singleton
    fun provideMetadataTools(
        scanner: MediaScanner,
        resolver: MimeTypeResolver
    ): MetadataTools = MetadataTools(scanner, resolver)

    @Provides
    @Singleton
    fun provideMediaInfoParser(): MediaInfoParser = MediaInfoParser()

    @Provides
    @Singleton
    fun provideDownloadMetadataManager(
        tools: MetadataTools,
        parser: MediaInfoParser
    ): DownloadMetadataManager = DownloadMetadataManager(tools, parser)

    @Provides
    @Singleton
    fun providePythonEnvironment(@ApplicationContext context: Context): PythonEnvironment =
        PythonEnvironment(context.filesDir)

    @Provides
    @Singleton
    fun provideNativeProcessExecutor(env: PythonEnvironment): NativeProcessExecutor =
        NativeProcessExecutor(env.buildVariables())

    @Provides
    @Singleton
    fun provideYtDlpWrapper(
        executor: NativeProcessExecutor,
        env: PythonEnvironment
    ): YtDlpWrapper = YtDlpWrapper(executor, env)

    @Provides
    @Singleton
    fun provideDownloadEngine(
        ytDlp: YtDlpWrapper,
        metadataManager: DownloadMetadataManager
    ): DownloadEngine = DownloadEngine(ytDlp, metadataManager)

    @Provides
    @Singleton
    fun provideDownloadRepository(dao: DownloadDao): DownloadRepository =
        DownloadRepository(dao)

    @Provides
    @Singleton
    fun provideMusicPlayer(@ApplicationContext context: Context): ExoPlayer {
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        return ExoPlayer.Builder(context)
            .setAudioAttributes(attributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideAssetExtractor(@ApplicationContext context: Context): AssetExtractor =
        AssetExtractor(context)

    @Provides
    @Singleton
    fun provideArchiveExtractor(): ArchiveExtractor = ArchiveExtractor()

    @Provides
    @Singleton
    fun provideBinaryOrchestrator(
        assets: AssetExtractor,
        archives: ArchiveExtractor,
        storage: StorageResolver
    ): BinaryOrchestrator {
        val tools = ExtractionTools(assets, archives)
        return BinaryOrchestrator(tools, storage)
    }
}