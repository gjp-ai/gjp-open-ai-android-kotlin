package com.ganjianping.ai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

class OpenListViewModel<T : OpenItem>(
    private val cacheKey: String,
    private val parser: (JSONObject) -> T,
    private val loader: suspend (String?) -> List<T>,
    private val imageUrls: (T) -> List<String> = { emptyList() }
) {
    var state by mutableStateOf<ScreenState>(ScreenState.Loading)
        private set
    var items by mutableStateOf<List<T>>(emptyList())
        private set
    var totalElements by mutableStateOf(0)
        private set
    var isBackgroundRefreshing by mutableStateOf(false)
        private set
    var searchText by mutableStateOf("")
    var selectedTag by mutableStateOf<String?>(null)
    var sortOrder by mutableStateOf(SortOrder.DISPLAY_ORDER)

    suspend fun load(app: AppController, forceRefresh: Boolean = false) {
        state = ScreenState.Loading
        updateFilteredItems(app)

        val cachedCount = app.contentCache.count(cacheKey, app.language)
        val hasCached = cachedCount > 0
        if (!hasCached) {
            state = ScreenState.Loading
        } else {
            prefetchImages(app, items)
        }

        val shouldRefresh = forceRefresh ||
            !hasCached ||
            !app.contentCache.isFresh(cacheKey, app.language, app.listFreshnessMillis)

        if (shouldRefresh) {
            isBackgroundRefreshing = hasCached
            syncAllFromApi(app, hasCached)
            isBackgroundRefreshing = false
        } else {
            updateFilteredItems(app)
        }
    }

    suspend fun refresh(app: AppController) {
        load(app, forceRefresh = true)
    }

    suspend fun updateFilteredItems(app: AppController) {
        val filtered = app.contentCache.query(
            resourceKey = cacheKey,
            language = app.language,
            parser = parser,
            search = searchText.trim().takeIf { it.isNotBlank() },
            tag = selectedTag,
            sortOrder = sortOrder
        )
        items = filtered
        totalElements = filtered.size
        state = if (filtered.isEmpty()) ScreenState.Empty else ScreenState.Content
    }

    private suspend fun syncAllFromApi(app: AppController, hasCached: Boolean) {
        val updatedAfter = app.contentCache.updatedAfter(cacheKey, app.language)
        runCatching { loader(updatedAfter) }
            .onSuccess { fresh ->
                app.contentCache.save(cacheKey, fresh, replaceExisting = updatedAfter == null)
                app.contentCache.markSynced(cacheKey, app.language)
                updateFilteredItems(app)
                prefetchImages(app, fresh)
            }
            .onFailure { error ->
                if (!hasCached && items.isEmpty()) {
                    state = ScreenState.Error(error.message ?: "Failed to load")
                }
            }
    }

    private suspend fun prefetchImages(app: AppController, source: List<T>) {
        val namespace = mediaNamespaceFor(cacheKey) ?: return
        app.mediaCache.prefetch(source.flatMap(imageUrls), namespace)
    }
}

sealed interface ScreenState {
    data object Loading : ScreenState
    data object Content : ScreenState
    data object Empty : ScreenState
    data class Error(val message: String) : ScreenState
}

fun mediaNamespaceFor(cacheKey: String): String? = when (cacheKey) {
    "websites" -> MediaDiskCache.WEBSITE_LOGOS
    "articles" -> MediaDiskCache.ARTICLE_COVERS
    "images" -> MediaDiskCache.IMAGES
    "videos" -> MediaDiskCache.VIDEOS
    "audios" -> MediaDiskCache.AUDIOS
    else -> null
}
