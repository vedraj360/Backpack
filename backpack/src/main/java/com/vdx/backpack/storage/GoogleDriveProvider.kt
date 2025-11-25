package com.vdx.backpack.storage

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.vdx.backpack.core.BackupMetadata
import com.vdx.backpack.exception.GoogleSignInErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

@Suppress("DEPRECATION")
class GoogleDriveProvider(
    private val context: Context,
    private val folderName: String
) : CloudStorageProvider {

    private var driveService: Drive? = null
    private var currentAccount: GoogleSignInAccount? = null
    private var signInClient: GoogleSignInClient? = null


    private fun getOrInitService(): Drive? {
        if (driveService != null && currentAccount != null) {
            return driveService
        }

        Timber.w("Drive Service/Account is null. Attempting auto-recovery...")

        val systemAccount = GoogleSignIn.getLastSignedInAccount(context)

        if (systemAccount != null) {
            if (GoogleSignIn.hasPermissions(systemAccount, Scope(DriveScopes.DRIVE_FILE))) {
                Timber.d("Recovered account from system: ${systemAccount.email}")
                initializeDriveService(systemAccount)
                currentAccount = systemAccount
                return driveService
            } else {
                Timber.w("System account found (${systemAccount.email}), but missing Drive permissions.")
            }
        } else {
            Timber.e("Recovery failed: No system account found.")
        }

        return null
    }

    private fun getSignInClient(activity: Activity): GoogleSignInClient {
        if (signInClient == null) {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveScopes.DRIVE_FILE))
                .build()
            signInClient = GoogleSignIn.getClient(activity, gso)
        }
        return signInClient!!
    }

    override fun getSignInIntent(activity: Activity): Intent {
        return getSignInClient(activity).signInIntent
    }

    override suspend fun handleSignInResult(activity: Activity, data: Intent?): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                Timber.d("=== Handling Sign-In Result ===")

                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val intentAccount = task.await() // Throws if user cancelled or failed

                val finalAccount = GoogleSignIn.getLastSignedInAccount(context) ?: intentAccount

                Timber.d("Account resolved. Email: ${finalAccount.email}")

                if (!GoogleSignIn.hasPermissions(finalAccount, Scope(DriveScopes.DRIVE_FILE))) {
                    Timber.e("Sign-in successful, but Drive Scope denied.")
                    return@withContext Result.failure(Exception("Drive permission not granted"))
                }

                initializeDriveService(finalAccount)
                currentAccount = finalAccount

                if (driveService == null) {
                    Timber.e("CRITICAL: Drive Service failed to initialize despite valid account.")
                    throw Exception("Service initialization failed")
                }

                Timber.d("Sign-in complete. Service ready for: ${finalAccount.email}")
                Result.success(finalAccount.email ?: "Unknown")

            } catch (e: Exception) {
                driveService = null
                currentAccount = null

                val errorMessage = GoogleSignInErrorHandler.getErrorMessage(e)
                Timber.e(e, "Sign in failed: $errorMessage")

                Result.failure(Exception(errorMessage))
            }
        }

    private fun initializeDriveService(account: GoogleSignInAccount) {
        try {
            Timber.d("Initializing Drive API for: ${account.email}")

            val credential = GoogleAccountCredential.usingOAuth2(
                context.applicationContext,
                listOf(DriveScopes.DRIVE_FILE)
            )

            credential.selectedAccountName = account.email

            driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("Backpack")
                .build()

            Timber.d("Drive API initialized successfully.")
        } catch (e: Exception) {
            Timber.e(e, "Failed to create Drive Service")
            driveService = null
        }
    }


    override suspend fun uploadBackup(file: File, fileName: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val service = getOrInitService() ?: return@withContext Result.failure(
                    Exception("Not authenticated. Please sign in first.")
                )

                Timber.d("Starting upload: $fileName (${file.length()} bytes)")

                val folderId = getOrCreateFolder(service, folderName)

                val fileMetadata = com.google.api.services.drive.model.File()
                    .setName(fileName)
                    .setParents(listOf(folderId))
                    .setMimeType("application/x-sqlite3")

                val mediaContent = FileContent("application/x-sqlite3", file)

                val uploadedFile = service.files()
                    .create(fileMetadata, mediaContent)
                    .setFields("id, name, size")
                    .execute()

                Timber.d("Upload Success! ID: ${uploadedFile.id}, Size: ${uploadedFile.size}")
                Result.success(uploadedFile.id)
            } catch (e: Exception) {
                Timber.e(e, "Upload operation failed")
                Result.failure(e)
            }
        }

    override suspend fun downloadBackup(fileId: String, destinationFile: File): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val service = getOrInitService() ?: return@withContext Result.failure(
                    Exception("Not authenticated")
                )

                Timber.d("Starting download for ID: $fileId")

                val outputStream = FileOutputStream(destinationFile)
                service.files().get(fileId).executeMediaAndDownloadTo(outputStream)
                outputStream.close()

                if (destinationFile.length() == 0L) {
                    Timber.e("Download finished but file is empty!")
                    return@withContext Result.failure(Exception("Downloaded file is empty"))
                }

                Timber.d("Download Success! Saved to: ${destinationFile.absolutePath} (${destinationFile.length()} bytes)")
                Result.success(destinationFile)
            } catch (e: Exception) {
                Timber.e(e, "Download operation failed")
                Result.failure(e)
            }
        }

    override suspend fun listBackups(): Result<List<BackupMetadata>> = withContext(Dispatchers.IO) {
        try {
            val service = getOrInitService() ?: return@withContext Result.failure(
                Exception("Not authenticated")
            )

            Timber.d("Listing backups in folder: $folderName")
            val folderId = getOrCreateFolder(service, folderName)

            val result = service.files().list()
                .setQ("'$folderId' in parents and trashed=false")
                .setOrderBy("createdTime desc")
                .setFields("files(id, name, createdTime, size)")
                .execute()

            val backups = result.files.map { file ->
                BackupMetadata(
                    fileId = file.id,
                    fileName = file.name,
                    timestamp = file.createdTime?.value ?: 0L,
                    size = file.getSize() ?: 0L
                )
            }

            Timber.d("Found ${backups.size} backups.")
            Result.success(backups)
        } catch (e: Exception) {
            Timber.e(e, "List backups failed")
            Result.failure(e)
        }
    }

    override suspend fun deleteBackup(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val service = getOrInitService() ?: return@withContext Result.failure(
                Exception("Not authenticated")
            )
            service.files().delete(fileId).execute()
            Timber.d("Deleted backup file: $fileId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Delete failed")
            Result.failure(e)
        }
    }

    private fun getOrCreateFolder(service: Drive, folderName: String): String {
        try {
            val query =
                "mimeType='application/vnd.google-apps.folder' and name='$folderName' and trashed=false"
            val result = service.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            if (result.files.isNotEmpty()) {
                return result.files[0].id
            }

            Timber.d("Folder '$folderName' not found. Creating new one...")
            val folderMetadata = com.google.api.services.drive.model.File()
                .setName(folderName)
                .setMimeType("application/vnd.google-apps.folder")

            val folder = service.files().create(folderMetadata)
                .setFields("id")
                .execute()

            Timber.d("Folder created: ${folder.id}")
            return folder.id
        } catch (e: Exception) {
            Timber.e(e, "Error getting/creating folder")
            throw e
        }
    }

    override fun isAuthenticated(): Boolean {
        return getOrInitService() != null
    }

    override fun getAuthenticatedEmail(): String? {
        getOrInitService()
        return currentAccount?.email
    }

    override fun signOut(activity: Activity) {
        try {
            getSignInClient(activity).signOut()
            driveService = null
            currentAccount = null
            signInClient = null
            Timber.d("Signed out successfully")
        } catch (e: Exception) {
            Timber.e(e, "Sign out error")
        }
    }
}