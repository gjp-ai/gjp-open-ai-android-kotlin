package com.ganjianping.ai

import org.json.JSONObject

enum class LanguageCode(val rawValue: String) {
    EN("EN"),
    ZH("ZH");

    companion object {
        fun from(raw: String?): LanguageCode = entries.firstOrNull { it.rawValue == raw } ?: EN
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AccentChoice { BLUE, PURPLE, GREEN, ORANGE, RED }

enum class SortOrder { DISPLAY_ORDER, ALPHA, RECENT }

data class AppSetting(
    val name: String,
    val value: String,
    val lang: LanguageCode
) {
    fun toJsonObject() = JSONObject().apply {
        put("name", name)
        put("value", value)
        put("lang", lang.rawValue)
    }

    companion object {
        fun fromJson(json: JSONObject) = AppSetting(
            name = json.optString("name"),
            value = json.optString("value"),
            lang = LanguageCode.from(json.optString("lang"))
        )
    }
}

sealed interface OpenItem {
    val id: String
    val tags: String?
    val lang: LanguageCode
    val displayOrder: Int
    val updatedAt: String
    val searchableText: String
    val sortTitle: String
}

data class Website(
    override val id: String,
    val name: String,
    val url: String?,
    val logoUrl: String?,
    val description: String?,
    override val tags: String?,
    override val lang: LanguageCode,
    override val displayOrder: Int,
    override val updatedAt: String
) : OpenItem {
    override val searchableText = listOfNotNull(name, url, description, tags).joinToString(" ")
    override val sortTitle = name

    companion object {
        fun fromJson(json: JSONObject) = Website(
            id = json.optString("id"),
            name = json.optString("name"),
            url = json.nullableString("url"),
            logoUrl = json.nullableString("logoUrl"),
            description = json.nullableString("description"),
            tags = json.nullableString("tags"),
            lang = LanguageCode.from(json.optString("lang")),
            displayOrder = json.optInt("displayOrder"),
            updatedAt = json.optString("updatedAt")
        )
    }
}

data class Question(
    override val id: String,
    val question: String,
    val answer: String,
    override val tags: String?,
    override val lang: LanguageCode,
    override val displayOrder: Int,
    override val updatedAt: String
) : OpenItem {
    override val searchableText = listOfNotNull(question, answer.stripHtml(), tags).joinToString(" ")
    override val sortTitle = question

    companion object {
        fun fromJson(json: JSONObject) = Question(
            id = json.optString("id"),
            question = json.optString("question"),
            answer = json.optString("answer"),
            tags = json.nullableString("tags"),
            lang = LanguageCode.from(json.optString("lang")),
            displayOrder = json.optInt("displayOrder"),
            updatedAt = json.optString("updatedAt")
        )
    }
}

data class ArticleSummary(
    override val id: String,
    val title: String,
    val summary: String?,
    val originalUrl: String?,
    val sourceName: String?,
    val coverImageOriginalUrl: String?,
    val coverImageUrl: String?,
    override val tags: String?,
    override val lang: LanguageCode,
    override val displayOrder: Int,
    override val updatedAt: String,
    val content: String?
) : OpenItem {
    override val searchableText = listOfNotNull(title, summary, content?.stripHtml(), tags).joinToString(" ")
    override val sortTitle = title

    companion object {
        fun fromJson(json: JSONObject) = ArticleSummary(
            id = json.optString("id"),
            title = json.optString("title"),
            summary = json.nullableString("summary"),
            originalUrl = json.nullableString("originalUrl"),
            sourceName = json.nullableString("sourceName"),
            coverImageOriginalUrl = json.nullableString("coverImageOriginalUrl"),
            coverImageUrl = json.nullableString("coverImageUrl"),
            tags = json.nullableString("tags"),
            lang = LanguageCode.from(json.optString("lang")),
            displayOrder = json.optInt("displayOrder"),
            updatedAt = json.optString("updatedAt"),
            content = json.nullableString("content")
        )
    }
}

data class MediaItem(
    override val id: String,
    val name: String?,
    val title: String?,
    val subtitle: String?,
    val description: String?,
    val url: String?,
    val thumbnailUrl: String?,
    val originalUrl: String?,
    val coverImageUrl: String?,
    val coverImageOriginalUrl: String?,
    val altText: String?,
    val captionsUrl: String?,
    override val tags: String?,
    val artist: String?,
    override val lang: LanguageCode,
    override val displayOrder: Int,
    override val updatedAt: String
) : OpenItem {
    val displayTitle: String = title ?: name ?: id
    override val searchableText = listOfNotNull(title, name, description, altText, artist, tags).joinToString(" ")
    override val sortTitle = displayTitle

    companion object {
        fun fromJson(json: JSONObject) = MediaItem(
            id = json.optString("id"),
            name = json.nullableString("name"),
            title = json.nullableString("title"),
            subtitle = json.nullableString("subtitle"),
            description = json.nullableString("description"),
            url = json.nullableString("url"),
            thumbnailUrl = json.nullableString("thumbnailUrl"),
            originalUrl = json.nullableString("originalUrl"),
            coverImageUrl = json.nullableString("coverImageUrl"),
            coverImageOriginalUrl = json.nullableString("coverImageOriginalUrl"),
            altText = json.nullableString("altText"),
            captionsUrl = json.nullableString("captionsUrl"),
            tags = json.nullableString("tags"),
            artist = json.nullableString("artist"),
            lang = LanguageCode.from(json.optString("lang")),
            displayOrder = json.optInt("displayOrder"),
            updatedAt = json.optString("updatedAt")
        )
    }
}

data class FileItem(
    override val id: String,
    val name: String,
    val description: String?,
    val url: String?,
    val originalUrl: String?,
    override val tags: String?,
    override val lang: LanguageCode,
    override val displayOrder: Int,
    override val updatedAt: String
) : OpenItem {
    override val searchableText = listOfNotNull(name, description, tags).joinToString(" ")
    override val sortTitle = name

    companion object {
        fun fromJson(json: JSONObject) = FileItem(
            id = json.optString("id"),
            name = json.optString("name"),
            description = json.nullableString("description"),
            url = json.nullableString("url"),
            originalUrl = json.nullableString("originalUrl"),
            tags = json.nullableString("tags"),
            lang = LanguageCode.from(json.optString("lang")),
            displayOrder = json.optInt("displayOrder"),
            updatedAt = json.optString("updatedAt")
        )
    }
}

fun JSONObject.nullableString(name: String): String? =
    if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotBlank() } else null

fun OpenItem.toJsonObject(): JSONObject = when (this) {
    is Website -> JSONObject().apply {
        put("id", id)
        put("name", name)
        putNullable("url", url)
        putNullable("logoUrl", logoUrl)
        putNullable("description", description)
        putNullable("tags", tags)
        put("lang", lang.rawValue)
        put("displayOrder", displayOrder)
        put("updatedAt", updatedAt)
    }
    is Question -> JSONObject().apply {
        put("id", id)
        put("question", question)
        put("answer", answer)
        putNullable("tags", tags)
        put("lang", lang.rawValue)
        put("displayOrder", displayOrder)
        put("updatedAt", updatedAt)
    }
    is ArticleSummary -> JSONObject().apply {
        put("id", id)
        put("title", title)
        putNullable("summary", summary)
        putNullable("originalUrl", originalUrl)
        putNullable("sourceName", sourceName)
        putNullable("coverImageOriginalUrl", coverImageOriginalUrl)
        putNullable("coverImageUrl", coverImageUrl)
        putNullable("tags", tags)
        put("lang", lang.rawValue)
        put("displayOrder", displayOrder)
        put("updatedAt", updatedAt)
        putNullable("content", content)
    }
    is MediaItem -> JSONObject().apply {
        put("id", id)
        putNullable("name", name)
        putNullable("title", title)
        putNullable("subtitle", subtitle)
        putNullable("description", description)
        putNullable("url", url)
        putNullable("thumbnailUrl", thumbnailUrl)
        putNullable("originalUrl", originalUrl)
        putNullable("coverImageUrl", coverImageUrl)
        putNullable("coverImageOriginalUrl", coverImageOriginalUrl)
        putNullable("altText", altText)
        putNullable("captionsUrl", captionsUrl)
        putNullable("tags", tags)
        putNullable("artist", artist)
        put("lang", lang.rawValue)
        put("displayOrder", displayOrder)
        put("updatedAt", updatedAt)
    }
    is FileItem -> JSONObject().apply {
        put("id", id)
        put("name", name)
        putNullable("description", description)
        putNullable("url", url)
        putNullable("originalUrl", originalUrl)
        putNullable("tags", tags)
        put("lang", lang.rawValue)
        put("displayOrder", displayOrder)
        put("updatedAt", updatedAt)
    }
}

private fun JSONObject.putNullable(name: String, value: String?) {
    if (value == null) {
        put(name, JSONObject.NULL)
    } else {
        put(name, value)
    }
}

fun String.tagList(): List<String> = split(",")
    .map { it.trim() }
    .filter { it.isNotBlank() }

fun String.stripHtml(): String = replace(Regex("<[^>]+>"), " ")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace(Regex("\\s+"), " ")
    .trim()
