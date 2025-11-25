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

    fun backup(context: Context): Flow<BackupResult> = flow {
        emit(BackupResult.InProgress(0))

        try {
            Timber.d("Starting backup...")

            emit(BackupResult.InProgress(10))
            val dbFile = exportDatabase(config.database, context)
            Timber.d("Database exported: ${dbFile.length()} bytes")
            emit(BackupResult.InProgress(30))

            val fileToUpload = if (config.encryptionEnabled) {
                emit(BackupResult.InProgress(40))
                val encryptedFile = File(context.cacheDir, "encrypted_backup.db")
                val result = SecureEncryption.encryptFile(dbFile, encryptedFile)
                if (result.isFailure) {
                    throw result.exceptionOrNull() ?: Exception("Encryption failed")
                }
                Timber.d("Database encrypted: ${encryptedFile.length()} bytes")
                encryptedFile
            } else {
                dbFile
            }
            emit(BackupResult.InProgress(50))

            emit(BackupResult.InProgress(60))
            val uploadResult = storageProvider.uploadBackup(
                fileToUpload,
                generateBackupFileName()
            )
            emit(BackupResult.InProgress(80))

            uploadResult.fold(
                onSuccess = { fileId ->
                    cleanupOldBackups()

                    dbFile.delete()
                    if (config.encryptionEnabled) fileToUpload.delete()

                    val timestamp = System.currentTimeMillis()
                    backupPreferences.saveLastBackupTime(timestamp)

                    emit(BackupResult.InProgress(100))
                    Timber.d("Backup completed: $fileId")
                    emit(BackupResult.Success(fileId, timestamp))
                },
                onFailure = { error ->
                    dbFile.delete()
                    if (config.encryptionEnabled) fileToUpload.delete()

                    Timber.e(error, "Upload failed")
                    emit(BackupResult.Failure(error))
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Backup exception")
            emit(BackupResult.Failure(e))
        }
    }

    fun restore(context: Context, fileId: String): Flow<BackupResult> = flow {
        emit(BackupResult.InProgress(0))

        try {
            Timber.d("Starting restore...")

            emit(BackupResult.InProgress(10))
            val tempFile = File(context.cacheDir, "restore_temp.db")
            val downloadResult = storageProvider.downloadBackup(fileId, tempFile)

            downloadResult.fold(
                onSuccess = { downloadedFile ->
                    Timber.d("Downloaded: ${downloadedFile.length()} bytes")
                    emit(BackupResult.InProgress(40))

                    val fileToRestore = if (config.encryptionEnabled) {
                        emit(BackupResult.InProgress(50))
                        val decrypted = File(context.cacheDir, "decrypted_restore.db")
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
                    emit(BackupResult.InProgress(70))

                    emit(BackupResult.InProgress(80))
                    importDatabase(config.database, fileToRestore, context)

                    fileToRestore.delete()

                    emit(BackupResult.InProgress(100))
                    Timber.d("Restore completed successfully")
                    emit(BackupResult.Success(fileId, System.currentTimeMillis()))
                },
                onFailure = { error ->
                    tempFile.delete()
                    Timber.e(error, "Download failed")
                    emit(BackupResult.Failure(error))
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Restore exception")
            emit(BackupResult.Failure(e))
        }
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
