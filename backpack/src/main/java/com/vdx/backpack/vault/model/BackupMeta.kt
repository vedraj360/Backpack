package com.vdx.backpack.vault.model

import kotlinx.serialization.Serializable

@Serializable
data class BackupMeta(
    val dbVersion: Int,
    val timestamp: Long,
    val appVersion: String,
    val attachmentCount: Int = 0,
    val missingCount: Int = 0
)
