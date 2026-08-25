package com.vdx.backpack.core

import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vdx.backpack.security.SecureEncryption
import com.vdx.backpack.storage.BackupPreferences
import com.vdx.backpack.storage.CloudStorageProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackpackManager(
    val config: BackupConfig,
    private val storageProvider: CloudStorageProvider,
    private val backupPreferences: BackupPreferences
) {

    private var pendingCloudRestoreFileId: String? = null

    fun backup(context: Context): Flow<BackupResult> = flow {
        emit(BackupResult.InProgress(0))

        val timestamp = System.currentTimeMillis()
        val tempExportFile = File(context.cacheDir, "backpack_cloud_export_$timestamp.zip")

        try {
            Timber.d("Starting cloud backup package...")

            emit(BackupResult.InProgress(10))
            val vaultConfig = com.vdx.backpack.vault.VaultKitConfig(
                database = config.database,
                encryptionEnabled = false,
                includeAttachmentTypes = config.includeAttachmentTypes,
                targetAttachmentColumns = config.targetAttachmentColumns
            )

            // 1. Package DB + Attachments into ZIP
            val appVersion = try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                packageInfo.versionName ?: ""
            } catch (_: Exception) { "" }

            var exportFailed = false
            com.vdx.backpack.vault.zip.BackupZipper.export(
                context,
                vaultConfig,
                android.net.Uri.fromFile(tempExportFile),
                appVersion
            ).collect { localResult ->
                when (localResult) {
                    is com.vdx.backpack.vault.model.LocalBackupResult.InProgress -> {
                        emit(BackupResult.InProgress(10 + (localResult.progress * 40 / 100)))
                    }
                    is com.vdx.backpack.vault.model.LocalBackupResult.Failure -> {
                        exportFailed = true
                        throw localResult.error
                    }
                    is com.vdx.backpack.vault.model.LocalBackupResult.Success -> {
                        Timber.d("Archive packaged: ${tempExportFile.length()} bytes")
                    }
                    else -> {}
                }
            }

            if (exportFailed || !tempExportFile.exists()) {
                throw Exception("Packaging backup archive failed")
            }

            // 2. Encrypt ZIP if encryption enabled
            val fileToUpload = if (config.encryptionEnabled) {
                emit(BackupResult.InProgress(55))
                val encryptedFile = File(context.cacheDir, "encrypted_backup_$timestamp.zip")
                val result = SecureEncryption.encryptFile(tempExportFile, encryptedFile)
                if (result.isFailure) {
                    throw result.exceptionOrNull() ?: Exception("Encryption failed")
                }
                Timber.d("Archive encrypted: ${encryptedFile.length()} bytes")
                encryptedFile
            } else {
                tempExportFile
            }

            emit(BackupResult.InProgress(65))
            val uploadResult = storageProvider.uploadBackup(
                fileToUpload,
                generateBackupFileName()
            )
            emit(BackupResult.InProgress(85))

            uploadResult.fold(
                onSuccess = { fileId ->
                    cleanupOldBackups()

                    tempExportFile.delete()
                    if (config.encryptionEnabled) fileToUpload.delete()

                    val completedTimestamp = System.currentTimeMillis()
                    backupPreferences.saveLastBackupTime(completedTimestamp)

                    emit(BackupResult.InProgress(100))
                    Timber.d("Cloud backup completed: $fileId")
                    emit(BackupResult.Success(fileId, completedTimestamp))
                },
                onFailure = { error ->
                    tempExportFile.delete()
                    if (config.encryptionEnabled) fileToUpload.delete()

                    Timber.e(error, "Upload failed")
                    emit(BackupResult.Failure(error))
                }
            )
        } catch (e: Exception) {
            tempExportFile.delete()
            Timber.e(e, "Backup exception")
            emit(BackupResult.Failure(e))
        }
    }

    fun restore(
        context: Context,
        fileId: String,
        conflictPolicy: com.vdx.backpack.vault.model.ConflictPolicy = com.vdx.backpack.vault.model.ConflictPolicy.ASK_USER
    ): Flow<BackupResult> = flow {
        emit(BackupResult.InProgress(0))
        pendingCloudRestoreFileId = fileId

        val timestamp = System.currentTimeMillis()
        val tempDownloadedFile = File(context.cacheDir, "cloud_restore_temp_$timestamp.zip")
        var fileToRestore: File? = null

        try {
            Timber.d("Starting cloud restore for file: $fileId...")

            emit(BackupResult.InProgress(10))
            val downloadResult = storageProvider.downloadBackup(fileId, tempDownloadedFile)

            downloadResult.fold(
                onSuccess = { downloadedFile ->
                    Timber.d("Downloaded: ${downloadedFile.length()} bytes")
                    emit(BackupResult.InProgress(35))

                    fileToRestore = if (config.encryptionEnabled) {
                        emit(BackupResult.InProgress(45))
                        val decrypted = File(context.cacheDir, "decrypted_cloud_restore_$timestamp.zip")
                        val result = SecureEncryption.decryptFile(downloadedFile, decrypted)

                        if (result.isFailure) {
                            downloadedFile.delete()
                            throw result.exceptionOrNull() ?: Exception("Decryption failed")
                        }

                        Timber.d("Decrypted: ${decrypted.length()} bytes")
                        downloadedFile.delete()
                        decrypted
                    } else {
                        downloadedFile
                    }
                    emit(BackupResult.InProgress(55))

                    val vaultConfig = com.vdx.backpack.vault.VaultKitConfig(
                        database = config.database,
                        encryptionEnabled = false,
                        includeAttachmentTypes = config.includeAttachmentTypes,
                        targetAttachmentColumns = config.targetAttachmentColumns
                    )

                    // Stream restore via BackupZipper (supports both V2 ZIP and V1 legacy raw DB)
                    var restoreSuccess = false
                    com.vdx.backpack.vault.zip.BackupZipper.import(
                        context,
                        vaultConfig,
                        android.net.Uri.fromFile(fileToRestore),
                        conflictPolicy
                    ).collect { localResult ->
                        when (localResult) {
                            is com.vdx.backpack.vault.model.LocalBackupResult.InProgress -> {
                                emit(BackupResult.InProgress(55 + (localResult.progress * 45 / 100), localResult.message))
                            }
                            is com.vdx.backpack.vault.model.LocalBackupResult.ConflictDetected -> {
                                emit(BackupResult.ConflictDetected(localResult.conflictingFiles, fileId))
                            }
                            is com.vdx.backpack.vault.model.LocalBackupResult.Success -> {
                                restoreSuccess = true
                                pendingCloudRestoreFileId = null
                                emit(BackupResult.InProgress(100))
                                emit(BackupResult.Success(fileId, System.currentTimeMillis()))
                            }
                            is com.vdx.backpack.vault.model.LocalBackupResult.Failure -> {
                                throw localResult.error
                            }
                        }
                    }

                    fileToRestore?.delete()
                },
                onFailure = { error ->
                    tempDownloadedFile.delete()
                    Timber.e(error, "Cloud download failed")
                    emit(BackupResult.Failure(error))
                }
            )
        } catch (e: Exception) {
            tempDownloadedFile.delete()
            fileToRestore?.delete()
            Timber.e(e, "Cloud restore exception")
            emit(BackupResult.Failure(e))
        }
    }

    /**
     * Resolves pending conflict after [BackupResult.ConflictDetected] for cloud restore.
     */
    fun resolveConflict(context: Context, overwrite: Boolean): Flow<BackupResult> {
        val fileId = pendingCloudRestoreFileId
        if (fileId == null) {
            return flow {
                emit(BackupResult.Failure(IllegalStateException(context.getString(com.vdx.backpack.R.string.vk_error_no_pending_conflict))))
            }
        }

        val policy = if (overwrite) com.vdx.backpack.vault.model.ConflictPolicy.OVERWRITE else com.vdx.backpack.vault.model.ConflictPolicy.SKIP
        return restore(context, fileId, policy)
    }

    suspend fun listAvailableBackups(): Result<List<BackupMetadata>> {
        return storageProvider.listBackups()
    }

    private suspend fun exportDatabase(database: RoomDatabase, context: Context): File =
        withContext(Dispatchers.IO) {
            val dbName = database.openHelper.databaseName ?: throw Exception("Database name is null")
            val dbPath = context.getDatabasePath(dbName)

            try {
                if (!dbPath.exists()) {
                    throw Exception("Database file not found. Try adding some data first.")
                }

                Timber.d("Attempting WAL Checkpoint (TRUNCATE)...")
                if (database.isOpen) {
                    val sqlDb: SupportSQLiteDatabase = database.openHelper.writableDatabase
                    val cursor = sqlDb.query("PRAGMA wal_checkpoint(TRUNCATE)")
                    if (cursor.moveToFirst()) {
                        val blocked = cursor.getInt(0)
                        val total = cursor.getInt(1)
                        val checkpointed = cursor.getInt(2)
                        Timber.d("Checkpoint Result - Blocked: $blocked, Total: $total, Checkpointed: $checkpointed")
                    }
                    cursor.close()
                }
            } catch (e: Exception) {
                Timber.e(e, "WAL checkpoint failed")
            }

            val currentSize = dbPath.length()
            val walFile = File(dbPath.path + "-wal")

            Timber.d("Main DB Size: $currentSize bytes | WAL Size: ${if(walFile.exists()) walFile.length() else 0} bytes")


            if (currentSize <= 4096L && walFile.exists() && walFile.length() > 0L) {
                throw Exception("BACKUP HALTED: Checkpoint failed. Main DB is empty (4KB) but WAL has data. Aborting to prevent data loss.")
            }

            val backupFile = File(context.cacheDir, "temp_backup.db")

            dbPath.inputStream().use { input ->
                backupFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            Timber.d("Database exported successfully: ${backupFile.absolutePath} (${backupFile.length()} bytes)")
            backupFile
        }

    private suspend fun importDatabase(
        database: RoomDatabase,
        backupFile: File,
        context: Context
    ) = withContext(Dispatchers.IO) {
        val dbName = database.openHelper.databaseName ?: throw Exception("Database name is null")

        Timber.d("=== STARTING DATABASE IMPORT ===")

        // 1. Aggressively Close Database
        try {
            if (database.isOpen) {
                database.openHelper.close()
                database.close()
            }
        } catch (e: Exception) {
            Timber.w("Error closing DB (might already be closed): ${e.message}")
        }

        delay(200)

        val dbPath = context.getDatabasePath(dbName)
        val walPath = File("${dbPath.absolutePath}-wal")
        val shmPath = File("${dbPath.absolutePath}-shm")

        val dbDeleted = dbPath.delete()
        val walDeleted = walPath.delete()
        val shmDeleted = shmPath.delete()

        Timber.d("Cleaned old files - DB: $dbDeleted, WAL: $walDeleted, SHM: $shmDeleted")


        backupFile.inputStream().use { input ->
            dbPath.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        if (walPath.exists()) walPath.delete()
        if (shmPath.exists()) shmPath.delete()

        if (!dbPath.exists() || dbPath.length() <= 0L) {
            throw Exception("Restore failed: File copy incomplete")
        }

        Timber.d("=== DATABASE RESTORED: ${dbPath.length()} bytes ===")
    }

    private fun generateBackupFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date())
        return "${config.backupFileName}_$timestamp.db"
    }

    private suspend fun cleanupOldBackups() {
        try {
            val backups = storageProvider.listBackups().getOrNull() ?: return

            if (backups.size > config.maxBackupFiles) {
                val toDelete = backups.sortedByDescending { it.timestamp }
                    .drop(config.maxBackupFiles)

                toDelete.forEach { backup ->
                    storageProvider.deleteBackup(backup.fileId)
                }
                Timber.d("Cleaned up ${toDelete.size} old backups")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup old backups")
        }
    }
}
