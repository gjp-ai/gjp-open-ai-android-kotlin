package com.ganjianping.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog as ComposeDialog
import kotlinx.coroutines.launch

@Composable
internal fun ImagesScreen(app: AppController) {
    var selectedImage by remember { mutableStateOf<MediaItem?>(null) }
    var allItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    selectedImage?.let { item ->
        ImagePreviewScreen(item = item, items = allItems, language = app.language) { selectedImage = null }
    }
    OpenListScreen(
        app = app,
        cacheKey = "images",
        tagSetting = "image_tags",
        parser = { MediaItem.fromJson(it) },
        loader = { updatedAfter -> app.api.allImages(updatedAfter) },
        imageUrls = { listOfNotNull(it.thumbnailUrl ?: it.coverImageUrl ?: it.url) },
        onItemsChanged = { allItems = it },
        row = { item -> ImageGridRow(item) { selectedImage = item } }
    )
}

@Composable
internal fun ImageGridRow(item: MediaItem, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp)) {
        RemoteImage(
            url = item.thumbnailUrl ?: item.coverImageUrl ?: item.url,
            title = item.displayTitle,
            cacheNamespace = MediaDiskCache.IMAGES,
            modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.height(8.dp))
        Text(item.displayTitle, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Tags(item.tags)
    }
}

@Composable
internal fun ImagePreviewScreen(item: MediaItem, items: List<MediaItem>, language: LanguageCode, close: () -> Unit) {
    val startIndex = items.indexOfFirst { it.id == item.id }.takeIf { it >= 0 } ?: 0
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { items.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()
    ComposeDialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        enabled = pagerState.currentPage > 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }
                    ) { Text("‹", color = Color.White) }
                    Text(
                        "${pagerState.currentPage + 1} / ${items.size.coerceAtLeast(1)}",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        enabled = pagerState.currentPage < items.lastIndex,
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }
                    ) { Text("›", color = Color.White) }
                    TextButton(onClick = close) { Text(l10n("done", language), color = Color.White) }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    val current = items.getOrNull(page) ?: item
                    ZoomableImage(item = current)
                }
                val current = items.getOrNull(pagerState.currentPage) ?: item
                Column(Modifier.fillMaxWidth().background(Color(0xFF171717)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(current.displayTitle, color = Color.White, fontWeight = FontWeight.SemiBold)
                    if (!current.description.isNullOrBlank()) {
                        Text(current.description.stripHtml(), color = Color.White.copy(alpha = 0.75f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Tags(current.tags)
                        Spacer(Modifier.weight(1f))
                        val context = LocalContext.current
                        TextButton(onClick = { context.openUrl(current.originalUrl ?: current.url) }) {
                            Text(l10n("open", language), color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ZoomableImage(item: MediaItem) {
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    val transformState = rememberTransformableState { zoomChange, _, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(item.id) {
                detectTapGestures(onDoubleTap = {
                    scale = if (scale > 1f) 1f else 2.5f
                })
            }
            .transformable(transformState),
        contentAlignment = Alignment.Center
    ) {
        RemoteImage(
            url = item.url ?: item.coverImageOriginalUrl ?: item.coverImageUrl,
            title = item.displayTitle,
            cacheNamespace = MediaDiskCache.IMAGES,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(scaleX = scale, scaleY = scale)
        )
    }
}
