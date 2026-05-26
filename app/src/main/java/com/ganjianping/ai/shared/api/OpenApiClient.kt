package com.ganjianping.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder

sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object InvalidUrl : ApiException("Invalid URL")
    class HttpStatus(val code: Int, val body: String) : ApiException("HTTP $code: $body")
    class Envelope(val code: Int, val apiMessage: String) : ApiException(apiMessage)
    class Decoding(cause: Throwable) : ApiException(cause.message ?: "Decoding failed", cause)
}

class OpenApiClient(
    private val baseUrl: String = "https://www.ganjianping.com/api/open"
) {
    suspend fun appSettings(): List<AppSetting> = fetchArray("app-settings") { AppSetting.fromJson(it) }
    suspend fun allQuestions(updatedAfter: String? = null): List<Question> = fetchAll("questions/all", updatedAfter) { Question.fromJson(it) }
    suspend fun allArticles(updatedAfter: String? = null): List<ArticleSummary> = fetchAll("articles/all", updatedAfter) { ArticleSummary.fromJson(it) }
    suspend fun allImages(updatedAfter: String? = null): List<MediaItem> = fetchAll("images/all", updatedAfter) { MediaItem.fromJson(it) }
    suspend fun allVideos(updatedAfter: String? = null): List<MediaItem> = fetchAll("videos/all", updatedAfter) { MediaItem.fromJson(it) }
    suspend fun allAudios(updatedAfter: String? = null): List<MediaItem> = fetchAll("audios/all", updatedAfter) { MediaItem.fromJson(it) }
    suspend fun allFiles(updatedAfter: String? = null): List<FileItem> = fetchAll("files/all", updatedAfter) { FileItem.fromJson(it) }

    internal suspend fun <T> fetchAll(path: String, updatedAfter: String?, mapper: (JSONObject) -> T): List<T> {
        val query = buildMap {
            put("isActive", "true")
            if (!updatedAfter.isNullOrBlank()) put("updatedAfter", updatedAfter)
        }
        return fetchArray(path, query, mapper)
    }

    private suspend fun <T> fetchArray(
        path: String,
        query: Map<String, String> = emptyMap(),
        mapper: (JSONObject) -> T
    ): List<T> = withContext(Dispatchers.IO) {
        val url = runCatching { buildUrl(path, mapOf("channel" to "AI") + query) }
            .getOrElse { throw ApiException.InvalidUrl }
        val connection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 30_000
                readTimeout = 30_000
            }
        } catch (_: MalformedURLException) {
            throw ApiException.InvalidUrl
        }
        try {
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val body = stream.bufferedReader().use { it.readText() }
            if (connection.responseCode !in 200..299) {
                throw ApiException.HttpStatus(connection.responseCode, body)
            }
            try {
                val envelope = JSONObject(body)
                val status = envelope.optJSONObject("status")
                val statusCode = status?.optInt("code") ?: 200
                if (statusCode >= 400) {
                    throw ApiException.Envelope(statusCode, status?.optString("message") ?: "API error")
                }
                val data = envelope.optJSONArray("data") ?: JSONArray()
                List(data.length()) { index -> mapper(data.getJSONObject(index)) }
            } catch (error: ApiException) {
                throw error
            } catch (error: Exception) {
                throw ApiException.Decoding(error)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun buildUrl(path: String, query: Map<String, String>): String {
        val separator = if (baseUrl.endsWith("/")) "" else "/"
        val queryString = query.entries.joinToString("&") { (key, value) ->
            "${key.encode()}=${value.encode()}"
        }
        return "$baseUrl$separator$path?$queryString"
    }

    private fun String.encode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
}
