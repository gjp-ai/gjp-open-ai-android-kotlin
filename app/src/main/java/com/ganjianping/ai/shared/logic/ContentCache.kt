package com.ganjianping.ai

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ContentCacheStats(
    val key: String,
    val count: Int,
    val lastSyncedAt: Long?,
    val updatedAfter: String?,
    val sizeBytes: Long
)

data class ContentCacheRow(
    val id: String,
    val lang: LanguageCode,
    val title: String,
    val tags: String?,
    val displayOrder: Int,
    val updatedAt: String,
    val json: String,
    val syncedAt: Long,
    val sizeBytes: Long
)

class ContentCache(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    private val databaseFile = context.getDatabasePath(DATABASE_NAME)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE content_items (
                resource_key TEXT NOT NULL,
                lang TEXT NOT NULL,
                id TEXT NOT NULL,
                tags TEXT,
                searchable_text TEXT NOT NULL,
                sort_title TEXT NOT NULL,
                display_order INTEGER NOT NULL,
                updated_at TEXT NOT NULL,
                json TEXT NOT NULL,
                synced_at INTEGER NOT NULL,
                PRIMARY KEY(resource_key, lang, id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE content_sync (
                resource_key TEXT NOT NULL,
                lang TEXT NOT NULL,
                updated_after TEXT,
                synced_at INTEGER NOT NULL,
                PRIMARY KEY(resource_key, lang)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_content_items_resource_lang ON content_items(resource_key, lang)")
        db.execSQL("CREATE INDEX idx_content_items_search ON content_items(resource_key, lang, searchable_text)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS content_items")
        db.execSQL("DROP TABLE IF EXISTS content_sync")
        onCreate(db)
    }

    suspend fun <T : OpenItem> save(resourceKey: String, items: List<T>, replaceExisting: Boolean) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        writableDatabase.runInTransaction {
            if (replaceExisting) {
                delete("content_items", "resource_key = ?", arrayOf(resourceKey))
                delete("content_sync", "resource_key = ?", arrayOf(resourceKey))
            }
            items.forEach { item ->
                insertWithOnConflict("content_items", null, item.contentValues(resourceKey, now), SQLiteDatabase.CONFLICT_REPLACE)
            }
            val byLanguage = items.groupBy { it.lang }
            byLanguage.forEach { (language, languageItems) ->
                val maxUpdatedAt = languageItems.map { it.updatedAt }.filter { it.isNotBlank() }.maxOrNull()
                    ?: updatedAfterSync(resourceKey, language)
                val values = ContentValues().apply {
                    put("resource_key", resourceKey)
                    put("lang", language.rawValue)
                    put("updated_after", maxUpdatedAt)
                    put("synced_at", now)
                }
                insertWithOnConflict("content_sync", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }

    suspend fun <T : OpenItem> query(
        resourceKey: String,
        language: LanguageCode,
        parser: (JSONObject) -> T,
        search: String? = null,
        tag: String? = null,
        sortOrder: SortOrder = SortOrder.DISPLAY_ORDER
    ): List<T> =
        withContext(Dispatchers.IO) {
            val selection = StringBuilder("resource_key = ? AND lang = ?")
            val args = mutableListOf(resourceKey, language.rawValue)
            if (!search.isNullOrBlank()) {
                selection.append(" AND searchable_text LIKE ? ESCAPE '\\'")
                args += "%${search.escapeLike()}%"
            }
            if (!tag.isNullOrBlank()) {
                selection.append(" AND (',' || REPLACE(tags, ' ', '') || ',') LIKE ?")
                args += "%,${tag.replace(" ", "")},%"
            }
            val orderBy = when (sortOrder) {
                SortOrder.DISPLAY_ORDER -> "display_order ASC, sort_title COLLATE NOCASE ASC"
                SortOrder.ALPHA -> "sort_title COLLATE NOCASE ASC"
                SortOrder.RECENT -> "updated_at DESC, display_order ASC"
            }
            readableDatabase.query(
                "content_items",
                arrayOf("json"),
                selection.toString(),
                args.toTypedArray(),
                null,
                null,
                orderBy
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        runCatching { parser(JSONObject(cursor.getString(0))) }.getOrNull()?.let(::add)
                    }
                }
            }
        }

    suspend fun updatedAfter(resourceKey: String, language: LanguageCode): String? = withContext(Dispatchers.IO) {
        updatedAfterSync(resourceKey, language)
    }

    suspend fun lastModified(resourceKey: String, language: LanguageCode): Long? = withContext(Dispatchers.IO) {
        lastSyncedAtSync(resourceKey, language)
    }

    suspend fun isFresh(resourceKey: String, language: LanguageCode, freshnessMillis: Long): Boolean = withContext(Dispatchers.IO) {
        val last = lastSyncedAtSync(resourceKey, language) ?: return@withContext false
        System.currentTimeMillis() - last < freshnessMillis
    }

    suspend fun markSynced(resourceKey: String, language: LanguageCode) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("resource_key", resourceKey)
            put("lang", language.rawValue)
            put("updated_after", updatedAfterSync(resourceKey, language))
            put("synced_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("content_sync", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun stats(keys: List<String>): List<ContentCacheStats> = withContext(Dispatchers.IO) {
        keys.map { key ->
            ContentCacheStats(
                key = key,
                count = countSync(key),
                lastSyncedAt = lastSyncedAtSync(key, null),
                updatedAfter = updatedAfterSync(key, null),
                sizeBytes = sizeSync(key)
            )
        }
    }

    suspend fun count(resourceKey: String, language: LanguageCode): Int = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM content_items WHERE resource_key = ? AND lang = ?",
            arrayOf(resourceKey, language.rawValue)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    suspend fun rows(resourceKey: String): List<ContentCacheRow> = withContext(Dispatchers.IO) {
        readableDatabase.query(
            "content_items",
            arrayOf("id", "lang", "sort_title", "tags", "display_order", "updated_at", "json", "synced_at", "LENGTH(json)"),
            "resource_key = ?",
            arrayOf(resourceKey),
            null,
            null,
            "lang ASC, display_order ASC, sort_title COLLATE NOCASE ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ContentCacheRow(
                            id = cursor.getString(0),
                            lang = LanguageCode.from(cursor.getString(1)),
                            title = cursor.getString(2),
                            tags = cursor.getString(3),
                            displayOrder = cursor.getInt(4),
                            updatedAt = cursor.getString(5),
                            json = cursor.getString(6),
                            syncedAt = cursor.getLong(7),
                            sizeBytes = cursor.getLong(8)
                        )
                    )
                }
            }
        }
    }

    suspend fun rowSummary(resourceKey: String): String = withContext(Dispatchers.IO) {
        rows(resourceKey).joinToString("\n\n") { row ->
            """
            id: ${row.id}
            title: ${row.title}
            lang: ${row.lang.rawValue}
            tags: ${row.tags.orEmpty()}
            updatedAt: ${row.updatedAt}
            storedBytes: ${row.sizeBytes}
            """.trimIndent()
        }.ifBlank { "No data found." }
    }

    suspend fun clear(resourceKey: String) = withContext(Dispatchers.IO) {
        writableDatabase.runInTransaction {
            delete("content_items", "resource_key = ?", arrayOf(resourceKey))
            delete("content_sync", "resource_key = ?", arrayOf(resourceKey))
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        writableDatabase.runInTransaction {
            delete("content_items", null, null)
            delete("content_sync", null, null)
        }
    }

    fun databaseSizeBytes(): Long = if (databaseFile.exists()) databaseFile.length() else 0L

    private fun OpenItem.contentValues(resourceKey: String, syncedAt: Long) = ContentValues().apply {
        put("resource_key", resourceKey)
        put("lang", lang.rawValue)
        put("id", id)
        put("tags", tags)
        put("searchable_text", searchableText)
        put("sort_title", sortTitle)
        put("display_order", displayOrder)
        put("updated_at", updatedAt)
        put("json", toJsonObject().toString())
        put("synced_at", syncedAt)
    }

    private fun countSync(resourceKey: String): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM content_items WHERE resource_key = ?", arrayOf(resourceKey)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    private fun sizeSync(resourceKey: String): Long =
        readableDatabase.rawQuery("SELECT COALESCE(SUM(LENGTH(json)), 0) FROM content_items WHERE resource_key = ?", arrayOf(resourceKey)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }

    private fun updatedAfterSync(resourceKey: String, language: LanguageCode?): String? {
        val selection = if (language == null) "resource_key = ?" else "resource_key = ? AND lang = ?"
        val args = if (language == null) arrayOf(resourceKey) else arrayOf(resourceKey, language.rawValue)
        return readableDatabase.query(
            "content_sync",
            arrayOf("updated_after"),
            selection,
            args,
            null,
            null,
            "synced_at DESC",
            "1"
        ).use { cursor -> cursor.firstStringOrNull() }
    }

    private fun lastSyncedAtSync(resourceKey: String, language: LanguageCode?): Long? {
        val selection = if (language == null) "resource_key = ?" else "resource_key = ? AND lang = ?"
        val args = if (language == null) arrayOf(resourceKey) else arrayOf(resourceKey, language.rawValue)
        return readableDatabase.query(
            "content_sync",
            arrayOf("synced_at"),
            selection,
            args,
            null,
            null,
            "synced_at DESC",
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    private fun SQLiteDatabase.runInTransaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    private fun Cursor.firstStringOrNull(): String? =
        if (moveToFirst()) getString(0)?.takeIf { it.isNotBlank() } else null

    private fun String.escapeLike(): String =
        replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    companion object {
        private const val DATABASE_NAME = "gjp_open_content_cache.db"
        private const val DATABASE_VERSION = 1

        val RESOURCE_KEYS = listOf("websites", "questions", "articles", "images", "videos", "audios", "files")
    }
}
