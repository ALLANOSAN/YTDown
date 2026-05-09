package com.example.ytdown.di

import android.content.Context
import com.example.ytdown.services.EqualizerManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface EqualizerEntryPoint {
    fun getEqualizerManager(): EqualizerManager
}

object HiltEntryPoints {
    fun getEqualizerManager(context: Context): EqualizerManager {
        return EntryPointAccessors.fromApplication(
            context.applicationContext,
            EqualizerEntryPoint::class.java
        ).getEqualizerManager()
    }
}
