package com.example.ytdown.core.audio

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EqualizerRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val PREAMP_KEY = floatPreferencesKey("eq_preamp")
    
    // Salva ganho de uma banda
    suspend fun saveBandGain(index: Int, gain: Float) {
        dataStore.edit { prefs ->
            prefs[floatPreferencesKey("band_$index")] = gain
        }
    }

    // Carrega ganhos salvos
    val savedGains = dataStore.data.map { prefs ->
        FloatArray(10) { i -> prefs[floatPreferencesKey("band_$i")] ?: 0f }
    }
}
