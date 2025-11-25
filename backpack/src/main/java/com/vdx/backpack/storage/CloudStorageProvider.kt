package com.vdx.backpack.storage

import android.accounts.Account
import android.app.Activity
import android.content.Intent
import com.vdx.backpack.core.BackupMetadata
import java.io.File

interface CloudStorageProvider {
    fun getSignInIntent(activity: Activity): Intent
    suspend fun handleSignInResult(activity: Activity, data: Intent?): Result<String>
    suspend fun uploadBackup(file: File, fileName: String): Result<String>
    suspend fun downloadBackup(fileId: String, destinationFile: File): Result<File>
    suspend fun listBackups(): Result<List<BackupMetadata>>
    suspend fun deleteBackup(fileId: String): Result<Unit>
    fun isAuthenticated(): Boolean
    fun getAuthenticatedEmail(): String?
    fun signOut(activity: Activity)
}