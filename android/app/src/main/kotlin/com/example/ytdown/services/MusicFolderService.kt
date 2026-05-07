package com.example.ytdown.services

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicFolderService @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("music_folders_prefs", Context.MODE_PRIVATE)
    private val KEY_FOLDERS = "selected_music_folders"

    private val _folders = MutableStateFlow<Set<String>>(getPersistedFolders())
    val folders: StateFlow<Set<String>> = _folders.asStateFlow()

    private fun getPersistedFolders(): Set<String> {
        return prefs.getStringSet(KEY_FOLDERS, emptySet()) ?: emptySet()
    }

    fun addFolder(path: String) {
        val current = getPersistedFolders().toMutableSet()
        if (current.add(path)) {
            prefs.edit().putStringSet(KEY_FOLDERS, current).apply()
            _folders.value = current
        }
    }

    fun removeFolder(path: String) {
        val current = getPersistedFolders().toMutableSet()
        if (current.remove(path)) {
            prefs.edit().putStringSet(KEY_FOLDERS, current).apply()
            _folders.value = current
        }
    }
}
