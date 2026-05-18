package com.example.ytdown.di

import com.example.ytdown.core.infrastructure.persistence.AppDatabase
import com.example.ytdown.core.infrastructure.persistence.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    // AppDatabase já éprovided por AppModule (com migrations)
    // Aqui só fornecemos os DAOs adicionais

    @Provides
    fun provideSongDao(db: AppDatabase): SongDao = db.songDao()
}