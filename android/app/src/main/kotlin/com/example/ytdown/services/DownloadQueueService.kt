package com.example.ytdown.services

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serviço de Fila e Travas (Locks).
 * Garante que o mesmo vídeo não seja baixado simultaneamente.
 * Migrado do Flutter (lib/services/download_queue_service.dart).
 */
@Singleton
class DownloadQueueService @Inject constructor() {
    
    private val locks = mutableMapOf<String, Mutex>()
    private val globalLock = Mutex()

    /**
     * Executa uma ação garantindo que apenas um processo por 'id' ocorra por vez.
     */
    suspend fun <T> withLock(id: String, action: suspend () -> T): T {
        val mutex = getOrCreateLock(id)
        return mutex.withLock {
            try {
                action()
            } finally {
                releaseLockIfEmpty(id)
            }
        }
    }

    private suspend fun getOrCreateLock(id: String): Mutex {
        return globalLock.withLock {
            locks.getOrPut(id) { Mutex() }
        }
    }

    private suspend fun releaseLockIfEmpty(id: String) {
        // No Kotlin Mutex não temos contador de espera simples, 
        // então mantemos os mutexes no mapa para simplicidade e segurança.
    }
}
