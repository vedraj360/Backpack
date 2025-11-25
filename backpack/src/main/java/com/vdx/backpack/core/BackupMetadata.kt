package com.vdx.backpack.core

data class BackupMetadata(
    val fileId: String,
    val fileName: String,
    val timestamp: Long,
    val size: Long
)