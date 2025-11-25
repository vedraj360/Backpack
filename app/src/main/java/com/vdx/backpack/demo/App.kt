package com.vdx.backpack.demo

import android.app.Application
import com.vdx.backpack.Backpack
import com.vdx.backpack.core.BackupConfig
import com.vdx.backpack.demo.data.MyDatabase
import timber.log.Timber

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.Forest.plant(Timber.DebugTree())
        }


        val config = BackupConfig(
            database = MyDatabase.getInstance(this),
            folderName = "My Backups"
        )

        Backpack.initialize(this, config)
    }
}