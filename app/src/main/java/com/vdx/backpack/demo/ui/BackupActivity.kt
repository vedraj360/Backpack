package com.vdx.backpack.demo.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.vdx.backpack.Backpack
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
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.backupView.apply {
                        // UI State Updates
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