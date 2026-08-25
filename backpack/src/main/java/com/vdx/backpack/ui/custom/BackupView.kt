package com.vdx.backpack.ui.custom

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.vdx.backpack.R
import com.vdx.backpack.databinding.ViewBackupBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewBackupBinding = ViewBackupBinding.inflate(
        LayoutInflater.from(context),
        this,
        true
    )

    init {
        orientation = VERTICAL

        // Read XML attributes if provided
        attrs?.let { attributeSet ->
            context.theme.obtainStyledAttributes(
                attributeSet,
                R.styleable.BackupView,
                0,
                0
            ).apply {
                try {
                    // Apply text attributes
                    getString(R.styleable.BackupView_signInTitle)?.let {
                        binding.tvSignInTitle.text = it
                    }
                    getString(R.styleable.BackupView_signInDescription)?.let {
                        binding.tvSignInDescription.text = it
                    }
                    getString(R.styleable.BackupView_signInButtonText)?.let {
                        binding.btnSignIn.text = it
                    }
                    getString(R.styleable.BackupView_linkedEmail)?.let {
                        binding.tvLinkedEmail.text = context.getString(R.string.bp_linked_to, it)
                    }
                    getString(R.styleable.BackupView_lastBackupTime)?.let {
                        binding.tvLastBackup.text = context.getString(R.string.bp_last_backup, it)
                        binding.tvAutoBackupTime.text = context.getString(R.string.bp_last_backup, it)
                    }
                    getString(R.styleable.BackupView_autoBackupLabel)?.let {
                        binding.tvAutoBackupLabel.text = it
                    }
                    getString(R.styleable.BackupView_unlinkAccountText)?.let {
                        binding.tvUnlinkAccount.text = it
                    }
                    getString(R.styleable.BackupView_exportTitle)?.let {
                        binding.tvExportTitle.text = it
                    }
                    getString(R.styleable.BackupView_exportDescription)?.let {
                        binding.tvExportDescription.text = it
                    }
                    getString(R.styleable.BackupView_exportButtonText)?.let {
                        binding.btnExport.text = it
                    }
                    getString(R.styleable.BackupView_importTitle)?.let {
                        binding.tvImportTitle.text = it
                    }
                    getString(R.styleable.BackupView_importDescription)?.let {
                        binding.tvImportDescription.text = it
                    }
                    getString(R.styleable.BackupView_importButtonText)?.let {
                        binding.btnImport.text = it
                    }
                    getString(R.styleable.BackupView_warningText)?.let {
                        binding.tvWarningMessage.text = it
                    }

                    // Apply visibility attributes
                    binding.cardSignIn.isVisible = getBoolean(
                        R.styleable.BackupView_showSignInCard,
                        true
                    )
                    binding.cardAuthenticated.isVisible = getBoolean(
                        R.styleable.BackupView_showAuthenticatedCard,
                        false
                    )
                    binding.cardActions.isVisible = getBoolean(
                        R.styleable.BackupView_showActionsCard,
                        false
                    )
                    binding.switchAutoBackup.isVisible = getBoolean(
                        R.styleable.BackupView_showAutoBackupToggle,
                        true
                    )
                    binding.tvUnlinkAccount.isVisible = getBoolean(
                        R.styleable.BackupView_showUnlinkAccount,
                        true
                    )

                    val showExportImport = getBoolean(
                        R.styleable.BackupView_showExportImport,
                        true
                    )
                    binding.layoutExport.isVisible = showExportImport
                    binding.btnExport.isVisible = showExportImport
                    binding.layoutImport.isVisible = showExportImport
                    binding.btnImport.isVisible = showExportImport

                    binding.layoutWarning.isVisible = getBoolean(
                        R.styleable.BackupView_showWarning,
                        true
                    )

                    // Apply state attributes
                    binding.switchAutoBackup.isChecked = getBoolean(
                        R.styleable.BackupView_autoBackupEnabled,
                        false
                    )

                    // Apply style attributes
                    val cornerRadius = getDimension(
                        R.styleable.BackupView_cardCornerRadius,
                        16f
                    )
                    binding.cardSignIn.radius = cornerRadius
                    binding.cardAuthenticated.radius = cornerRadius
                    binding.cardActions.radius = cornerRadius

                    val elevation = getDimension(
                        R.styleable.BackupView_cardElevation,
                        2f
                    )
                    binding.cardSignIn.cardElevation = elevation
                    binding.cardAuthenticated.cardElevation = elevation
                    binding.cardActions.cardElevation = elevation

                    getColor(R.styleable.BackupView_iconTint, -1).let { color ->
                        if (color != -1) {
                            binding.ivCloudSignIn.setColorFilter(color)
                            binding.ivCloudAuth.setColorFilter(color)
                            binding.ivExport.setColorFilter(color)
                            binding.ivImport.setColorFilter(color)
                        }
                    }

                    getColor(R.styleable.BackupView_primaryButtonColor, -1).let { color ->
                        if (color != -1) {
                            binding.btnSignIn.backgroundTintList = ColorStateList.valueOf(color)
                        }
                    }

                } finally {
                    recycle()
                }
            }
        }
    }

    // ==================== Public View Access ====================

    val signInCard: MaterialCardView get() = binding.cardSignIn
    val authenticatedCard: MaterialCardView get() = binding.cardAuthenticated
    val actionsCard: MaterialCardView get() = binding.cardActions

    val signInButton get() = binding.btnSignIn
    val exportButton get() = binding.btnExport
    val importButton get() = binding.btnImport

    val linkedEmailText get() = binding.tvLinkedEmail
    val lastBackupText get() = binding.tvLastBackup
    val autoBackupTimeText get() = binding.tvAutoBackupTime
    val unlinkAccountText get() = binding.tvUnlinkAccount
    val progressText get() = binding.tvProgress

    val autoBackupSwitch: SwitchMaterial get() = binding.switchAutoBackup
    val progressBar get() = binding.progressBar

    val exportLayout get() = binding.layoutExport
    val importLayout get() = binding.layoutImport

    // ==================== Convenience Methods ====================

    fun showNotAuthenticated() {
        binding.cardSignIn.isVisible = true
        binding.cardAuthenticated.isVisible = false
        binding.cardActions.isVisible = false
    }

    fun showAuthenticated(email: String? = null, lastBackupTime: String? = null) {
        binding.cardSignIn.isVisible = false
        binding.cardAuthenticated.isVisible = true
        binding.cardActions.isVisible = true

        email?.let { binding.tvLinkedEmail.text = context.getString(R.string.bp_linked_to_newline, it) }
        lastBackupTime?.let {
            binding.tvLastBackup.text = context.getString(R.string.bp_last_backup, it)
            binding.tvAutoBackupTime.text = context.getString(R.string.bp_last_backup, it)
        } ?: run {
            binding.tvLastBackup.text = context.getString(R.string.bp_last_backup_never)
            binding.tvAutoBackupTime.text = context.getString(R.string.bp_last_backup_never)
        }
    }

    fun showProgress(progress: Int, message: String? = null) {
        binding.progressBar.isVisible = true
        binding.progressBar.progress = progress
        binding.tvProgress.isVisible = true
        binding.tvProgress.text = message ?: context.getString(R.string.bp_backing_up)
        setButtonsEnabled(false)
    }

    fun hideProgress() {
        binding.progressBar.isVisible = false
        binding.tvProgress.isVisible = false
        setButtonsEnabled(true)
    }

    fun setButtonsEnabled(enabled: Boolean) {
        binding.btnSignIn.isEnabled = enabled
        binding.btnExport.isEnabled = enabled
        binding.btnImport.isEnabled = enabled
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        binding.switchAutoBackup.isChecked = enabled
    }

    fun updateLastBackupTime(timestamp: Long) {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        val formattedTime = sdf.format(Date(timestamp))
        binding.tvLastBackup.text = context.getString(R.string.bp_last_backup, formattedTime)
        binding.tvAutoBackupTime.text = context.getString(R.string.bp_last_backup, formattedTime)
    }

    fun setLinkedEmail(email: String) {
        binding.tvLinkedEmail.text = context.getString(R.string.bp_linked_to, email)
    }

    fun setLastBackupText(text: String) {
        binding.tvLastBackup.text = text
        binding.tvAutoBackupTime.text = text
    }

    // ==================== Local Backup Helpers ====================

    fun showLocalProgress(progress: Int, message: String? = null) {
        binding.localProgressBar.isVisible = true
        binding.localProgressBar.progress = progress
        binding.tvLocalProgress.isVisible = true
        binding.tvLocalProgress.text = message ?: context.getString(R.string.bp_backing_up)
        binding.btnLocalExport.isEnabled = false
        binding.btnLocalImport.isEnabled = false
    }

    fun hideLocalProgress() {
        binding.localProgressBar.isVisible = false
        binding.tvLocalProgress.isVisible = false
        binding.btnLocalExport.isEnabled = true
        binding.btnLocalImport.isEnabled = true
    }

    fun setLastLocalBackupTime(timestamp: Long) {
        val formatted = if (timestamp > 0) {
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        } else {
            context.getString(R.string.bp_local_last_backup_never)
        }
        binding.tvLocalLastBackup.text = context.getString(R.string.bp_local_last_backup_prefix, formatted)
    }

    fun setOnLocalExportClickListener(listener: () -> Unit) {
        binding.btnLocalExport.setOnClickListener { listener() }
    }

    fun setOnLocalImportClickListener(listener: () -> Unit) {
        binding.btnLocalImport.setOnClickListener { listener() }
    }

    fun showLocalBackupCard(show: Boolean) {
        binding.cardLocalBackup.isVisible = show
    }

    // ==================== Listener Setters ====================

    fun setOnSignInClickListener(listener: () -> Unit) {
        binding.btnSignIn.setOnClickListener { listener() }
    }

    fun setOnExportClickListener(listener: () -> Unit) {
        binding.btnExport.setOnClickListener { listener() }
        binding.layoutExport.setOnClickListener { listener() }
    }

    fun setOnImportClickListener(listener: () -> Unit) {
        binding.btnImport.setOnClickListener { listener() }
        binding.layoutImport.setOnClickListener { listener() }
    }

    fun setOnUnlinkClickListener(listener: () -> Unit) {
        binding.tvUnlinkAccount.setOnClickListener { listener() }
    }

    fun setOnAutoBackupToggleListener(listener: (Boolean) -> Unit) {
        binding.switchAutoBackup.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                listener(isChecked)
            }
        }
    }

    // ==================== Visibility Controls ====================

    fun showSignInCard(show: Boolean) {
        binding.cardSignIn.isVisible = show
    }

    fun showAuthenticatedCard(show: Boolean) {
        binding.cardAuthenticated.isVisible = show
    }

    fun showActionsCard(show: Boolean) {
        binding.cardActions.isVisible = show
    }

    fun showAutoBackupToggle(show: Boolean) {
        binding.switchAutoBackup.isVisible = show
    }

    fun showUnlinkAccount(show: Boolean) {
        binding.tvUnlinkAccount.isVisible = show
    }
}
