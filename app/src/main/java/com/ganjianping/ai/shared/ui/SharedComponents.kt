package com.ganjianping.ai

import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T : OpenItem> OpenListScreen(
    app: AppController,
    cacheKey: String,
    tagSetting: String,
    parser: (JSONObject) -> T,
    loader: suspend (String?) -> List<T>,
    imageUrls: (T) -> List<String> = { emptyList() },
    onItemsChanged: (List<T>) -> Unit = {},
    cardless: Boolean = false,
    gridColumns: Int? = null,
    showSearch: Boolean = false,
    row: @Composable (T) -> Unit
) {
    val viewModel = remember(cacheKey) {
        OpenListViewModel(
            cacheKey = cacheKey,
            parser = parser,
            loader = loader,
            imageUrls = imageUrls
        )
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(app.language, cacheKey, app.listFreshnessMillis) { viewModel.load(app) }
    LaunchedEffect(viewModel.items) { onItemsChanged(viewModel.items) }

    fun refresh() {
        scope.launch { viewModel.refresh(app) }
    }

    var pullDistance by remember { mutableFloatStateOf(0f) }

    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = showSearch) {
            TextField(
                value = viewModel.searchText,
                onValueChange = {
                    viewModel.searchText = it
                    scope.launch { viewModel.updateFilteredItems(app) }
                },
                placeholder = { Text(l10n("search", app.language), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                leadingIcon = { Text("🔍", modifier = Modifier.padding(start = 12.dp)) },
                trailingIcon = {
                    if (viewModel.searchText.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                viewModel.searchText = ""
                                scope.launch { viewModel.updateFilteredItems(app) }
                            }
                        ) {
                            Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(99.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                singleLine = true
            )
        }
        FilterBar(
            tags = app.tags(tagSetting),
            selectedTag = viewModel.selectedTag,
            sortOrder = viewModel.sortOrder,
            onTag = {
                viewModel.selectedTag = if (viewModel.selectedTag == it) null else it
                scope.launch { viewModel.updateFilteredItems(app) }
            },
            onSort = {
                viewModel.sortOrder = it
                scope.launch { viewModel.updateFilteredItems(app) }
            },
            language = app.language
        )
        if (viewModel.isBackgroundRefreshing) {
            BackgroundRefreshBanner(app.language)
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(cacheKey, app.language) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, amount ->
                            if (amount > 0) pullDistance += amount
                        },
                        onDragEnd = {
                            if (pullDistance > 120f) refresh()
                            pullDistance = 0f
                        },
                        onDragCancel = { pullDistance = 0f }
                    )
                }
        ) {
            when (val state = viewModel.state) {
                ScreenState.Loading -> LoadingView(app.language)
                is ScreenState.Error -> ErrorView(state.message, app.language) { refresh() }
                ScreenState.Empty -> EmptyView(app.language)
                ScreenState.Content -> {
                    if (gridColumns != null) {
                        LazyVerticalGrid(columns = GridCells.Fixed(gridColumns), modifier = Modifier.fillMaxSize()) {
                            gridItems(viewModel.items, key = { it.id }) { item ->
                                if (cardless) row(item) else OpenCard { row(item) }
                            }
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(viewModel.items, key = { it.id }) { item ->
                                if (cardless) {
                                    row(item)
                                } else {
                                    OpenCard { row(item) }
                                }
                            }
                        }
                    }
                }
            }
            if (pullDistance > 0f) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 2.dp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                ) {
                    Text(
                        l10n("pullToRefresh", app.language),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
internal fun CustomFilterChip(selected: Boolean, text: String, onClick: () -> Unit) {
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor
        )
    }
}

@Composable
internal fun FilterBar(
    tags: List<String>,
    selectedTag: String?,
    sortOrder: SortOrder,
    onTag: (String?) -> Unit,
    onSort: (SortOrder) -> Unit,
    language: LanguageCode
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Horizontally scrolling tags list on the left
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomFilterChip(selected = selectedTag == null, text = l10n("all", language)) { onTag(null) }
            tags.forEach { tag ->
                CustomFilterChip(selected = selectedTag == tag, text = tag) { onTag(tag) }
            }
        }
        
        Spacer(Modifier.width(8.dp))
        
        // Static Sort Icon Button fixed on the right
        SortMenu(sortOrder, language, onSort)
    }
}

@Composable
internal fun SortMenu(sortOrder: SortOrder, language: LanguageCode, onSort: (SortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = l10n("sort", language),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(l10n(order.l10nKey, language) + if (order == sortOrder) " ✓" else "") },
                    onClick = {
                        onSort(order)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
internal fun BackgroundRefreshBanner(language: LanguageCode) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(l10n("refreshing", language), color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
internal fun RemoteImage(url: String?, title: String, cacheNamespace: String = MediaDiskCache.IMAGES, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cache = remember(context) { MediaDiskCache(context) }

    var bitmap by remember(url, cacheNamespace) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember(url, cacheNamespace) { mutableStateOf(true) }

    val normalized = normalizeMediaUrl(url)

    LaunchedEffect(normalized, cacheNamespace) {
        if (normalized == null) {
            isLoading = false
            return@LaunchedEffect
        }
        try {
            val bytes = cache.data(normalized, cacheNamespace)
            if (bytes != null && bytes.isNotEmpty()) {
                // Try decoding as a normal raster image first (PNG, JPG, WEBP)
                val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (decoded != null) {
                    bitmap = decoded
                } else {
                    // BitmapFactory failed — check if this is SVG content
                    val rawString = String(bytes, Charsets.UTF_8)
                    if (rawString.contains("<svg", ignoreCase = true)) {
                        bitmap = renderSvgToBitmap(rawString, targetSize = 256)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (!isLoading) {
            // Gradient monogram fallback
            val gradientBrush = remember(title) {
                val hash = title.hashCode()
                val r1 = (hash and 0xFF0000) shr 16
                val g1 = (hash and 0x00FF00) shr 8
                val b1 = (hash and 0x0000FF)
                val c1 = Color(r1 or 0x50, g1 or 0x40, b1 or 0x60)
                val c2 = Color(b1 or 0x40, r1 or 0x60, g1 or 0x50)
                Brush.linearGradient(colors = listOf(c1, c2))
            }
            Box(
                modifier = Modifier.fillMaxSize().background(gradientBrush),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title.trim().take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

/**
 * Renders an SVG string to a Bitmap using the AndroidSVG library.
 * The SVG is rasterized at [targetSize] x [targetSize] pixels with proper aspect ratio.
 */
private fun renderSvgToBitmap(svgString: String, targetSize: Int = 256): android.graphics.Bitmap? {
    return try {
        val svg = com.caverock.androidsvg.SVG.getFromString(svgString)
        val bmp = android.graphics.Bitmap.createBitmap(targetSize, targetSize, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)

        // Set the SVG document dimensions to match our target to ensure proper scaling
        svg.documentWidth = targetSize.toFloat()
        svg.documentHeight = targetSize.toFloat()

        svg.renderToCanvas(canvas)
        bmp
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
internal fun OpenCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) { content() }
}

@Composable
internal fun Tags(tags: String?) {
    val list = tags?.tagList().orEmpty().take(4)
    if (list.isNotEmpty()) {
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            list.forEach { tag ->
                Text(tag, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
internal fun HtmlWebView(html: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = false
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                loadDataWithBaseURL(null, htmlDocument(html), "text/html", "UTF-8", null)
            }
        },
        update = { view ->
            view.loadDataWithBaseURL(null, htmlDocument(html), "text/html", "UTF-8", null)
        }
    )
}

internal fun htmlDocument(body: String): String = """
    <!doctype html>
    <html>
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
      <style>
        :root { color-scheme: light dark; }
        body { margin: 0; padding: 14px; background: transparent; color: #1c1c1e; font-family: sans-serif; font-size: 16px; line-height: 1.55; overflow-wrap: anywhere; }
        @media (prefers-color-scheme: dark) { body { color: #f2f2f7; } }
        img, video, iframe { max-width: 100%; height: auto; border-radius: 12px; }
        pre, code { white-space: pre-wrap; overflow-wrap: anywhere; }
        a { color: #0a84ff; text-decoration: none; }
      </style>
    </head>
    <body>$body</body>
    </html>
""".trimIndent()

@Composable
internal fun LoadingView(language: LanguageCode) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(l10n("loading", language))
        }
    }
}

@Composable
internal fun ErrorView(message: String, language: LanguageCode, retry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(l10n("failed", language), fontWeight = FontWeight.Bold)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Button(onClick = retry) { Text(l10n("retry", language)) }
    }
}

@Composable
internal fun EmptyView(language: LanguageCode) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(l10n("empty", language), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SectionTitle(title: String) {
    Text(title, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    HorizontalDivider()
}
