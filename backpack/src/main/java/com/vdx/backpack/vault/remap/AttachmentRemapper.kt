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
     * Remaps JSON arrays, JSON objects, or plain string paths containing file references.
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
                // Plain string path
                val fileName = extractFileName(trimmed)
                val isAttachment = if (backedUpFileNames != null) {
                    backedUpFileNames.contains(fileName)
                } else {
                    isFilePath(trimmed)
                }

                if (isAttachment) {
                    val newPath = joinPath(newBasePath, fileName)
                    if (trimmed != newPath) {
                        return newPath
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("AttachmentRemapper").w(e, "Skipping non-matching value for remapping")
        }

        return null
    }

    private fun isFilePath(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return false
        // Matches typical Android paths (/data/user/..., /storage/emulated/..., /files/...)
        val hasPathSeparators = trimmed.contains('/') || trimmed.contains('\\')
        val startsWithPath = trimmed.startsWith("/") || trimmed.startsWith("file:/", ignoreCase = true)
        return hasPathSeparators && startsWithPath
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
                        val oldPath = value.content
                        if (oldPath.isNotEmpty()) {
                            val fileName = extractFileName(oldPath)
                            val isMatch = if (backedUpFileNames != null) {
                                backedUpFileNames.contains(fileName)
                            } else {
                                isFilePath(oldPath)
                            }

                            if (isMatch) {
                                val newPath = joinPath(newBasePath, fileName)
                                if (oldPath != newPath) {
                                    newMap[key] = JsonPrimitive(newPath)
                                    modified = true
                                    continue
                                }
                            }
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
                    val oldPath = element.content
                    val fileName = extractFileName(oldPath)
                    val isMatch = if (backedUpFileNames != null) {
                        backedUpFileNames.contains(fileName)
                    } else {
                        isFilePath(oldPath)
                    }

                    if (isMatch) {
                        val newPath = joinPath(newBasePath, fileName)
                        if (oldPath != newPath) {
                            return JsonPrimitive(newPath) to true
                        }
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
