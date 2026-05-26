package com.ganjianping.ai.features.websites.views

import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog as ComposeDialog
import com.ganjianping.ai.AppController
import com.ganjianping.ai.LanguageCode
import com.ganjianping.ai.MediaDiskCache
import com.ganjianping.ai.OpenListScreen
import com.ganjianping.ai.RemoteImage
import com.ganjianping.ai.Tags
import com.ganjianping.ai.Website

import com.ganjianping.ai.features.websites.api.WebsitesApi
import com.ganjianping.ai.features.websites.views.WebsiteCard
import com.ganjianping.ai.features.websites.views.WebsiteBrowserDialog

@Composable
fun WebsitesScreen(app: AppController, showSearch: Boolean = false) {
    var selectedWebsite by remember { mutableStateOf<Website?>(null) }
    
    selectedWebsite?.let { website ->
        WebsiteBrowserDialog(website = website, language = app.language) { selectedWebsite = null }
    }
    
    val configuration = LocalConfiguration.current
    val gridColumns = if (configuration.screenWidthDp < 400) 1 else 2
    
    OpenListScreen(
        app = app,
        cacheKey = "websites",
        tagSetting = "website_tags",
        parser = { Website.fromJson(it) },
        loader = { updatedAfter -> app.websitesApi.allWebsites(updatedAfter) },
        imageUrls = { listOfNotNull(it.logoUrl) },
        gridColumns = gridColumns,
        cardless = true, // We draw our own custom premium card layout!
        showSearch = showSearch,
        row = { item -> 
            WebsiteCard(item = item) { 
                selectedWebsite = item 
            } 
        }
    )
}

@Composable
fun WebsiteCard(item: Website, onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // Elegant Rounded Squircle Logo Container on the left side
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                RemoteImage(
                    url = item.logoUrl,
                    title = item.name,
                    cacheNamespace = MediaDiskCache.WEBSITE_LOGOS,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            // Name and Description Column on the right side
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Website Name
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Website Description
                Text(
                    text = item.description.orEmpty().ifBlank { "No description available" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun WebsiteBrowserDialog(website: Website, language: LanguageCode, close: () -> Unit) {
    val context = LocalContext.current
    val url = remember(website.url) {
        val trimmed = website.url?.trim().orEmpty()
        if (trimmed.isBlank()) null
        else if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) trimmed
        else "https://$trimmed"
    }

    var progress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    ComposeDialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // PREMIUM TOP BAR WITH DOMAIN INFO
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Security / SSL lock icon
                    Text(
                        text = "🔒",
                        modifier = Modifier.padding(end = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    // Domain / Title column
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = website.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        url?.let {
                            Text(
                                text = Uri.parse(it).host ?: it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    // Done Button
                    TextButton(
                        onClick = close,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = if (language == LanguageCode.ZH) "完成" else "Done",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Sleek animated loading progress bar
                AnimatedVisibility(
                    visible = isLoading,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxWidth().height(3.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // WEB VIEW OR EMPTY VIEW
                if (url == null) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (language == LanguageCode.ZH) "无效的链接" else "Invalid URL",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Box(modifier = Modifier.weight(1f)) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { viewContext ->
                                WebView(viewContext).apply {
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                            super.onPageStarted(view, url, favicon)
                                            isLoading = true
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            isLoading = false
                                            canGoBack = canGoBack()
                                            canGoForward = canGoForward()
                                        }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            super.onProgressChanged(view, newProgress)
                                            progress = newProgress / 100f
                                            if (newProgress >= 100) {
                                                isLoading = false
                                            }
                                        }
                                    }
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    settings.supportZoom()
                                    webViewInstance = this
                                    loadUrl(url)
                                }
                            },
                            update = { view ->
                                webViewInstance = view
                            }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                
                // PREMIUM BROWSER TOOLBAR (BOTTOM NAV CONTROLS)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    TextButton(
                        onClick = { webViewInstance?.goBack() },
                        enabled = canGoBack,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "←",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (canGoBack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                    }
                    
                    // Forward button
                    TextButton(
                        onClick = { webViewInstance?.goForward() },
                        enabled = canGoForward,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "→",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (canGoForward) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                    }
                    
                    // Refresh button
                    TextButton(
                        onClick = { webViewInstance?.reload() },
                        enabled = url != null,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "↻",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (url != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                    }
                    
                    // Open in External Browser button
                    TextButton(
                        onClick = {
                            url?.let {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it))
                                context.startActivity(intent)
                            }
                        },
                        enabled = url != null,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "↗",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (url != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                    }
                }
            }
        }
    }
}
