package com.vdx.backpack.core

sealed class BackupResult {
    data class Success(val fileId: String, val timestamp: Long) : BackupResult()
    data class Failure(val error: Throwable) : BackupResult()
    data class InProgress(val progress: Int) : BackupResult()
}