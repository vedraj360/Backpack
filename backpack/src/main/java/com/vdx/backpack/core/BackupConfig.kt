package com.vdx.backpack.core

import androidx.room.RoomDatabase

data class BackupConfig(
    val database: RoomDatabase,
    val folderName: String = "AppBackups",
    val encryptionEnabled: Boolean = true,
    val autoBackupEnabled: Boolean = false,
    val backupIntervalHours: Long = 24,
    val maxBackupFiles: Int = 1,
    val backupFileName: String = "backup",
    val includeAttachmentTypes: Set<AttachmentType> = AttachmentType.values().toSet(),
    val targetAttachmentColumns: Map<String, List<String>>? = null
)