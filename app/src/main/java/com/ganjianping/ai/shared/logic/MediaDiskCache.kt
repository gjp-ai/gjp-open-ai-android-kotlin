package com.ganjianping.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class MediaCacheStats(
    val namespace: String,
    val count: Int,
    val sizeBytes: Long,
    val lastModified: Long?
)

data class MediaCacheFile(
    val namespace: String,
    val name: String,
    val originalUrl: String?,
    val sizeBytes: Long,
    val lastModified: Long
)

class MediaDiskCache(context: Context) {
    private val root = File(context.cacheDir, "gjp_media_cache")
    private val memoryCache = object : LruCache<String, Bitmap>(MEMORY_CAPACITY_BYTES / 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun bitmap(url: String?, namespace: String): Bitmap? = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeUrl(url) ?: return@withContext null
        memoryCache.get("$namespace:$normalizedUrl")?.let { return@withContext it }
        val file = dataFile(normalizedUrl, namespace) ?: return@withContext null
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
        memoryCache.put("$namespace:$normalizedUrl", bitmap)
        bitmap
    }

    suspend fun data(url: String?, namespace: String): ByteArray? = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeUrl(url) ?: return@withContext null
        dataFile(normalizedUrl, namespace)?.readBytes()
    }

    suspend fun prefetch(urls: List<String>, namespace: String) = withContext(Dispatchers.IO) {
        urls.mapNotNull { normalizeUrl(it) }.distinct().forEach { normalizedUrl ->
            dataFile(normalizedUrl, namespace)
        }
    }

    suspend fun stats(namespaces: List<String>): List<MediaCacheStats> = withContext(Dispatchers.IO) {
        namespaces.map { namespace ->
            val files = dataFiles(namespace)
            MediaCacheStats(
                namespace = namespace,
                count = files.size,
                sizeBytes = files.sumOf { it.length() },
                lastModified = files.maxOfOrNull { it.lastModified() }?.takeIf { it > 0L }
            )
        }
    }

    suspend fun files(namespace: String): List<MediaCacheFile> = withContext(Dispatchers.IO) {
        dataFiles(namespace).map {
            MediaCacheFile(
                namespace = namespace,
                name = it.name,
                originalUrl = File(it.parentFile, "${it.name}.url").takeIf(File::exists)?.readText(),
                sizeBytes = it.length(),
                lastModified = it.lastModified()
            )
        }.sortedByDescending { it.lastModified }
    }

    suspend fun clear(namespace: String) = withContext(Dispatchers.IO) {
        memoryCache.evictAll()
        namespaceDir(namespace).deleteRecursively()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        memoryCache.evictAll()
        root.deleteRecursively()
    }

    fun diskSize(namespace: String): Long = dataFiles(namespace).sumOf { it.length() }

    fun lastModified(namespace: String): Long? =
        dataFiles(namespace).maxOfOrNull { it.lastModified() }?.takeIf { it > 0L }

    private fun dataFiles(namespace: String): List<File> =
        namespaceDir(namespace).listFiles().orEmpty().filter { it.isFile && !it.name.endsWith(".url") }

    private fun namespaceDir(namespace: String): File = File(root, namespace.safeNamespace()).also { it.mkdirs() }

    private fun fileFor(url: String, namespace: String): File {
        val extension = MimeTypeMap.getFileExtensionFromUrl(url).takeIf { it.isNotBlank() } ?: "cache"
        return File(namespaceDir(namespace), "${url.sha256()}.$extension")
    }

    private fun dataFile(url: String, namespace: String): File? {
        val file = fileFor(url, namespace)
        if (!file.exists()) {
            download(url, file) ?: return null
            File(file.parentFile, "${file.name}.url").writeText(url)
            trimToCapacity(namespace)
        }
        return file
    }

    private fun download(url: String, destination: File): File? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
        }
        try {
            if (connection.responseCode !in 200..299) return null
            destination.outputStream().use { output ->
                connection.inputStream.use { input -> input.copyTo(output) }
            }
            destination
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun trimToCapacity(namespace: String) {
        val max = capacityFor(namespace)
        var files = dataFiles(namespace).sortedBy { it.lastModified() }
        var size = files.sumOf { it.length() }
        while (size > max && files.isNotEmpty()) {
            val oldest = files.first()
            val length = oldest.length()
            File(oldest.parentFile, "${oldest.name}.url").delete()
            oldest.delete()
            size -= length
            files = files.drop(1)
        }
    }

    private fun capacityFor(namespace: String): Long = when (namespace) {
        WEBSITE_LOGOS -> 50L * 1024L * 1024L
        ARTICLE_COVERS -> 100L * 1024L * 1024L
        IMAGES -> 200L * 1024L * 1024L
        VIDEOS -> 500L * 1024L * 1024L
        AUDIOS -> 200L * 1024L * 1024L
        else -> 100L * 1024L * 1024L
    }

    companion object {
        const val WEBSITE_LOGOS = "websites"
        const val ARTICLE_COVERS = "articles"
        const val IMAGES = "media"
        const val VIDEOS = "videos"
        const val AUDIOS = "audios"
        const val MEMORY_CAPACITY_BYTES = 20 * 1024 * 1024

        val NAMESPACES = listOf(WEBSITE_LOGOS, ARTICLE_COVERS, IMAGES, VIDEOS, AUDIOS)

        fun parsedUrl(raw: String?): Uri? = normalizeMediaUrl(raw)?.let(Uri::parse)
    }
}

fun normalizeMediaUrl(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    return runCatching {
        val uri = URI(trimmed)
        if (uri.scheme == null) return@runCatching null
        val filteredQuery = uri.rawQuery
            ?.split("&")
            ?.filter { part ->
                val name = part.substringBefore("=").lowercase()
                name !in transientQueryParameters
            }
            ?.joinToString("&")
            ?.takeIf { it.isNotBlank() }
        URI(uri.scheme, uri.rawAuthority, uri.rawPath, filteredQuery, uri.rawFragment).toString()
    }.getOrNull() ?: trimmed
}

private fun normalizeUrl(raw: String?): String? = normalizeMediaUrl(raw)

private val transientQueryParameters = setOf("token", "v", "t", "timestamp", "nonce", "_", "sig")

private fun String.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

private fun String.safeNamespace(): String = replace(Regex("[^A-Za-z0-9_-]"), "_")
