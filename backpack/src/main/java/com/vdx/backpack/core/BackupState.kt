package com.vdx.backpack.core

data class BackupState(
    val isBackupInProgress: Boolean = false,
    val isRestoreInProgress: Boolean = false,
    val progress: Int = 0,
    val lastBackupTimestamp: Long? = null,
    val error: String? = null
)
