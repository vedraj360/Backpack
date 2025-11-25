package com.vdx.backpack.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vdx.backpack.Backpack
import com.vdx.backpack.core.BackupResult
import timber.log.Timber

class AutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val manager = Backpack.manager

            var lastResult: BackupResult? = null

            manager.backup(applicationContext).collect { result ->
                lastResult = result
                when (result) {
                    is BackupResult.Success -> Timber.d("Auto backup completed: ${result.fileId}")
                    is BackupResult.Failure -> Timber.e(result.error, "Auto backup failed")
                    else -> {  }
                }
            }

            when (lastResult) {
                is BackupResult.Success -> Result.success()
                is BackupResult.Failure -> Result.retry()
                else -> Result.failure()
            }
        } catch (e: Exception) {
            Timber.e(e, "Worker exception. Ensure Backpack.initialize() is called.")
            Result.retry()
        }
    }
}