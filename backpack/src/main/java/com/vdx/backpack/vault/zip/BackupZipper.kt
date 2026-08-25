package com.vdx.backpack.vault.zip

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.vdx.backpack.R
import com.vdx.backpack.vault.VaultKitConfig
import com.vdx.backpack.vault.model.BackupMeta
import com.vdx.backpack.vault.model.ConflictPolicy
import com.vdx.backpack.vault.model.LocalBackupResult
import com.vdx.backpack.vault.remap.AttachmentRemapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupZipper {

    private const val BUFFER_SIZE = 8192
    private const val DIR_DB = "db"
    private const val DIR_ATTACHMENTS = "attachments"
    private const val META_FILE = "meta.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Exports DB and attachments to the provided outputUri.
     */
    fun export(
        context: Context,
        config: VaultKitConfig,
        outputUri: Uri,
        appVersion: String = ""
    ): Flow<LocalBackupResult> = flow {
        val timestamp = System.currentTimeMillis()
        val stagingDir = File(context.cacheDir, "vaultkit_export_stage_$timestamp")

        try {
            emit(LocalBackupResult.InProgress(5, context.getString(R.string.vk_progress_preparing_db)))
            stagingDir.mkdirs()

            val dbFile = context.getDatabasePath(config.dbName)
            if (!dbFile.exists()) {
                throw FileNotFoundException(context.getString(R.string.vk_error_db_not_found, dbFile.absolutePath))
            }

            // 1. Checkpoint WAL and get db version
            var dbVersion = 1
            try {
                config.database?.openHelper?.writableDatabase?.query("PRAGMA wal_checkpoint(FULL)")?.close()
                val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
                val cursor = db.rawQuery("PRAGMA wal_checkpoint(FULL)", null)
                cursor.use { if (it.moveToFirst()) it.getInt(0) }

                val versionCursor = db.rawQuery("PRAGMA user_version", null)
                versionCursor.use {
                    if (it.moveToFirst()) {
                        dbVersion = it.getInt(0)
                    }
                }
                db.close()
            } catch (e: Exception) {
                Timber.tag("BackupZipper").w(e, "Could not checkpoint WAL; continuing copy")
            }

            // 2. Copy DB files to staging/db/
            emit(LocalBackupResult.InProgress(15, context.getString(R.string.vk_progress_exporting_db)))
            val stageDbDir = File(stagingDir, DIR_DB).apply { mkdirs() }
            val stageDbFile = File(stageDbDir, config.dbName)
            copyFile(dbFile, stageDbFile)

            val walFile = File(dbFile.parentFile, "${config.dbName}-wal")
            if (walFile.exists()) {
                copyFile(walFile, File(stageDbDir, "${config.dbName}-wal"))
            }

            val shmFile = File(dbFile.parentFile, "${config.dbName}-shm")
            if (shmFile.exists()) {
                copyFile(shmFile, File(stageDbDir, "${config.dbName}-shm"))
            }

            // 3. Copy attachments to staging/attachments/
            emit(LocalBackupResult.InProgress(25, context.getString(R.string.vk_progress_exporting_attachments)))
            val sourceAttachmentsDir = File(context.filesDir, DIR_ATTACHMENTS)
            val stageAttachmentsDir = File(stagingDir, DIR_ATTACHMENTS).apply { mkdirs() }

            var attachmentCount = 0
            var missingCount = 0

            if (sourceAttachmentsDir.exists() && sourceAttachmentsDir.isDirectory) {
                val files = sourceAttachmentsDir.listFiles() ?: emptyArray()
                val total = files.size

                files.forEachIndexed { index, file ->
                    if (file.isFile && file.exists()) {
                        copyFile(file, File(stageAttachmentsDir, file.name))
                        attachmentCount++
                    } else {
                        missingCount++
                    }

                    if (total > 0) {
                        val progress = 25 + ((index + 1) * 40 / total)
                        emit(
                            LocalBackupResult.InProgress(
                                progress,
                                context.getString(R.string.vk_progress_exporting_attachments_count, index + 1, total)
                            )
                        )
                    }
                }
            }

            // 4. Write meta.json
            val meta = BackupMeta(
                dbVersion = dbVersion,
                timestamp = timestamp,
                appVersion = appVersion,
                attachmentCount = attachmentCount,
                missingCount = missingCount
            )
            val metaFile = File(stagingDir, META_FILE)
            metaFile.writeText(json.encodeToString(meta))

            // 5. Zip staging directory to outputUri
            emit(LocalBackupResult.InProgress(70, context.getString(R.string.vk_progress_creating_zip)))
            val outputStream = context.contentResolver.openOutputStream(outputUri)
                ?: throw IOException(context.getString(R.string.vk_error_open_output_stream))

            outputStream.use { os ->
                ZipOutputStream(BufferedOutputStream(os)).use { zos ->
                    zipDirectory(stagingDir, stagingDir, zos)
                }
            }

            emit(LocalBackupResult.InProgress(100, context.getString(R.string.vk_progress_backup_completed)))
            emit(LocalBackupResult.Success(timestamp, outputUri.toString()))
        } catch (e: Throwable) {
            Timber.tag("BackupZipper").e(e, "Export failed")
            emit(LocalBackupResult.Failure(e))
        } finally {
            deleteRecursively(stagingDir)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Imports from the provided inputUri.
     */
    fun import(
        context: Context,
        config: VaultKitConfig,
        inputUri: Uri,
        conflictPolicy: ConflictPolicy = config.conflictPolicy
    ): Flow<LocalBackupResult> = flow {
        val timestamp = System.currentTimeMillis()
        val stagingDir = File(context.cacheDir, "vaultkit_import_stage_$timestamp")
        val currentDbFile = context.getDatabasePath(config.dbName)

        try {
            emit(LocalBackupResult.InProgress(5, context.getString(R.string.vk_progress_reading_backup)))
            stagingDir.mkdirs()

            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: throw IOException(context.getString(R.string.vk_error_open_input_stream))

            // 1. Check if input is a ZIP archive or a raw SQLite database
            emit(LocalBackupResult.InProgress(15, context.getString(R.string.vk_progress_extracting_files)))
            val isZip = isZipStream(context, inputUri)
            val stagedDbDir = File(stagingDir, DIR_DB).apply { mkdirs() }
            val stagedDbFile = File(stagedDbDir, config.dbName)

            if (isZip) {
                inputStream.use { `is` ->
                    ZipInputStream(BufferedInputStream(`is`)).use { zis ->
                        unzipStream(zis, stagingDir)
                    }
                }
            } else {
                // Legacy V1 Backup (Raw SQLite database file)
                Timber.tag("BackupZipper").i("Detected legacy raw SQLite database backup")
                inputStream.use { `is` ->
                    stagedDbFile.outputStream().use { os ->
                        `is`.copyTo(os, BUFFER_SIZE)
                    }
                }
            }

            // 2. Validate extracted content & schema version compatibility
            emit(LocalBackupResult.InProgress(30, context.getString(R.string.vk_progress_validating_integrity)))
            if (!stagedDbFile.exists() || stagedDbFile.length() <= 0L) {
                throw IllegalStateException(context.getString(R.string.vk_error_invalid_archive, config.dbName))
            }

            // Inspect staged DB version
            var stagedDbVersion = 1
            try {
                val db = SQLiteDatabase.openDatabase(stagedDbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                val versionCursor = db.rawQuery("PRAGMA user_version", null)
                versionCursor.use {
                    if (it.moveToFirst()) {
                        stagedDbVersion = it.getInt(0)
                    }
                }
                db.close()
            } catch (e: Exception) {
                Timber.tag("BackupZipper").w(e, "Could not read staged DB version")
            }

            // Check current app database version
            var currentAppDbVersion = 1
            if (currentDbFile.exists()) {
                try {
                    val db = SQLiteDatabase.openDatabase(currentDbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                    val versionCursor = db.rawQuery("PRAGMA user_version", null)
                    versionCursor.use {
                        if (it.moveToFirst()) {
                            currentAppDbVersion = it.getInt(0)
                        }
                    }
                    db.close()
                } catch (_: Exception) {}
            }

            if (stagedDbVersion > currentAppDbVersion) {
                throw IllegalStateException(context.getString(R.string.vk_error_incompatible_db_version, stagedDbVersion))
            }

            val stagedMetaFile = File(stagingDir, META_FILE)
            if (stagedMetaFile.exists()) {
                try {
                    val meta = json.decodeFromString<BackupMeta>(stagedMetaFile.readText())
                    Timber.tag("BackupZipper").i("Restoring backup: DB v${meta.dbVersion}, app v${meta.appVersion}")
                } catch (e: Exception) {
                    Timber.tag("BackupZipper").w(e, "Could not parse meta.json; continuing restore")
                }
            }

            // 3. Remap attachment paths in SQLite DB file directly
            emit(LocalBackupResult.InProgress(45, context.getString(R.string.vk_progress_remapping_paths)))
            val destAttachmentsDir = File(context.filesDir, DIR_ATTACHMENTS).apply { mkdirs() }
            val stagedAttachmentsDir = File(stagingDir, DIR_ATTACHMENTS)
            val backedUpFileNames = if (stagedAttachmentsDir.exists() && stagedAttachmentsDir.isDirectory) {
                stagedAttachmentsDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
            } else {
                emptySet()
            }

            AttachmentRemapper.remap(
                stagedDbFile,
                destAttachmentsDir,
                config.targetAttachmentColumns,
                backedUpFileNames
            )

            // 4. Check for attachment conflicts
            if (stagedAttachmentsDir.exists() && stagedAttachmentsDir.isDirectory) {
                val stagedFiles = stagedAttachmentsDir.listFiles() ?: emptyArray()
                val conflictingFiles = mutableListOf<String>()

                for (file in stagedFiles) {
                    val existingFile = File(destAttachmentsDir, file.name)
                    if (existingFile.exists()) {
                        conflictingFiles.add(file.name)
                    }
                }

                if (conflictingFiles.isNotEmpty() && conflictPolicy == ConflictPolicy.ASK_USER) {
                    // Emit conflict and pause
                    emit(LocalBackupResult.ConflictDetected(conflictingFiles, inputUri))
                    return@flow
                }

                // 5. Copy attachments per conflict policy
                emit(LocalBackupResult.InProgress(60, context.getString(R.string.vk_progress_restoring_attachments)))
                stagedFiles.forEachIndexed { index, file ->
                    val destFile = File(destAttachmentsDir, file.name)
                    if (!destFile.exists() || conflictPolicy == ConflictPolicy.OVERWRITE) {
                        copyFile(file, destFile)
                    }
                    if (stagedFiles.isNotEmpty()) {
                        val progress = 60 + ((index + 1) * 20 / stagedFiles.size)
                        emit(
                            LocalBackupResult.InProgress(
                                progress,
                                context.getString(R.string.vk_progress_restoring_attachments_count, index + 1, stagedFiles.size)
                            )
                        )
                    }
                }
            }

            // 6. Atomic swap of database
            emit(LocalBackupResult.InProgress(85, context.getString(R.string.vk_progress_restoring_db)))
            val targetDbFile = context.getDatabasePath(config.dbName)
            targetDbFile.parentFile?.mkdirs()

            // Backup current DB to .bak in case of unexpected failure
            val backupTargetFile = File(targetDbFile.parentFile, "${config.dbName}.bak")
            if (targetDbFile.exists()) {
                copyFile(targetDbFile, backupTargetFile)
            }

            try {
                // Delete existing db and wal/shm
                targetDbFile.delete()
                File(targetDbFile.parentFile, "${config.dbName}-wal").delete()
                File(targetDbFile.parentFile, "${config.dbName}-shm").delete()

                // Copy staged DB files over
                copyFile(stagedDbFile, targetDbFile)

                val stagedWal = File(stagedDbDir, "${config.dbName}-wal")
                if (stagedWal.exists()) {
                    copyFile(stagedWal, File(targetDbFile.parentFile, "${config.dbName}-wal"))
                }

                val stagedShm = File(stagedDbDir, "${config.dbName}-shm")
                if (stagedShm.exists()) {
                    copyFile(stagedShm, File(targetDbFile.parentFile, "${config.dbName}-shm"))
                }

                // Restore successful: remove .bak
                backupTargetFile.delete()
            } catch (e: Exception) {
                // Rollback if needed
                if (backupTargetFile.exists()) {
                    copyFile(backupTargetFile, targetDbFile)
                    backupTargetFile.delete()
                }
                throw e
            }

            emit(LocalBackupResult.InProgress(100, context.getString(R.string.vk_progress_restore_complete)))
            emit(LocalBackupResult.Success(timestamp, inputUri.toString()))
        } catch (e: Throwable) {
            Timber.tag("BackupZipper").e(e, "Import failed")
            emit(LocalBackupResult.Failure(e))
        } finally {
            deleteRecursively(stagingDir)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Generates a clean timestamped backup filename.
     */
    fun generateBackupFileName(prefix: String, dbVersion: Int): String {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "${prefix}_${dateStr}.zip"
    }

    private fun zipDirectory(rootDir: File, sourceDir: File, zos: ZipOutputStream) {
        val files = sourceDir.listFiles() ?: return
        val buffer = ByteArray(BUFFER_SIZE)

        for (file in files) {
            if (file.isDirectory) {
                zipDirectory(rootDir, file, zos)
            } else {
                val relativePath = rootDir.toURI().relativize(file.toURI()).path
                val entry = ZipEntry(relativePath)
                entry.time = file.lastModified()
                zos.putNextEntry(entry)

                BufferedInputStream(FileInputStream(file)).use { bis ->
                    var count: Int
                    while (bis.read(buffer, 0, BUFFER_SIZE).also { count = it } != -1) {
                        zos.write(buffer, 0, count)
                    }
                }
                zos.closeEntry()
            }
        }
    }

    private fun unzipStream(zis: ZipInputStream, destDir: File) {
        val buffer = ByteArray(BUFFER_SIZE)
        var entry: ZipEntry? = zis.nextEntry

        while (entry != null) {
            val newFile = safeNewFile(destDir, entry.name)
            if (entry.isDirectory) {
                newFile.mkdirs()
            } else {
                newFile.parentFile?.mkdirs()
                FileOutputStream(newFile).use { fos ->
                    var count: Int
                    while (zis.read(buffer, 0, BUFFER_SIZE).also { count = it } != -1) {
                        fos.write(buffer, 0, count)
                    }
                }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }

    /**
     * Prevents Zip Slip vulnerability.
     */
    private fun safeNewFile(destinationDir: File, zipEntryName: String): File {
        val destFile = File(destinationDir, zipEntryName)
        val destDirPath = destinationDir.canonicalPath
        val destFilePath = destFile.canonicalPath

        if (!destFilePath.startsWith(destDirPath + File.separator) && destFilePath != destDirPath) {
            throw SecurityException("Zip entry is outside target directory: $zipEntryName")
        }
        return destFile
    }

    private fun copyFile(src: File, dest: File) {
        src.inputStream().use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output, BUFFER_SIZE)
            }
        }
    }

    private fun isZipStream(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(4)
                val read = input.read(header)
                read == 4 &&
                        header[0] == 0x50.toByte() &&
                        header[1] == 0x4B.toByte() &&
                        header[2] == 0x03.toByte() &&
                        header[3] == 0x04.toByte()
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun isZipFile(file: File): Boolean {
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(4)
                val read = input.read(header)
                read == 4 &&
                        header[0] == 0x50.toByte() &&
                        header[1] == 0x4B.toByte() &&
                        header[2] == 0x03.toByte() &&
                        header[3] == 0x04.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }
}
