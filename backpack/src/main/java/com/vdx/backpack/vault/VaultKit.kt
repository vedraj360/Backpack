package com.vdx.backpack.vault

import android.content.Context
import com.vdx.backpack.vault.manager.LocalBackupManager
import com.vdx.backpack.vault.prefs.LocalBackupPreferences

object VaultKit {

    @Volatile
    private var _manager: LocalBackupManager? = null

    @Volatile
    private var _preferences: LocalBackupPreferences? = null

    @Volatile
    private var _config: VaultKitConfig? = null

    /**
     * Initializes VaultKit singleton.
     */
    fun initialize(context: Context, config: VaultKitConfig) {
        if (_manager == null) {
            synchronized(this) {
                if (_manager == null) {
                    val appContext = context.applicationContext
                    val prefs = LocalBackupPreferences(appContext)
                    _preferences = prefs
                    _config = config
                    _manager = LocalBackupManager(appContext, config, prefs)
                }
            }
        }
    }

    val manager: LocalBackupManager
        get() = _manager ?: error("VaultKit is not initialized. Call VaultKit.initialize(context, config) first.")

    val preferences: LocalBackupPreferences
        get() = _preferences ?: error("VaultKit is not initialized. Call VaultKit.initialize(context, config) first.")

    val config: VaultKitConfig
        get() = _config ?: error("VaultKit is not initialized. Call VaultKit.initialize(context, config) first.")
}
