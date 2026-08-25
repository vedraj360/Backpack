package com.vdx.backpack.vault.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocalBackupPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _lastBackupTimestamp =
        MutableStateFlow(prefs.getLong(KEY_LAST_BACKUP_TIMESTAMP, 0L))
    val lastBackupTimestamp: StateFlow<Long> = _lastBackupTimestamp.asStateFlow()

    private val _lastSafUriString =
        MutableStateFlow(prefs.getString(KEY_LAST_SAF_URI, "") ?: "")
    val lastSafUriString: StateFlow<String> = _lastSafUriString.asStateFlow()

    private val _lastBackupOutputPath =
        MutableStateFlow(prefs.getString(KEY_LAST_OUTPUT_PATH, "") ?: "")
    val lastBackupOutputPath: StateFlow<String> = _lastBackupOutputPath.asStateFlow()

    fun getLastBackupTimestamp(): Long = prefs.getLong(KEY_LAST_BACKUP_TIMESTAMP, 0L)

    fun setLastBackupTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_BACKUP_TIMESTAMP, timestamp).apply()
        _lastBackupTimestamp.value = timestamp
    }

    fun getLastSafUri(): String = prefs.getString(KEY_LAST_SAF_URI, "") ?: ""

    fun setLastSafUri(uri: String) {
        prefs.edit().putString(KEY_LAST_SAF_URI, uri).apply()
        _lastSafUriString.value = uri
    }

    fun getLastBackupPath(): String = prefs.getString(KEY_LAST_OUTPUT_PATH, "") ?: ""

    fun setLastBackupPath(path: String) {
        prefs.edit().putString(KEY_LAST_OUTPUT_PATH, path).apply()
        _lastBackupOutputPath.value = path
    }

    fun clear() {
        prefs.edit().clear().apply()
        _lastBackupTimestamp.value = 0L
        _lastSafUriString.value = ""
        _lastBackupOutputPath.value = ""
    }

    companion object {
        private const val PREFS_NAME = "vaultkit_backup_prefs"
        private const val KEY_LAST_BACKUP_TIMESTAMP = "key_last_backup_timestamp"
        private const val KEY_LAST_SAF_URI = "key_last_saf_uri"
        private const val KEY_LAST_OUTPUT_PATH = "key_last_output_path"
    }
}
