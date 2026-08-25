# Backpack 🎒

**All-In-One Backup & Restore Suite for Android (Google Drive Cloud + Local Storage)**

Backpack is a production-ready, drop-in Android backup framework that packages your **Room SQLite Database** and **All Media Attachments** (images, audio, videos, documents) into an encrypted, portable archive.

It supports both **Google Drive (Cloud)** and **Device Storage (SAF / Local)** with cross-device path remapping and automatic schema/conflict safety.

---

[![](https://jitpack.io/v/vedraj360/Backpack.svg)](https://jitpack.io/#vedraj360/Backpack)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

---

## ✨ Features

- ☁️ **Google Drive App Folder**: Uploads encrypted archives directly to the user's isolated Google Drive app folder.
- 📱 **Local Device Storage (SAF)**: Export and Import backups via Android's Storage Access Framework file picker.
- 🖼️ **Full Media Attachments Support**: Automatically extracts, archives, and restores images, audio notes, PDFs, videos, and custom attachments.
- 🔄 **Cross-Device Path Remapping**: Deterministic, manifest-driven path translation so restored attachments load correctly across different Android versions and device storage roots.
- 🛡️ **AES-256 GCM Encryption**: Secure encryption for both cloud and local archives before writing or uploading.
- ⚡ **Atomic DB Swap & Rollback**: Safe WAL checkpointing and automatic `.bak` rollback protection in case of unexpected errors.
- ⚠️ **Attachment Conflict Resolution**: Built-in duplicate detection (`OVERWRITE`, `SKIP`, or `ASK_USER`).
- 🔄 **100% Backward Compatible**: Automatically detects and restores legacy v1 raw `.db` files from Google Drive without crashing.
- 🎨 **Plug-and-Play UI**: Includes pre-built `BackupView` and modular APIs.

---

## 📲 Used In Production

Backpack powers backup & recovery for **[AutoSend](https://play.google.com/store/apps/details?id=com.vdx.autosend)** on Google Play.

---

## 📦 Installation

### 1. Add JitPack to `settings.gradle.kts`

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. Add Dependency in `build.gradle.kts`

```kotlin
dependencies {
    implementation("com.github.vedraj360:Backpack:2.0.0")
}
```

---

## 🚀 Quick Start

### 1. Initialize Backpack

In your `Application` class:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val database = AppDatabase.getInstance(this)

        val config = BackupConfig(
            database = database,
            folderName = "My App Backups",
            encryptionEnabled = true,
            autoBackupEnabled = true,
            backupIntervalHours = 24,
            includeAttachmentTypes = setOf(
                AttachmentType.IMAGE,
                AttachmentType.AUDIO,
                AttachmentType.VIDEO,
                AttachmentType.DOCUMENT
            )
        )

        Backpack.initialize(this, config)
    }
}
```

---

## ☁️ Google Drive Cloud Backup

```kotlin
// 1. Check Authentication & Sign-in
val isAuth = Backpack.driveProvider.isAuthenticated()
val signInIntent = Backpack.driveProvider.getSignInIntent(activity)

// In Activity Result:
Backpack.driveProvider.handleSignInResult(activity, dataIntent)

// 2. Perform Cloud Backup (DB + Attachments)
lifecycleScope.launch {
    Backpack.cloud.backup(context).collect { result ->
        when (result) {
            is BackupResult.InProgress -> updateProgress(result.progress)
            is BackupResult.Success -> showSuccess("Backup completed!")
            is BackupResult.Failure -> showError(result.error)
            is BackupResult.ConflictDetected -> {}
        }
    }
}

// 3. Perform Cloud Restore
lifecycleScope.launch {
    Backpack.cloud.restore(context, fileId).collect { result ->
        when (result) {
            is BackupResult.InProgress -> updateProgress(result.progress)
            is BackupResult.Success -> restartApp()
            is BackupResult.ConflictDetected -> {
                // Prompt user to Overwrite or Keep Existing
                Backpack.cloud.resolveConflict(context, overwrite = true)
            }
            is BackupResult.Failure -> showError(result.error)
        }
    }
}
```

---

## 📱 Local Device Storage Backup (SAF)

```kotlin
// 1. Export Backup to Local File (SAF CreateDocument)
val createDocLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
    if (uri != null) {
        lifecycleScope.launch {
            Backpack.local.export(uri).collect { result ->
                when (result) {
                    is LocalBackupResult.InProgress -> updateProgress(result.progress)
                    is LocalBackupResult.Success -> showSuccess("Exported successfully!")
                    is LocalBackupResult.Failure -> showError(result.error)
                    else -> {}
                }
            }
        }
    }
}

// 2. Import Backup from Local File (SAF OpenDocument)
val openDocLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
        lifecycleScope.launch {
            Backpack.local.import(uri).collect { result ->
                when (result) {
                    is LocalBackupResult.InProgress -> updateProgress(result.progress)
                    is LocalBackupResult.Success -> restartApp()
                    is LocalBackupResult.ConflictDetected -> {
                        // Resolve conflict:
                        Backpack.local.resolveConflict(overwrite = true)
                    }
                    is LocalBackupResult.Failure -> showError(result.error)
                }
            }
        }
    }
}
```

---

## 📄 License

```text
Copyright 2026 Vedraj Sharma

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
