package com.ganjianping.ai

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray

class AppController(private val activity: ComponentActivity) {
    private val prefs = activity.getSharedPreferences("gjp-ai", Context.MODE_PRIVATE)
    val api = OpenApiClient()
    val websitesApi = com.ganjianping.ai.features.websites.api.WebsitesApi(api)
    val contentCache = ContentCache(activity)
    val mediaCache = MediaDiskCache(activity)
    val cacheManager = CacheManager(activity)

    var language by mutableStateOf(LanguageCode.from(prefs.getString("language", "EN")))
        private set
    var themeMode by mutableStateOf(ThemeMode.valueOf(prefs.getString("theme", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name))
        private set
    var accentChoice by mutableStateOf(AccentChoice.valueOf(prefs.getString("accent", AccentChoice.BLUE.name) ?: AccentChoice.BLUE.name))
        private set
    var listFreshnessMillis by mutableStateOf(prefs.getLong("listFreshnessMillis", 30 * 60 * 1000L))
        private set
    var settings by mutableStateOf(loadCachedSettings())
    var settingsError by mutableStateOf<String?>(null)
    var backgroundVideoUrl: String? = null
        private set

    val isDarkTheme: Boolean
        @Composable get() = when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

    fun updateLanguage(value: LanguageCode) {
        language = value
        prefs.edit().putString("language", value.rawValue).apply()
    }

    fun updateTheme(value: ThemeMode) {
        themeMode = value
        prefs.edit().putString("theme", value.name).apply()
    }

    fun updateAccent(value: AccentChoice) {
        accentChoice = value
        prefs.edit().putString("accent", value.name).apply()
    }

    fun updateListFreshness(value: Long) {
        listFreshnessMillis = value
        prefs.edit().putLong("listFreshnessMillis", value).apply()
    }

    fun tags(name: String): List<String> = settings.firstOrNull {
        it.name == name && it.lang == language
    }?.value?.replace("\u201C", "")?.replace("\u201D", "")?.tagList().orEmpty()

    fun updateBackgroundVideo(item: MediaItem?) {
        backgroundVideoUrl = item?.url?.takeIf { it.isNotBlank() }
    }

    suspend fun refreshSettings() {
        runCatching { api.appSettings() }
            .onSuccess {
                settings = it
                settingsError = null
                saveSettingsCache(it)
            }
            .onFailure { settingsError = it.message }
    }

    suspend fun clearLocalCache() {
        cacheManager.clearAll()
        contentCache.clearAll()
        mediaCache.clearAll()
        clearSettingsCache()
    }

    fun settingsCacheBytes(): Long = prefs.getString("settingsCache", null)?.length?.toLong() ?: 0L

    fun settingsCacheTimestamp(): Long? = prefs.getLong("settingsCacheTimestamp", 0L).takeIf { it > 0L }

    fun settingsCacheRaw(): String = prefs.getString("settingsCache", null) ?: "No data found."

    private fun saveSettingsCache(items: List<AppSetting>) {
        val json = JSONArray()
        items.forEach { json.put(it.toJsonObject()) }
        prefs.edit()
            .putString("settingsCache", json.toString())
            .putLong("settingsCacheTimestamp", System.currentTimeMillis())
            .apply()
    }

    private fun loadCachedSettings(): List<AppSetting> {
        val raw = prefs.getString("settingsCache", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index -> AppSetting.fromJson(array.getJSONObject(index)) }
        }.getOrDefault(emptyList())
    }

    fun clearSettingsCache() {
        prefs.edit().remove("settingsCache").remove("settingsCacheTimestamp").apply()
        settings = emptyList()
    }
}
