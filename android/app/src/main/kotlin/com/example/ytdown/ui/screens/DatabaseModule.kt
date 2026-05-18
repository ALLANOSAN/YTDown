package com.example.ytdown.di

import android.content.Context
import androidx.room.Room
import com.example.ytdown.core.infrastructure.persistence.AppDatabase
import com.example.ytdown.core.infrastructure.persistence.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "ytdown.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideSongDao(db: AppDatabase): SongDao = db.songDao()
}