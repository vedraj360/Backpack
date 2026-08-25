package com.vdx.backpack.vault.model

import android.net.Uri

sealed class LocalBackupResult {
    data class InProgress(val progress: Int, val message: String) : LocalBackupResult()
    data class Success(val timestamp: Long, val outputPath: String = "") : LocalBackupResult()
    data class Failure(val error: Throwable) : LocalBackupResult()
    data class ConflictDetected(
        val conflictingFiles: List<String>,
        val pendingImportUri: Uri
    ) : LocalBackupResult()
}
