package com.example.ytdown.utils

class LruCache<K, V>(capacity: Int) {
    init {
        require(capacity > 0) { "LruCache capacity must be greater than zero" }
    }

    private val map = object : LinkedHashMap<K, V>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean {
            return size > capacity
        }
    }

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V) {
        map[key] = value
    }

    @Synchronized
    fun remove(key: K) {
        map.remove(key)
    }

    @Synchronized
    fun clear() {
        map.clear()
    }

    @Synchronized
    fun containsKey(key: K): Boolean = map.containsKey(key)

    val length: Int
        @Synchronized get() = map.size
}
