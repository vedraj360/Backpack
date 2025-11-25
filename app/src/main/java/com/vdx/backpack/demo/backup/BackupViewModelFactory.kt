package com.vdx.backpack.demo.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vdx.backpack.Backpack

class BackupViewModelFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BackupViewModel::class.java)) {

            return BackupViewModel(
                backpackManager = Backpack.manager,
                driveProvider = Backpack.driveProvider,
                backupScheduler = Backpack.scheduler,
                backupPreferences = Backpack.preferences
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}