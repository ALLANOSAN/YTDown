package com.example.ytdown.core.artwork

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtworkRotationController @Inject constructor() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _artworkMode = MutableStateFlow(ArtworkMode.ALBUM)
    val artworkMode: StateFlow<ArtworkMode> = _artworkMode.asStateFlow()

    // Caminhos das artwork atuais (setados pelo player)
    private val _albumArtPath = MutableStateFlow<String?>(null)
    private val _artistArtPath = MutableStateFlow<String?>(null)

    init {
        startRotation()
    }

    /**
     * Atualiza os caminhos das artwork. Chamado pelo player quando a track muda.
     */
    fun updateArtworkPaths(albumPath: String?, artistPath: String?) {
        _albumArtPath.value = albumPath
        _artistArtPath.value = artistPath
    }

    private fun startRotation() {
        scope.launch {
            while (isActive) {
                delay(10_000)
                val currentMode = _artworkMode.value

                if (currentMode == ArtworkMode.ALBUM) {
                    // Só muda para ARTIST se a foto da banda existir
                    if (!_artistArtPath.value.isNullOrBlank()) {
                        _artworkMode.value = ArtworkMode.ARTIST
                    }
                    // Se não tem foto da banda, fica no ALBUM (não alterna)
                } else {
                    // Volta para ALBUM sempre (capa do álbum sempre deve existir)
                    _artworkMode.value = ArtworkMode.ALBUM
                }
            }
        }
    }
}
