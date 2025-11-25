package com.vdx.backpack.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class BackupPreferences(
    context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "backpack_prefs",
        Context.MODE_PRIVATE
    )

    private val _lastBackupTime = MutableStateFlow(getLastBackupTime())
    val lastBackupTime: Flow<Long> = _lastBackupTime.asStateFlow()

    private val _isAutoBackupEnabled = MutableStateFlow(isAutoBackupEnabled())
    val isAutoBackupEnabled: Flow<Boolean> = _isAutoBackupEnabled.asStateFlow()

    fun saveLastBackupTime(timestamp: Long) {
        prefs.edit { putLong(KEY_LAST_BACKUP, timestamp) }
        _lastBackupTime.value = timestamp
    }

    fun getLastBackupTime(): Long {
        return prefs.getLong(KEY_LAST_BACKUP, 0L)
    }

    fun clearLastBackupTime() {
        prefs.edit { remove(KEY_LAST_BACKUP) }
        _lastBackupTime.value = 0L
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_BACKUP_ENABLED, enabled) }
        _isAutoBackupEnabled.value = enabled
    }

    fun isAutoBackupEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, false)
    }

    companion object {
        private const val KEY_LAST_BACKUP = "last_backup_time"
        private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
    }
}