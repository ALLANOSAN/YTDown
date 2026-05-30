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
import com.example.ytdown.core.infrastructure.persistence.LibraryDao
import com.example.ytdown.data.local.metal.database.MetalDatabase
import com.example.ytdown.data.repository.metal.MetalRepository
import com.example.ytdown.services.CoverArtArchiveService
import com.example.ytdown.services.MusicBrainzService
import com.example.ytdown.services.ObservabilityService
import com.example.ytdown.services.StorageService
import com.example.ytdown.core.audio.PlaybackController
import com.example.ytdown.core.audio.PlaybackActionDispatcher
import com.example.ytdown.core.audio.PlaybackActionDispatcherImpl
import com.google.gson.Gson
import com.google.gson.GsonBuilder
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
                    .addMigrations(*AppDatabase.ALL_MIGRATIONS)
                    .fallbackToDestructiveMigration()
                    .build()

    @Provides
    @Singleton
    fun provideDownloadDao(db: AppDatabase): DownloadDao = db.downloadDao()

    @Provides
    @Singleton
    fun provideLibraryDao(db: AppDatabase): LibraryDao = db.libraryDao()

    // SongDao já está no DatabaseModule

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

    @Provides @Singleton fun provideMimeTypeResolver(): MimeTypeResolver = MimeTypeResolver()

    @Provides
    @Singleton
    fun provideMetadataTools(scanner: MediaScanner, resolver: MimeTypeResolver): MetadataTools =
            MetadataTools(scanner, resolver)

    @Provides @Singleton fun provideMediaInfoParser(): MediaInfoParser = MediaInfoParser()

    // FIX #2: StorageService is now a proper injectable class
    @Provides
    @Singleton
    fun provideStorageService(): StorageService = StorageService()

    @Provides
    @Singleton
    fun provideDownloadMetadataManager(
            tools: MetadataTools,
            parser: MediaInfoParser,
            ytDlp: YtDlpWrapper,
            storageService: StorageService,
            orchestrator: BinaryOrchestrator,
            @ApplicationContext context: Context
    ): DownloadMetadataManager =
            DownloadMetadataManager(tools, parser, ytDlp, storageService, orchestrator, context)

    @Provides
    @Singleton
    fun providePythonEnvironment(@ApplicationContext context: Context): PythonEnvironment {
        val filesDir = context.filesDir
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        return PythonEnvironment(filesDir, nativeLibDir)
    }

    @Provides
    @Singleton
    fun provideYtDlpWrapper(
            env: PythonEnvironment,
            binaryOrchestrator: BinaryOrchestrator,
            observabilityService: ObservabilityService
    ): YtDlpWrapper = YtDlpWrapper(env, binaryOrchestrator, observabilityService)

    @Provides
    @Singleton
    fun provideDownloadEngine(
            ytDlp: YtDlpWrapper,
            metadataManager: DownloadMetadataManager,
            observabilityService: ObservabilityService
    ): DownloadEngine = DownloadEngine(ytDlp, metadataManager, observabilityService)

    @Provides
    @Singleton
    fun provideDownloadRepository(dao: DownloadDao, storage: StorageResolver): DownloadRepository =
            DownloadRepository(dao, storage)

    @Provides
    @Singleton
    fun provideAssetExtractor(@ApplicationContext context: Context): AssetExtractor =
            AssetExtractor(context)

    @Provides @Singleton fun provideArchiveExtractor(): ArchiveExtractor = ArchiveExtractor()

    @Provides
    @Singleton
    fun provideBinaryOrchestrator(
            assets: AssetExtractor,
            storage: StorageResolver,
            @ApplicationContext context: Context
    ): BinaryOrchestrator = BinaryOrchestrator(assets, storage, context)

    // =====================================================
    // METAL MODULE - Sistema de Descoberta de Metal
    // =====================================================
    
    @Provides
    @Singleton
    fun provideMetalDatabase(@ApplicationContext context: Context): MetalDatabase =
            MetalDatabase.getInstance(context)
    
    @Provides
    @Singleton
    fun provideMusicBrainzService(): MusicBrainzService = MusicBrainzService()
    
    @Provides
    @Singleton
    fun provideCoverArtArchiveService(): CoverArtArchiveService = CoverArtArchiveService()

    @Provides
    @Singleton
    fun provideMetalRepository(
            database: MetalDatabase,
            musicBrainzService: MusicBrainzService,
            coverArtService: CoverArtArchiveService
    ): MetalRepository = MetalRepository(database, musicBrainzService, coverArtService)

    // Player Controller
    @Provides
    @Singleton
    fun providePlaybackController(
        engineProvider: javax.inject.Provider<com.example.ytdown.core.audio.BassPlaybackEngine>,
        bassAdapterProvider: dagger.Lazy<com.example.ytdown.core.audio.BassMediaSessionAdapter>,
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
    ): PlaybackController = PlaybackController(engineProvider, bassAdapterProvider, context)

    @Provides
    @Singleton
    fun providePlaybackActionDispatcher(
        controller: PlaybackController,
        engine: com.example.ytdown.core.audio.BassPlaybackEngine
    ): PlaybackActionDispatcher = PlaybackActionDispatcherImpl(controller, engine)

    // Gson Provider
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .serializeNulls()
            .setPrettyPrinting()
            .create()
    }
}
