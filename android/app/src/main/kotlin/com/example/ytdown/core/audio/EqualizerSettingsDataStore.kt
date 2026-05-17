package com.example.ytdown.core.audio

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Extensão para facilitar o acesso ao DataStore
val Context.equalizerSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "equalizer_settings")

/**
 * Gerencia a persistência das configurações do Equalizador usando Jetpack Preference DataStore.
 */
@Singleton
class EqualizerSettingsDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val gson: Gson // Injeta Gson para serialização/deserialização de List<Float>
) {
    private val dataStore = context.equalizerSettingsDataStore

    private object PreferencesKeys {
        val IS_ENABLED = booleanPreferencesKey("is_equalizer_enabled")
        val BANDS_GAINS = stringPreferencesKey("equalizer_bands_gains") // Armazena como JSON string
        val PREAMP = floatPreferencesKey("equalizer_preamp")
        val CURRENT_PRESET_NAME = stringPreferencesKey("equalizer_current_preset_name")
    }

    /**
     * Salva o estado atual do EqualizerUiState no DataStore.
     */
    suspend fun saveSettings(state: EqualizerUiState) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_ENABLED] = state.isEnabled
            preferences[PreferencesKeys.BANDS_GAINS] = gson.toJson(state.bandGains.toList())
            preferences[PreferencesKeys.PREAMP] = state.preamp
            preferences[PreferencesKeys.CURRENT_PRESET_NAME] = state.currentPresetId
        }
    }

    /**
     * Carrega o estado do EqualizerUiState do DataStore.
     * Retorna um EqualizerUiState padrão se não houver configurações salvas.
     */
    suspend fun loadSettings(): EqualizerUiState {
        return dataStore.data.map { preferences ->
            val isEnabled = preferences[PreferencesKeys.IS_ENABLED] ?: true
            val bandsJson = preferences[PreferencesKeys.BANDS_GAINS]
            val bandsList = if (bandsJson != null) gson.fromJson<List<Float>>(bandsJson, object : TypeToken<List<Float>>() {}.type) else List(10) { 0f }
            val preamp = preferences[PreferencesKeys.PREAMP] ?: 0f
            val currentPresetId = preferences[PreferencesKeys.CURRENT_PRESET_NAME] ?: "flat"

            EqualizerUiState(isEnabled, preamp, bandsList.toFloatArray(), currentPresetId)
        }.first() // Obtém a primeira (e única) emissão
    }
}