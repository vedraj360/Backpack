package com.vdx.backpack.util

import java.io.File

object FileUtils {
    
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
    
    fun cleanupTempFiles(cacheDir: File) {
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.contains("backup") || file.name.contains("restore")) {
                file.delete()
            }
        }
    }
}
