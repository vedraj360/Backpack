package com.vdx.backpack.demo.ui

import android.content.DialogInterface
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.vdx.backpack.Backpack
import com.vdx.backpack.R as BackpackR
import com.vdx.backpack.core.BackupConfig
import com.vdx.backpack.demo.backup.BackupViewModel
import com.vdx.backpack.demo.backup.BackupViewModelFactory
import com.vdx.backpack.demo.data.MyDatabase
import com.vdx.backpack.demo.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class BackupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: BackupViewModel

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleSignInResult(this, result.data)
    }

    private val localExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { viewModel.exportLocalBackup(it) }
    }

    private val localImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { selectedUri ->
            AlertDialog.Builder(this)
                .setTitle(BackpackR.string.bp_local_import_confirm_title)
                .setMessage(BackpackR.string.bp_local_import_confirm_msg)
                .setPositiveButton(BackpackR.string.bp_confirm) { _: DialogInterface, _: Int ->
                    viewModel.importLocalBackup(this, selectedUri)
                }
                .setNegativeButton(BackpackR.string.bp_cancel, null)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        try {
            Backpack.manager
        } catch (e: Exception) {
            val database = MyDatabase.Companion.getInstance(this)
            val config = BackupConfig(
                database = database,
                folderName = "My App Backups",
                encryptionEnabled = true,
                autoBackupEnabled = true
            )
            Backpack.initialize(this, config)
        }

        val factory = BackupViewModelFactory()
        viewModel = ViewModelProvider(this, factory)[BackupViewModel::class.java]

        setupToolbar()
        setupBackupView()
        observeState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupBackupView() = with(binding.backupView) {
        setOnSignInClickListener {
            signInLauncher.launch(viewModel.getSignInIntent(this@BackupActivity))
        }

        setOnExportClickListener {
            viewModel.exportBackup(this@BackupActivity)
        }

        setOnImportClickListener {
            viewModel.importBackup(this@BackupActivity)
        }

        setOnUnlinkClickListener {
            viewModel.unlinkAccount(this@BackupActivity)
        }

        setOnAutoBackupToggleListener { isEnabled ->
            viewModel.toggleAutoBackup(this@BackupActivity, isEnabled)
        }

        // Local Device Storage Backup Listeners
        setOnLocalExportClickListener {
            localExportLauncher.launch("app_backup_${System.currentTimeMillis()}.zip")
        }

        setOnLocalImportClickListener {
            localImportLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.backupView.apply {
                        // Cloud UI State Updates
                        if (state.isAuthenticated && state.accountEmail != null) {
                            showAuthenticated(state.accountEmail, state.lastBackupTime)
                        } else {
                            showNotAuthenticated()
                        }

                        if (state.isLoading) {
                            showProgress(state.progress, state.progressMessage)
                        } else {
                            hideProgress()
                        }

                        setAutoBackupEnabled(state.isAutoBackupEnabled)

                        // Local UI State Updates
                        setLastLocalBackupTime(state.lastLocalBackupTimestamp)
                        if (state.isLocalLoading) {
                            showLocalProgress(state.localProgress, state.localProgressMessage)
                        } else {
                            hideLocalProgress()
                        }
                    }

                    // One-shot events
                    state.error?.let {
                        Toast.makeText(this@BackupActivity, it, Toast.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                    state.successMessage?.let {
                        Toast.makeText(this@BackupActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.clearSuccessMessage()
                    }
                }
            }
        }
    }
}