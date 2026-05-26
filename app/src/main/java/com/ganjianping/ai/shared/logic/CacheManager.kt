package com.ganjianping.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

class CacheManager(context: Context) {
    private val root = File(context.cacheDir, "gjp_cache")

    suspend fun save(items: List<OpenItem>, key: String) = withContext(Dispatchers.IO) {
        root.mkdirs()
        val array = JSONArray()
        items.forEach { array.put(it.toJsonObject()) }
        File(root, "$key.json").writeText(array.toString())
    }

    suspend fun loadRaw(key: String): String? = withContext(Dispatchers.IO) {
        File(root, "$key.json").takeIf(File::exists)?.readText()
    }

    suspend fun size(key: String): Long = withContext(Dispatchers.IO) {
        File(root, "$key.json").takeIf(File::exists)?.length() ?: 0L
    }

    suspend fun lastModified(key: String): Long? = withContext(Dispatchers.IO) {
        File(root, "$key.json").takeIf(File::exists)?.lastModified()?.takeIf { it > 0L }
    }

    suspend fun clear(key: String) = withContext(Dispatchers.IO) {
        File(root, "$key.json").delete()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        root.deleteRecursively()
    }

    suspend fun summary(key: String): String = loadRaw(key) ?: "No data found."
}
