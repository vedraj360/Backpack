package com.vdx.backpack.vault.manager

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.vdx.backpack.vault.VaultKitConfig
import com.vdx.backpack.vault.model.ConflictPolicy
import com.vdx.backpack.vault.model.LocalBackupResult
import com.vdx.backpack.vault.prefs.LocalBackupPreferences
import com.vdx.backpack.vault.zip.BackupZipper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

class LocalBackupManager(
    private val context: Context,
    private val config: VaultKitConfig,
    private val preferences: LocalBackupPreferences
) {

    private var pendingImportUri: Uri? = null

    /**
     * Gets the last SAF Uri used for export, or null if none saved.
     */
    fun getLastSafUri(): Uri? {
        val uriStr = preferences.getLastSafUri()
        return if (uriStr.isNotEmpty()) {
            try {
                Uri.parse(uriStr)
            } catch (_: Exception) {
                null
            }
        } else null
    }

    /**
     * Generates a suggested backup filename based on the configured prefix and current SQLite version.
     */
    fun getDefaultBackupFileName(): String {
        var dbVersion = 1
        try {
            val dbFile = context.getDatabasePath(config.dbName)
            if (dbFile.exists()) {
                val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                val cursor = db.rawQuery("PRAGMA user_version", null)
                cursor.use {
                    if (it.moveToFirst()) {
                        dbVersion = it.getInt(0)
                    }
                }
                db.close()
            }
        } catch (e: Exception) {
            Timber.tag("LocalBackupManager").w(e, "Could not read user_version for filename")
        }

        return BackupZipper.generateBackupFileName(config.backupFilePrefix, dbVersion)
    }

    /**
     * Exports database and all attachments to the target [outputUri].
     */
    fun export(outputUri: Uri): Flow<LocalBackupResult> {
        val appVersion = try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: ""
        } catch (_: Exception) {
            ""
        }

        return BackupZipper.export(context, config, outputUri, appVersion)
            .onEach { result ->
                if (result is LocalBackupResult.Success) {
                    preferences.setLastBackupTimestamp(result.timestamp)
                    preferences.setLastSafUri(outputUri.toString())
                    preferences.setLastBackupPath(outputUri.toString())
                }
            }
    }

    /**
     * Imports from the provided backup [inputUri].
     */
    fun import(inputUri: Uri): Flow<LocalBackupResult> {
        pendingImportUri = inputUri
        return BackupZipper.import(context, config, inputUri)
            .onEach { result ->
                if (result is LocalBackupResult.ConflictDetected) {
                    pendingImportUri = result.pendingImportUri
                }
            }
    }

    /**
     * Resolves pending conflict after [LocalBackupResult.ConflictDetected].
     */
    fun resolveConflict(overwrite: Boolean): Flow<LocalBackupResult> {
        val uri = pendingImportUri
        if (uri == null) {
            return flow {
                emit(LocalBackupResult.Failure(IllegalStateException(context.getString(com.vdx.backpack.R.string.vk_error_no_pending_conflict))))
            }
        }

        val policy = if (overwrite) ConflictPolicy.OVERWRITE else ConflictPolicy.SKIP
        return BackupZipper.import(context, config, uri, policy)
            .onEach {
                if (it is LocalBackupResult.Success || it is LocalBackupResult.Failure) {
                    pendingImportUri = null
                }
            }
    }
}
