package com.ganjianping.ai

import android.app.Dialog
import android.view.Window
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

@Composable
internal fun VideosScreen(app: AppController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeId by remember { mutableStateOf<String?>(null) }
    var fullScreenItem by remember { mutableStateOf<MediaItem?>(null) }
    var allItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    var isDownloading by remember { mutableStateOf(false) }

    fullScreenItem?.let { item ->
        FullScreenVideoDialog(item) { fullScreenItem = null }
    }
    alertMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { alertMessage = null },
            title = { Text(l10n("videos", app.language)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { alertMessage = null }) { Text(l10n("done", app.language)) } }
        )
    }

    OpenListScreen(
        app = app,
        cacheKey = "videos",
        tagSetting = "video_tags",
        parser = { MediaItem.fromJson(it) },
        loader = { updatedAfter -> app.api.allVideos(updatedAfter) },
        imageUrls = { listOfNotNull(it.coverImageUrl ?: it.thumbnailUrl) },
        onItemsChanged = { allItems = it },
        cardless = true,
        row = { item ->
            VideoRow(
                item = item,
                isActive = activeId == item.id,
                onPlay = {
                    activeId = item.id
                    app.updateBackgroundVideo(item)
                },
                onEnded = {
                    val next = allItems.dropWhile { it.id != item.id }.drop(1).firstOrNull()
                    activeId = next?.id
                    app.updateBackgroundVideo(next)
                },
                onFullScreen = { fullScreenItem = item },
                isDownloading = isDownloading,
                onDownload = {
                    context.downloadToFiles(item.url, item.displayTitle)
                    alertMessage = l10n("downloadStarted", app.language)
                },
                onSaveToMedia = {
                    scope.launch {
                        isDownloading = true
                        runCatching { context.saveVideoToMediaLibrary(item.url, item.displayTitle) }
                            .onSuccess { alertMessage = l10n("savedToPhotos", app.language) }
                            .onFailure { alertMessage = it.message ?: l10n("failed", app.language) }
                        isDownloading = false
                    }
                }
            )
        }
    )
}

@Composable
internal fun VideoRow(
    item: MediaItem,
    isActive: Boolean,
    onPlay: () -> Unit,
    onEnded: () -> Unit,
    onFullScreen: () -> Unit,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onSaveToMedia: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp).background(MaterialTheme.colorScheme.background)) {
        if (isActive) {
            AndroidVideo(url = item.url, onEnded = onEnded, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clickable(onClick = onPlay)) {
                RemoteImage(url = item.coverImageUrl ?: item.thumbnailUrl, title = item.displayTitle, cacheNamespace = MediaDiskCache.VIDEOS, modifier = Modifier.fillMaxSize())
                Text("▶", color = Color.White, style = MaterialTheme.typography.displayMedium, modifier = Modifier.align(Alignment.Center))
            }
        }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Text("▶", color = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.displayTitle, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(item.artist ?: "GJP AI", item.description?.stripHtml()).joinToString(" • "), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            if (isActive) {
                TextButton(onClick = onFullScreen) { Text("⛶") }
            }
            if (isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            } else {
                MediaMenu(onDownload, onSaveToMedia)
            }
        }
    }
}

@Composable
internal fun MediaMenu(onDownload: () -> Unit, onSaveToMedia: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text("⋮") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(l10n("saveToFiles", LanguageCode.EN)) }, onClick = { expanded = false; onDownload() })
            DropdownMenuItem(text = { Text(l10n("saveToPhotos", LanguageCode.EN)) }, onClick = { expanded = false; onSaveToMedia() })
        }
    }
}

@Composable
internal fun AndroidVideo(url: String?, onEnded: () -> Unit, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { context ->
            VideoView(context).apply {
                setMediaController(MediaController(context).also { it.setAnchorView(this) })
                setOnCompletionListener { onEnded() }
            }
        },
        update = { view ->
            if (!url.isNullOrBlank() && view.tag != url) {
                view.tag = url
                view.setVideoURI(android.net.Uri.parse(url))
                view.start()
            }
        }
    )
}

@Composable
internal fun FullScreenVideoDialog(item: MediaItem, close: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(item.id) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val videoView = VideoView(context).apply {
            setMediaController(MediaController(context).also { it.setAnchorView(this) })
            setVideoURI(android.net.Uri.parse(item.url))
            start()
        }
        dialog.setContentView(videoView)
        dialog.setOnDismissListener { close() }
        dialog.show()
        onDispose {
            videoView.stopPlayback()
            dialog.dismiss()
        }
    }
}
