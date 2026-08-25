package com.vdx.backpack.ui.custom

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import com.vdx.backpack.R
import com.vdx.backpack.databinding.ViewLocalBackupBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated Custom View for Device Storage (Local / SAF) Backup & Restore.
 */
class LocalBackupView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewLocalBackupBinding = ViewLocalBackupBinding.inflate(
        LayoutInflater.from(context),
        this,
        true
    )

    init {
        attrs?.let { attributeSet ->
            context.obtainStyledAttributes(attributeSet, R.styleable.LocalBackupView).apply {
                try {
                    getString(R.styleable.LocalBackupView_localTitle)?.let {
                        binding.tvLocalTitle.text = it
                    }
                    getString(R.styleable.LocalBackupView_localDescription)?.let {
                        binding.tvLocalDescription.text = it
                    }
                    getString(R.styleable.LocalBackupView_localExportButtonText)?.let {
                        binding.btnExportLocal.text = it
                    }
                    getString(R.styleable.LocalBackupView_localImportButtonText)?.let {
                        binding.btnImportLocal.text = it
                    }

                    val cornerRadius = getDimension(
                        R.styleable.LocalBackupView_localCardCornerRadius,
                        16f
                    )
                    binding.cardLocalBackup.radius = cornerRadius

                    val elevation = getDimension(
                        R.styleable.LocalBackupView_localCardElevation,
                        2f
                    )
                    binding.cardLocalBackup.cardElevation = elevation

                    getColor(R.styleable.LocalBackupView_localIconTint, -1).let { color ->
                        if (color != -1) {
                            binding.ivLocalBackupIcon.setColorFilter(color)
                        }
                    }
                } finally {
                    recycle()
                }
            }
        }
    }

    // ==================== Public Methods ====================

    val card: MaterialCardView get() = binding.cardLocalBackup
    val exportButton: Button get() = binding.btnExportLocal
    val importButton: Button get() = binding.btnImportLocal
    val progressBar: ProgressBar get() = binding.pbLocal
    val progressText: TextView get() = binding.tvLocalProgressStatus
    val lastBackupText: TextView get() = binding.tvLocalLastBackupTime

    fun showProgress(progress: Int, message: String? = null) {
        binding.pbLocal.isVisible = true
        binding.pbLocal.progress = progress
        binding.tvLocalProgressStatus.isVisible = true
        binding.tvLocalProgressStatus.text = message ?: context.getString(R.string.bp_backing_up)
        setButtonsEnabled(false)
    }

    fun hideProgress() {
        binding.pbLocal.isVisible = false
        binding.tvLocalProgressStatus.isVisible = false
        setButtonsEnabled(true)
    }

    fun setButtonsEnabled(enabled: Boolean) {
        binding.btnExportLocal.isEnabled = enabled
        binding.btnImportLocal.isEnabled = enabled
    }

    fun updateLastBackupTime(timestamp: Long) {
        val formatted = if (timestamp > 0) {
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        } else {
            context.getString(R.string.bp_local_last_backup_never)
        }
        binding.tvLocalLastBackupTime.text = context.getString(R.string.bp_local_last_backup_prefix, formatted)
    }

    fun setOnExportClickListener(listener: () -> Unit) {
        binding.btnExportLocal.setOnClickListener { listener() }
    }

    fun setOnImportClickListener(listener: () -> Unit) {
        binding.btnImportLocal.setOnClickListener { listener() }
    }
}
