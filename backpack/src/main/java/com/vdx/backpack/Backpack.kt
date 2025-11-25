package com.vdx.backpack

import android.annotation.SuppressLint
import android.content.Context
import com.vdx.backpack.core.BackpackManager
import com.vdx.backpack.core.BackupConfig
import com.vdx.backpack.storage.BackupPreferences
import com.vdx.backpack.storage.GoogleDriveProvider
import com.vdx.backpack.worker.BackupScheduler

object Backpack {
    private var _manager: BackpackManager? = null
    private var _preferences: BackupPreferences? = null

    // Suppress warning because we GUARANTEE applicationContext is used
    @SuppressLint("StaticFieldLeak")
    private var _driveProvider: GoogleDriveProvider? = null

    val scheduler = BackupScheduler()

    val manager: BackpackManager
        get() = _manager ?: throwError()


    val preferences: BackupPreferences
        get() = _preferences ?: throwError()


    val driveProvider: GoogleDriveProvider
        get() = _driveProvider ?: throwError()

    fun initialize(context: Context, config: BackupConfig) {
        val appContext = context.applicationContext

        _preferences = BackupPreferences(appContext)
        _driveProvider = GoogleDriveProvider(appContext, config.folderName)

        _manager = BackpackManager(config, _driveProvider!!, _preferences!!)
    }

    private fun throwError(): Nothing =
        throw IllegalStateException("Backpack is not initialized. Call Backpack.initialize(context, config) in your Application class.")
}