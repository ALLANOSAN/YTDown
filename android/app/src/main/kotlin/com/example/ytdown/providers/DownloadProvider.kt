package com.example.ytdown.providers

import com.example.ytdown.core.domain.DownloadItemEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DownloadProvider {
    private val _downloadItems = MutableStateFlow<List<DownloadItemEntity>>(emptyList())
    val downloadItems: StateFlow<List<DownloadItemEntity>> = _downloadItems.asStateFlow()

    fun update(items: List<DownloadItemEntity>) {
        _downloadItems.value = items
    }

    fun addOrUpdate(item: DownloadItemEntity) {
        val current = _downloadItems.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == item.id }
        val hasExisting = existingIndex >= 0
        if (hasExisting) {
            current[existingIndex] = item
        }
        if (!hasExisting) {
            current.add(item)
        }
        _downloadItems.value = current
    }

    fun remove(id: String) {
        _downloadItems.value = _downloadItems.value.filter { it.id != id }
    }

    fun clear() {
        _downloadItems.value = emptyList()
    }
}
