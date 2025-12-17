# Backpack 🎒

Secure, Drop-in Google Drive Backup for Android Room Databases

Backpack is a production-ready Android library that encrypts, exports,
and uploads your Room database to a private Google Drive App Folder. It
manages SQLite WAL/SHM complexity, handles Google Identity Services
authentication, and offers a plug-and-play UI.

## Features

-   **AES-256 Encryption**
    Encrypts all database files before upload.

-   **Google Drive App Folder**
    Stores backups in an isolated, hidden location users can't tamper
    with.

-   **Coroutines & Flow**
    Fully asynchronous, modern Kotlin API.

-   **Room-Aware Backup Engine**
    Gracefully handles WAL, SHM, checkpointing, and safe DB closure.

-   **Built-in UI**
    Includes a `BackupView` for instant onboarding and authentication
    handling.

## Installation

### 1. Add JitPack

Add to `settings.gradle.kts`:

``` kotlin
dependencyResolutionManagement {
    repositories {
        ...
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. Add Dependency

``` kotlin
dependencies {
    implementation("com.github.vedraj360:Backpack:1.0.1")
}
```

## Quick Start

### 1. Initialize Backpack

``` kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val database = MyDatabase.getInstance(this)

        val config = BackupConfig(
            database = database,
            folderName = "My App Backups",
            encryptionEnabled = true,
            backupFileName = "user_data",
            maxBackupFiles = 5
        )

        Backpack.initialize(this, config)
    }
}
```

## Optional Built-in UI

``` xml
<com.vdx.backpack.ui.custom.BackupView
    android:id="@+id/backupView"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:signInTitle="Cloud Sync"
    app:signInDescription="Sign in to keep your data safe."
    app:showAutoBackupToggle="true" />
```

## Authentication API

``` kotlin
val isAuth = Backpack.driveProvider.isAuthenticated()

val intent = Backpack.driveProvider.getSignInIntent(activity)

Backpack.driveProvider.handleSignInResult(activity, dataIntent)
    .onSuccess { email -> Log.d("Auth", "Signed in: $email") }
    .onFailure { e -> Log.e("Auth", "Failed", e) }

Backpack.driveProvider.signOut(activity)
```

## Backup & Restore

### Backup

``` kotlin
lifecycleScope.launch {
    Backpack.manager.backup(context).collect { result ->
        when (result) {
            is BackupResult.InProgress -> showProgress(result.progress)
            is BackupResult.Success -> showSuccess("Saved!")
            is BackupResult.Failure -> showError(result.error)
        }
    }
}
```

### Restore

``` kotlin
val result = Backpack.manager.listAvailableBackups()
val latestFile = result.getOrNull()?.firstOrNull()

latestFile?.let { file ->
    Backpack.manager.restore(context, file.fileId).collect { result ->
        if (result is BackupResult.Success) {
            AppRestartHelper.triggerRestart(context)
        }
    }
}
```

## Google Cloud Setup

Backpack requires a Google Cloud project with an OAuth Client ID for
Drive access.

Setup guide: [Google Cloud Setup Guide](https://vedraj360.medium.com/integrating-google-drive-for-android-cloud-backups-a-complete-setup-guide-6cd8611f1905)

## License

Copyright 2025 Vedraj

Licensed under the Apache License, Version 2.0. You may obtain a copy at
http://www.apache.org/licenses/LICENSE-2.0

Distributed on an "AS IS" basis, without warranties or conditions of any
kind.
