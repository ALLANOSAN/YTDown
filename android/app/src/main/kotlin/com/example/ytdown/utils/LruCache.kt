package com.example.ytdown.utils

import android.util.LruCache

/**
 * Cache LRU (Least Recently Used) genérico.
 * Migrado do Flutter (lib/utils/lru_cache.dart) usando a implementação nativa Android.
 */
class MemoryLruCache<K : Any, V : Any>(capacity: Int) {
    private val cache = LruCache<K, V>(capacity)

    fun get(key: K): V? = cache.get(key)

    fun put(key: K, value: V) {
        cache.put(key, value)
    }

    fun containsKey(key: K): Boolean = cache.get(key) != null

    fun clear() {
        cache.evictAll()
    }
}
