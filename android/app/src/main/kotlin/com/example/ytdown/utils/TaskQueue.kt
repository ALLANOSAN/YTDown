package com.example.ytdown.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fila de tarefas com controle de concorrência.
 * Migrado do Flutter (lib/utils/task_queue.dart).
 */
class TaskQueue(val maxConcurrent: Int = 3) {
    private val semaphore = Semaphore(maxConcurrent)
    private val _activeCount = AtomicInteger(0)

    val activeCount: Int get() = _activeCount.get()

    /**
     * Adiciona uma tarefa à fila e aguarda sua execução.
     */
    suspend fun <T> add(task: suspend () -> T): Deferred<T> = coroutineScope {
        async {
            semaphore.withPermit {
                _activeCount.incrementAndGet()
                try {
                    task()
                } finally {
                    _activeCount.decrementAndGet()
                }
            }
        }
    }
}
