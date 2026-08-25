package com.vdx.backpack.vault.remap

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.json.*
import timber.log.Timber
import java.io.File

object AttachmentRemapper {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * Opens the restored SQLite database file directly (BEFORE Room opens it),
     * finds all stored savedPath references, and updates them to the new device's
     * attachments directory.
     *
     * @param dbFile Staged SQLite database file
     * @param newAttachmentsDir Directory where attachments are stored on current device
     * @param targetColumns Optional explicit map of Table -> Columns. If null, auto-scans all text columns in all user tables.
     */
    fun remap(
        dbFile: File,
        newAttachmentsDir: File,
        targetColumns: Map<String, List<String>>? = null,
        backedUpFileNames: Set<String>? = null
    ) {
        if (!dbFile.exists()) {
            Timber.tag("AttachmentRemapper").w("DB file does not exist for remapping: ${dbFile.absolutePath}")
            return
        }

        val newBasePath = newAttachmentsDir.absolutePath
        var db: SQLiteDatabase? = null

        try {
            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            db.beginTransaction()

            val columnsToScan = targetColumns ?: discoverAllTextColumns(db)
            Timber.tag("AttachmentRemapper").d("Scanning tables/columns for attachment remapping: $columnsToScan")

            for ((tableName, columns) in columnsToScan) {
                if (!tableExists(db, tableName)) continue

                for (columnName in columns) {
                    if (!columnExists(db, tableName, columnName)) continue
                    remapColumn(db, tableName, columnName, newBasePath, backedUpFileNames)
                }
            }

            db.setTransactionSuccessful()
            Timber.tag("AttachmentRemapper").i("Successfully remapped attachment paths in database")
        } catch (e: Exception) {
            Timber.tag("AttachmentRemapper").e(e, "Error during attachment path remapping")
            throw e
        } finally {
            try {
                db?.endTransaction()
            } catch (_: Exception) {}
            try {
                db?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Dynamically discovers all user tables and text columns in the SQLite database.
     * Excludes system tables (sqlite_*, room_*, android_*).
     */
    private fun discoverAllTextColumns(db: SQLiteDatabase): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()

        val tableCursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' " +
                    "AND name NOT LIKE 'sqlite_%' " +
                    "AND name NOT LIKE 'room_%' " +
                    "AND name NOT LIKE 'android_%'",
            null
        )

        tableCursor.use {
            while (it.moveToNext()) {
                val tableName = it.getString(0) ?: continue
                val columnCursor = db.rawQuery("PRAGMA table_info($tableName)", null)

                columnCursor.use { cCursor ->
                    val nameIdx = cCursor.getColumnIndex("name")
                    val typeIdx = cCursor.getColumnIndex("type")

                    if (nameIdx != -1) {
                        while (cCursor.moveToNext()) {
                            val colName = cCursor.getString(nameIdx)
                            val colType = if (typeIdx != -1) cCursor.getString(typeIdx)?.uppercase() ?: "" else ""

                            if (colType.contains("TEXT") || colType.contains("CHAR") || colType.contains("CLOB") || colType.isEmpty()) {
                                result.getOrPut(tableName) { mutableListOf() }.add(colName)
                            }
                        }
                    }
                }
            }
        }

        return result
    }

    private fun remapColumn(
        db: SQLiteDatabase,
        tableName: String,
        columnName: String,
        newBasePath: String,
        backedUpFileNames: Set<String>?
    ) {
        val primaryKeyCol = getPrimaryKeyColumn(db, tableName) ?: "id"
        if (!columnExists(db, tableName, primaryKeyCol)) return

        val cursor = db.rawQuery(
            "SELECT $primaryKeyCol, $columnName FROM $tableName WHERE $columnName IS NOT NULL AND $columnName != ''",
            null
        )

        cursor.use {
            val idIndex = cursor.getColumnIndex(primaryKeyCol)
            val columnIndex = cursor.getColumnIndex(columnName)

            if (idIndex == -1 || columnIndex == -1) return

            while (cursor.moveToNext()) {
                val rowId = cursor.getString(idIndex) ?: continue
                val rawValue = cursor.getString(columnIndex) ?: continue

                val updatedValue = remapValue(rawValue, newBasePath, backedUpFileNames)
                if (updatedValue != null && updatedValue != rawValue) {
                    val cv = ContentValues().apply {
                        put(columnName, updatedValue)
                    }
                    db.update(tableName, cv, "$primaryKeyCol = ?", arrayOf(rowId))
                }
            }
        }
    }

    /**
     * Remaps JSON arrays, JSON objects, or plain string paths containing local file references.
     * Uses backedUpFileNames when available for 100% deterministic matching without guessing.
     */
    fun remapValue(
        rawValue: String,
        newBasePath: String,
        backedUpFileNames: Set<String>? = null
    ): String? {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) return null

        try {
            if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
                val element = json.parseToJsonElement(trimmed)
                val (modifiedElement, wasModified) = remapJsonElement(element, newBasePath, backedUpFileNames)
                if (wasModified) {
                    return json.encodeToString(JsonElement.serializer(), modifiedElement)
                }
            } else {
                return remapStringIfPath(trimmed, newBasePath, backedUpFileNames)
            }
        } catch (e: Exception) {
            Timber.tag("AttachmentRemapper").w(e, "Skipping non-matching value for remapping")
        }

        return null
    }

    /**
     * Validates whether a given string is an actual local absolute file path or file:// URI.
     * Rejects plain filenames ("photo.jpg"), content URIs ("content://..."), web URLs ("http..."), etc.
     */
    private fun isLocalFilePath(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return false

        // Exclude content URIs, web URLs, and Android resource URIs
        if (trimmed.startsWith("content://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("android.resource://", ignoreCase = true)
        ) {
            return false
        }

        // Must start with an absolute file path separator or file scheme
        val isAbsolute = trimmed.startsWith("/") ||
                trimmed.startsWith("file://", ignoreCase = true) ||
                trimmed.startsWith("file:/", ignoreCase = true)

        // Must contain directory separators to be a valid path (not just a filename)
        val pathWithoutScheme = if (trimmed.startsWith("file://", ignoreCase = true)) {
            trimmed.substring(7)
        } else if (trimmed.startsWith("file:/", ignoreCase = true)) {
            trimmed.substring(5)
        } else {
            trimmed
        }

        val hasDirectorySeparators = pathWithoutScheme.contains('/') || pathWithoutScheme.contains('\\')

        return isAbsolute && hasDirectorySeparators
    }

    private fun remapStringIfPath(
        value: String,
        newBasePath: String,
        backedUpFileNames: Set<String>?
    ): String? {
        if (!isLocalFilePath(value)) return null

        val isFileUri = value.startsWith("file://", ignoreCase = true) || value.startsWith("file:/", ignoreCase = true)
        val rawPath = if (value.startsWith("file://", ignoreCase = true)) {
            value.substring(7)
        } else if (value.startsWith("file:/", ignoreCase = true)) {
            value.substring(5)
        } else {
            value
        }

        val fileName = extractFileName(rawPath)
        if (fileName.isEmpty() || fileName == rawPath) return null

        val isAttachment = if (backedUpFileNames != null) {
            backedUpFileNames.contains(fileName)
        } else {
            true
        }

        if (isAttachment) {
            val newAbsolutePath = joinPath(newBasePath, fileName)
            val finalPath = if (isFileUri) "file://$newAbsolutePath" else newAbsolutePath
            if (value != finalPath) {
                return finalPath
            }
        }

        return null
    }

    private fun remapJsonElement(
        element: JsonElement,
        newBasePath: String,
        backedUpFileNames: Set<String>?
    ): Pair<JsonElement, Boolean> {
        return when (element) {
            is JsonArray -> {
                var modified = false
                val newArray = element.map { item ->
                    val (remapItem, itemModified) = remapJsonElement(item, newBasePath, backedUpFileNames)
                    if (itemModified) modified = true
                    remapItem
                }
                JsonArray(newArray) to modified
            }
            is JsonObject -> {
                var modified = false
                val newMap = element.toMutableMap()
                for ((key, value) in element) {
                    if (value is JsonPrimitive && value.isString) {
                        val remapped = remapStringIfPath(value.content, newBasePath, backedUpFileNames)
                        if (remapped != null) {
                            newMap[key] = JsonPrimitive(remapped)
                            modified = true
                            continue
                        }
                    }

                    val (childElement, childModified) = remapJsonElement(value, newBasePath, backedUpFileNames)
                    if (childModified) {
                        newMap[key] = childElement
                        modified = true
                    }
                }
                JsonObject(newMap) to modified
            }
            is JsonPrimitive -> {
                if (element.isString) {
                    val remapped = remapStringIfPath(element.content, newBasePath, backedUpFileNames)
                    if (remapped != null) {
                        return JsonPrimitive(remapped) to true
                    }
                }
                element to false
            }
        }
    }

    private fun extractFileName(path: String): String {
        val lastSlash = maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\'))
        return if (lastSlash >= 0) path.substring(lastSlash + 1) else path
    }

    private fun joinPath(baseDir: String, fileName: String): String {
        val cleanBase = baseDir.trimEnd('/', '\\')
        return "$cleanBase/$fileName"
    }

    private fun getPrimaryKeyColumn(db: SQLiteDatabase, tableName: String): String? {
        val cursor = db.rawQuery("PRAGMA table_info($tableName)", null)
        return cursor.use {
            val nameIdx = it.getColumnIndex("name")
            val pkIdx = it.getColumnIndex("pk")
            if (nameIdx == -1 || pkIdx == -1) return null

            while (it.moveToNext()) {
                if (it.getInt(pkIdx) > 0) {
                    return it.getString(nameIdx)
                }
            }
            null
        }
    }

    private fun tableExists(db: SQLiteDatabase, tableName: String): Boolean {
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        )
        return cursor.use { it.count > 0 }
    }

    private fun columnExists(db: SQLiteDatabase, tableName: String, columnName: String): Boolean {
        val cursor = db.rawQuery("PRAGMA table_info($tableName)", null)
        return cursor.use {
            val nameIndex = cursor.getColumnIndex("name")
            if (nameIndex == -1) return false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == columnName) {
                    return true
                }
            }
            false
        }
    }
}
