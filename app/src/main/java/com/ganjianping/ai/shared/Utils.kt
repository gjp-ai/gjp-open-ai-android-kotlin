package com.ganjianping.ai

import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

internal val SortOrder.l10nKey: String
    get() = when (this) {
        SortOrder.DISPLAY_ORDER -> "displayOrder"
        SortOrder.ALPHA -> "alpha"
        SortOrder.RECENT -> "recent"
    }

internal fun normalizeInputUrl(url: String?): String? {
    val trimmed = url?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    return if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
        trimmed
    } else {
        "https://$trimmed"
    }
}

internal fun fileIcon(name: String): String = when {
    name.endsWith(".pdf", true) -> "PDF"
    name.endsWith(".doc", true) || name.endsWith(".docx", true) -> "DOC"
    name.endsWith(".xls", true) || name.endsWith(".xlsx", true) || name.endsWith(".csv", true) -> "XLS"
    name.endsWith(".ppt", true) || name.endsWith(".pptx", true) -> "PPT"
    name.endsWith(".zip", true) || name.endsWith(".rar", true) || name.endsWith(".7z", true) -> "ZIP"
    name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) || name.endsWith(".png", true) || name.endsWith(".gif", true) || name.endsWith(".webp", true) -> "IMG"
    name.endsWith(".mp3", true) || name.endsWith(".wav", true) || name.endsWith(".aac", true) || name.endsWith(".m4a", true) -> "AUD"
    name.endsWith(".mp4", true) || name.endsWith(".mov", true) || name.endsWith(".avi", true) -> "VID"
    name.endsWith(".txt", true) || name.endsWith(".md", true) -> "TXT"
    name.endsWith(".json", true) || name.endsWith(".xml", true) -> "{}"
    else -> "FILE"
}

internal fun prettyJson(raw: String): String = runCatching {
    val trimmed = raw.trim()
    if (trimmed.startsWith("[")) JSONArray(trimmed).toString(2) else JSONObject(trimmed).toString(2)
}.getOrDefault(raw)

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes / 1024.0
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024.0
        index += 1
    }
    return "%.1f %s".format(value, units[index])
}

internal fun formatDate(timestamp: Long?): String =
    timestamp?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) } ?: "Never"

internal fun freshnessLabel(milliseconds: Long): String = when (milliseconds) {
    5 * 60 * 1000L -> "5 minutes"
    30 * 60 * 1000L -> "30 minutes"
    60 * 60 * 1000L -> "1 hour"
    24 * 60 * 60 * 1000L -> "24 hours"
    else -> "${milliseconds / 60000} minutes"
}

internal fun mediaCacheTitle(namespace: String, language: LanguageCode): String = when (namespace) {
    MediaDiskCache.WEBSITE_LOGOS -> if (language == LanguageCode.ZH) "网站图标" else "Website Logos"
    MediaDiskCache.ARTICLE_COVERS -> if (language == LanguageCode.ZH) "文章封面" else "Article Covers"
    MediaDiskCache.IMAGES -> l10n("images", language)
    MediaDiskCache.VIDEOS -> l10n("videos", language)
    MediaDiskCache.AUDIOS -> l10n("audios", language)
    else -> namespace
}
