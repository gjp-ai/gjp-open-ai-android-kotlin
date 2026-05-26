package com.ganjianping.ai

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

fun Context.openUrl(url: String?) {
    val uri = url?.let(Uri::parse) ?: return
    startActivity(Intent(Intent.ACTION_VIEW, uri))
}

fun Context.downloadToFiles(url: String?, title: String) {
    val uri = url?.let(Uri::parse) ?: return
    val extension = MimeTypeMap.getFileExtensionFromUrl(url).ifBlank { "bin" }
    val request = DownloadManager.Request(uri)
        .setTitle(title)
        .setDescription("Downloading $title")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "${title.safeFileName()}.$extension")
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
    val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
}

suspend fun Context.saveVideoToMediaLibrary(url: String?, title: String) = withContext(Dispatchers.IO) {
    val source = url ?: return@withContext
    val extension = MimeTypeMap.getFileExtensionFromUrl(source).ifBlank { "mp4" }
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, "${title.safeFileName()}.$extension")
        put(MediaStore.Video.Media.MIME_TYPE, "video/${if (extension == "mov") "quicktime" else extension}")
        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/GJP AI")
    }
    val resolver = contentResolver
    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext
    resolver.openOutputStream(uri)?.use { output ->
        URL(source).openStream().use { input -> input.copyTo(output) }
    }
}

private fun String.safeFileName(): String =
    replace(Regex("[\\\\/:*?\"<>|]"), "-").ifBlank { "download" }
