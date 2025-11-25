package com.vdx.backpack.demo.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vdx.backpack.core.BackpackManager
import com.vdx.backpack.core.BackupResult
import com.vdx.backpack.demo.utils.AppRestartHelper
import com.vdx.backpack.storage.BackupPreferences
import com.vdx.backpack.storage.GoogleDriveProvider
import com.vdx.backpack.util.DateTimeUtils
import com.vdx.backpack.worker.BackupScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.FileNotFoundException

data class BackupUiState(
    val isAuthenticated: Boolean = false,
    val accountEmail: String? = null,
    val lastBackupTime: String? = null,
    val isAutoBackupEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val progressMessage: String = "",
    val error: String? = null,
    val successMessage: String? = null
)


class BackupViewModel(
    private val backpackManager: BackpackManager,
    private val driveProvider: GoogleDriveProvider,
    private val backupScheduler: BackupScheduler,
    private val backupPreferences: BackupPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    init {
        checkAuthenticationStatus()
        observePreferences()
    }

    private fun checkAuthenticationStatus() {
        val isAuthenticated = driveProvider.isAuthenticated()
        val email = driveProvider.getAuthenticatedEmail()

        _uiState.update {
            it.copy(isAuthenticated = isAuthenticated, accountEmail = email)
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            backupPreferences.lastBackupTime.collectLatest { timestamp ->
                val formattedTime =
                    if (timestamp > 0) DateTimeUtils.formatTimestamp(timestamp) else "Never"
                _uiState.update { it.copy(lastBackupTime = formattedTime) }
            }
        }

        viewModelScope.launch {
            backupPreferences.isAutoBackupEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(isAutoBackupEnabled = enabled) }
            }
        }
    }

    // ============================================================================================
    // Authentication
    // ============================================================================================

    fun getSignInIntent(activity: Activity): Intent {
        return driveProvider.getSignInIntent(activity)
    }

    fun handleSignInResult(activity: Activity, data: Intent?) {
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            // This calls the suspend function in your library's GoogleDriveProvider
            val result = driveProvider.handleSignInResult(activity, data)

            result.onSuccess { email ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAuthenticated = true,
                        accountEmail = email,
                        successMessage = "Signed in successfully as $email"
                    )
                }
                Timber.d("Sign-in success: $email")
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAuthenticated = false,
                        error = if (error.message?.contains(
                                "cancel",
                                true
                            ) == true
                        ) null else "Sign in failed: ${error.localizedMessage}"
                    )
                }
                Timber.e(error, "Sign-in failure")
            }
        }
    }

    fun unlinkAccount(activity: Activity) {
        driveProvider.signOut(activity)

        backupPreferences.clearLastBackupTime()
        backupPreferences.setAutoBackupEnabled(false)
        backupScheduler.cancelAutoBackup(activity)

        _uiState.update {
            it.copy(
                isAuthenticated = false,
                accountEmail = null,
                isAutoBackupEnabled = false,
                successMessage = "Account unlinked successfully"
            )
        }
    }

    // ============================================================================================
    // Backup & Restore
    // ============================================================================================

    fun exportBackup(context: Context) {
        if (!driveProvider.isAuthenticated()) {
            _uiState.update { it.copy(error = "Please sign in to Google Drive first") }
            return
        }

        viewModelScope.launch {
            backpackManager.backup(context).collect { result ->
                handleBackupResult(result)
            }
        }
    }

    private fun handleBackupResult(result: BackupResult) {
        when (result) {
            is BackupResult.InProgress -> {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        progress = result.progress,
                        progressMessage = getProgressMessage(result.progress, isRestore = false),
                        error = null
                    )
                }
            }

            is BackupResult.Success -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        progress = 100,
                        successMessage = "Backup completed successfully",
                        lastBackupTime = DateTimeUtils.formatTimestamp(result.timestamp)
                    )
                }
            }

            is BackupResult.Failure -> {
                Timber.e("Error during backup: ${result.error.localizedMessage}")
                var errorMessage = "Backup failed: ${result.error.localizedMessage}"
                if (result.error is FileNotFoundException) {
                    errorMessage = "Backup failed: Database file not found. Try adding some data first."
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = errorMessage
                    )
                }
            }
        }
    }

    fun importBackup(context: Context) {
        if (!driveProvider.isAuthenticated()) {
            _uiState.update { it.copy(error = "Please sign in to Google Drive first") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, progressMessage = "Finding backups...") }

            val listResult = backpackManager.listAvailableBackups()

            listResult.onSuccess { backups ->
                if (backups.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "No backups found in Drive."
                        )
                    }
                    return@launch
                }

                val latestBackup = backups.first()

                backpackManager.restore(context, latestBackup.fileId).collect { result ->
                    handleRestoreResult(result, context)
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to list backups: ${error.localizedMessage}"
                    )
                }
            }
        }
    }

    private suspend fun handleRestoreResult(result: BackupResult, context: Context) {
        when (result) {
            is BackupResult.InProgress -> {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        progress = result.progress,
                        progressMessage = getProgressMessage(result.progress, isRestore = true),
                        error = null
                    )
                }
            }

            is BackupResult.Success -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        progress = 100,
                        successMessage = "Restore successful! Restarting app..."
                    )
                }
                delay(1500)
                AppRestartHelper.triggerRestart(context)
            }

            is BackupResult.Failure -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Restore failed: ${result.error.localizedMessage}"
                    )
                }
            }
        }
    }

    // ============================================================================================
    // Settings & Helpers
    // ============================================================================================

    fun toggleAutoBackup(activity: Activity, enabled: Boolean) {
        viewModelScope.launch {
            backupPreferences.setAutoBackupEnabled(enabled)
            if (enabled) {
                backupScheduler.scheduleAutoBackup(activity, 24)
                _uiState.update { it.copy(successMessage = "Auto-backup enabled (Daily)") }
            } else {
                backupScheduler.cancelAutoBackup(activity)
                _uiState.update { it.copy(successMessage = "Auto-backup disabled") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    private fun getProgressMessage(progress: Int, isRestore: Boolean): String {
        return if (isRestore) {
            when {
                progress < 30 -> "Downloading..."
                progress < 60 -> "Decrypting..."
                progress < 90 -> "Restoring Database..."
                else -> "Finalizing..."
            }
        } else {
            when {
                progress < 30 -> "Exporting Database..."
                progress < 60 -> "Encrypting..."
                progress < 90 -> "Uploading..."
                else -> "Finishing up..."
            }
        }
    }
}