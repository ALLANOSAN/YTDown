package com.example.ytdown.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

private data class QueuedTask(
    val task: suspend () -> Any?,
    val deferred: CompletableDeferred<Any?>
)

class TaskQueue(private val maxConcurrent: Int = 3) {
    init {
        require(maxConcurrent > 0) { "maxConcurrent must be greater than zero" }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val queue = ArrayDeque<QueuedTask>()
    private val mutex = Mutex()
    private var activeCount = 0

    val queueLength: Int
        get() = queue.size

    val totalActive: Int
        get() = activeCount + queue.size

    fun <T> add(task: suspend () -> T): Deferred<T> {
        val deferred = CompletableDeferred<T>()
        scope.launch {
            mutex.withLock {
                @Suppress("UNCHECKED_CAST")
                queue.addLast(QueuedTask(task as suspend () -> Any?, deferred as CompletableDeferred<Any?>))
                processQueueLocked()
            }
        }
        return deferred
    }

    private fun processQueueLocked() {
        while (activeCount < maxConcurrent && queue.isNotEmpty()) {
            val queuedTask = queue.removeFirst()
            activeCount++
            scope.launch {
                try {
                    val result = queuedTask.task()
                    queuedTask.deferred.complete(result)
                } catch (error: Throwable) {
                    queuedTask.deferred.completeExceptionally(error)
                } finally {
                    mutex.withLock {
                        activeCount--
                        processQueueLocked()
                    }
                }
            }
        }
    }
}
