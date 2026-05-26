package com.ganjianping.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun CacheScreen(app: AppController) {
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf(0) }
    var contentStats by remember { mutableStateOf<List<ContentCacheStats>>(emptyList()) }
    var mediaStats by remember { mutableStateOf<List<MediaCacheStats>>(emptyList()) }
    var contentDetailKey by remember { mutableStateOf<String?>(null) }
    var mediaDetailNamespace by remember { mutableStateOf<String?>(null) }
    var showSettingsCache by remember { mutableStateOf(false) }

    LaunchedEffect(version) {
        contentStats = app.contentCache.stats(ContentCache.RESOURCE_KEYS)
        mediaStats = app.mediaCache.stats(MediaDiskCache.NAMESPACES)
    }

    contentDetailKey?.let { key ->
        ContentCacheDialog(app = app, resourceKey = key) { contentDetailKey = null }
    }
    mediaDetailNamespace?.let { namespace ->
        MediaCacheDialog(app = app, namespace = namespace) { mediaDetailNamespace = null }
    }
    if (showSettingsCache) {
        JsonDialog(json = app.settingsCacheRaw()) { showSettingsCache = false }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(l10n("cache", app.language), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(l10n("cacheSubtitle", app.language), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            CacheRow(
                title = l10n("settings", app.language),
                subtitle = "${app.settings.size} rows • ${formatBytes(app.settingsCacheBytes())} • ${formatDate(app.settingsCacheTimestamp())}",
                onOpen = { showSettingsCache = true },
                onClear = {
                    app.clearSettingsCache()
                    version += 1
                }
            )
        }
        item { SectionTitle(l10n("dataCache", app.language)) }
        items(contentStats, key = { it.key }) { stats ->
            CacheRow(
                title = l10n(stats.key, app.language),
                subtitle = "${stats.count} rows • ${formatBytes(stats.sizeBytes)} • ${formatDate(stats.lastSyncedAt)}",
                onOpen = { contentDetailKey = stats.key },
                onClear = {
                    scope.launch {
                        app.contentCache.clear(stats.key)
                        app.cacheManager.clear("${stats.key}_${app.language.rawValue}")
                        version += 1
                    }
                }
            )
        }
        item { SectionTitle(l10n("mediaCache", app.language)) }
        items(mediaStats, key = { it.namespace }) { stats ->
            CacheRow(
                title = mediaCacheTitle(stats.namespace, app.language),
                subtitle = "${stats.count} files • ${formatBytes(stats.sizeBytes)} • ${formatDate(stats.lastModified)}",
                onOpen = { mediaDetailNamespace = stats.namespace },
                onClear = {
                    scope.launch {
                        app.mediaCache.clear(stats.namespace)
                        version += 1
                    }
                }
            )
        }
        item {
            Button(
                onClick = {
                    scope.launch {
                        app.clearLocalCache()
                        version += 1
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text(l10n("clearAllCache", app.language))
            }
        }
    }
}

@Composable
internal fun CacheRow(title: String, subtitle: String, onOpen: (() -> Unit)?, onClear: () -> Unit) {
    OpenCard {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (onOpen != null) {
                TextButton(onClick = onOpen) { Text("View") }
            }
            TextButton(onClick = onClear) { Text("Clear") }
        }
    }
}

@Composable
internal fun ContentCacheDialog(app: AppController, resourceKey: String, close: () -> Unit) {
    var rows by remember(resourceKey) { mutableStateOf<List<ContentCacheRow>>(emptyList()) }
    var selectedJson by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(resourceKey) { rows = app.contentCache.rows(resourceKey) }
    selectedJson?.let { json ->
        JsonDialog(json = json) { selectedJson = null }
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(l10n(resourceKey, app.language)) },
        text = {
            LazyColumn(Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { "${it.lang.rawValue}:${it.id}" }) { row ->
                    Column(Modifier.fillMaxWidth().clickable { selectedJson = row.json }.padding(vertical = 6.dp)) {
                        Text(row.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${row.lang.rawValue} • order ${row.displayOrder} • ${row.updatedAt} • ${formatBytes(row.sizeBytes)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(row.tags.orEmpty(), color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(l10n("done", app.language)) } }
    )
}

@Composable
internal fun JsonDialog(json: String, close: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text("JSON Data") },
        text = {
            LazyColumn(Modifier.height(380.dp)) {
                item {
                    Text(prettyJson(json), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Done") } }
    )
}

@Composable
internal fun MediaCacheDialog(app: AppController, namespace: String, close: () -> Unit) {
    var files by remember(namespace) { mutableStateOf<List<MediaCacheFile>>(emptyList()) }
    LaunchedEffect(namespace) { files = app.mediaCache.files(namespace) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(mediaCacheTitle(namespace, app.language)) },
        text = {
            LazyColumn(Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(files, key = { it.name }) { file ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RemoteImage(
                            url = file.originalUrl,
                            title = file.name,
                            cacheNamespace = file.namespace,
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(file.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${formatBytes(file.sizeBytes)} • ${formatDate(file.lastModified)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(file.originalUrl.orEmpty(), color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(l10n("done", app.language)) } }
    )
}
