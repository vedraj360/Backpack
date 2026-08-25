package com.vdx.backpack.vault

import androidx.room.RoomDatabase
import com.vdx.backpack.core.AttachmentType
import com.vdx.backpack.vault.model.ConflictPolicy

data class VaultKitConfig(
    val database: RoomDatabase? = null,
    val dbName: String = database?.openHelper?.databaseName ?: "app_database",
    val backupFilePrefix: String = "vaultkit_backup",
    val encryptionEnabled: Boolean = false,
    val encryptionKey: String? = null,
    val conflictPolicy: ConflictPolicy = ConflictPolicy.ASK_USER,
    val includeAttachmentTypes: Set<AttachmentType> = AttachmentType.values().toSet(),
    /**
     * Optional map of Table Name -> List of Column Names containing attachment paths/JSON.
     * If null (default), VaultKit automatically scans all user tables and text columns dynamically.
     */
    val targetAttachmentColumns: Map<String, List<String>>? = null
)
