package com.example.ytdown.core.audio

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.un4seen.bass.BASS
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * BassFXEngine - Gerencia o pipeline DSP e efeitos usando BASS_FX.
 * Implementa Equalizador Paramétrico e efeitos de Tempo/Pitch.
 *
 * Carrega automaticamente as configurações salvas do DataStore em cada
 * setupEqualizer(), ou seja, o EQ reflete as preferências mesmo sem abrir a tela.
 */
@Singleton
class BassFXEngine @Inject constructor(
    private val playbackEngine: BassPlaybackEngine,
    @param:ApplicationContext private val context: Context
) {
    private val TAG = "BassFXEngine"

    // Equalizador (10 bandas padrão)
    private val eqBands = IntArray(10)
    private val eqGains = FloatArray(10) { 0f }
    private var currentPreamp = 1.0f
    private val frequencies = floatArrayOf(31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

    private val dataStore = context.equalizerSettingsDataStore

    // Ponte: escopo para carregar configurações de forma assíncrona
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        // Pré-carrega as configurações salvas do equalizador na inicialização,
        // assim o EQ reflete as preferências mesmo sem nunca abrir a tela.
        scope.launch {
            loadEQFromStorageAsync()
        }
    }

    /**
     * Aplica o Equalizador ao canal ativo.
     * Carrega as configurações salvas do DataStore automaticamente,
     * então o EQ reflete as preferências mesmo sem abrir a tela.
     */
    fun setupEqualizer() {
        val channel = playbackEngine.getActiveChannel()
        if (channel == 0) return

        // Carrega as configurações do DataStore se ainda não foram — assíncrono
        // A pré-carga no init já deve ter completado, mas garantimos em background
        scope.launch {
            loadEQFromStorageAsync()
        }

        // Aplica o ganho do Preamp global do EQ
        BASS.BASS_ChannelSetAttribute(channel, BASS.BASS_ATTRIB_VOL, currentPreamp)

        for (i in 0 until 10) {
            if (eqBands[i] != 0) {
                BASS.BASS_ChannelRemoveFX(channel, eqBands[i])
                eqBands[i] = 0
            }

            eqBands[i] = BASS.BASS_ChannelSetFX(channel, BASS.BASS_FX_DX8_PARAMEQ, 0)

            if (eqBands[i] != 0) {
                val params = BASS.BASS_DX8_PARAMEQ()
                params.fCenter = frequencies[i]
                params.fBandwidth = 12f
                params.fGain = eqGains[i]
                BASS.BASS_FXSetParameters(eqBands[i], params)
            }
        }
    }

    /**
     * Sincroniza os ganhos do EQ a partir de uma fonte externa
     * (ex.: quando a tela do equalizador envia os valores via ViewModel).
     */
    fun loadEQState(gains: FloatArray, preampDb: Float) {
        if (gains.size != 10) return
        gains.copyInto(eqGains)
        currentPreamp = Math.pow(10.0, (preampDb / 20.0)).toFloat()
        val channel = playbackEngine.getActiveChannel()
        if (channel != 0) {
            BASS.BASS_ChannelSetAttribute(channel, BASS.BASS_ATTRIB_VOL, currentPreamp)
            for (i in 0 until 10) {
                if (eqBands[i] != 0) {
                    val params = BASS.BASS_DX8_PARAMEQ()
                    if (BASS.BASS_FXGetParameters(eqBands[i], params)) {
                        params.fGain = eqGains[i]
                        BASS.BASS_FXSetParameters(eqBands[i], params)
                    }
                }
            }
        }
    }

    /**
     * Carrega as configurações do equalizador do DataStore.
     * Called automatically on every setupEqualizer() so the EQ
     * reflects saved settings even without opening the equalizer screen.
     */
    private suspend fun loadEQFromStorageAsync() {
        try {
            val prefs = dataStore.data.first()
            val isEnabled = prefs[booleanPreferencesKey("is_equalizer_enabled")] ?: true
            if (!isEnabled) {
                eqGains.fill(0f)
                currentPreamp = 1.0f
                return
            }
            val bandsJson = prefs[stringPreferencesKey("equalizer_bands_gains")]
            if (bandsJson != null) {
                val bands: List<Float> = Gson().fromJson(bandsJson, object : TypeToken<List<Float>>() {}.type)
                val n = minOf(bands.size, 10)
                for (i in 0 until n) {
                    eqGains[i] = bands[i]
                }
            }
            currentPreamp = Math.pow(10.0, ((prefs[floatPreferencesKey("equalizer_preamp")] ?: 0f) / 20.0)).toFloat()
        } catch (_: Exception) {
            // DataStore not available yet — use defaults (all flat)
        }
    }

    /**
     * Ajusta o ganho de uma banda específica (-15dB a +15dB).
     */
    fun setBandGain(bandIndex: Int, gain: Float) {
        if (bandIndex !in 0 until 10) return

        eqGains[bandIndex] = gain.coerceIn(-15f, 15f)

        val handle = eqBands[bandIndex]
        if (handle != 0) {
            val params = BASS.BASS_DX8_PARAMEQ()
            if (BASS.BASS_FXGetParameters(handle, params)) {
                params.fGain = eqGains[bandIndex]
                BASS.BASS_FXSetParameters(handle, params)
            }
        }
    }

    fun setPreamp(gainDb: Float) {
        // Converte dB para escala linear (0.0 a 2.0)
        currentPreamp = Math.pow(10.0, (gainDb / 20.0)).toFloat()
        val channel = playbackEngine.getActiveChannel()
        if (channel != 0) BASS.BASS_ChannelSetAttribute(channel, BASS.BASS_ATTRIB_VOL, currentPreamp)
    }

    fun getBandGain(bandIndex: Int): Float = eqGains.getOrElse(bandIndex) { 0f }

    /**
     * Aplica Reverb ao canal ativo.
     */
    fun setReverb(mix: Float) {
        val channel = playbackEngine.getActiveChannel()
        if (channel == 0) return

        val rvHandle = BASS.BASS_ChannelSetFX(channel, BASS.BASS_FX_DX8_REVERB, 1)
        if (rvHandle != 0) {
            val params = BASS.BASS_DX8_REVERB()
            params.fInGain = 0f
            params.fReverbMix = mix.coerceIn(-96f, 0f)
            BASS.BASS_FXSetParameters(rvHandle, params)
        }
    }
}