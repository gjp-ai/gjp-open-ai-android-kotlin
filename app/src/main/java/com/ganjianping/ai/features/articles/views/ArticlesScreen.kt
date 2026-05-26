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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog as ComposeDialog

@Composable
internal fun ArticlesScreen(app: AppController) {
    var selectedArticle by remember { mutableStateOf<ArticleSummary?>(null) }
    if (selectedArticle != null) {
        ArticleDialog(selectedArticle!!) { selectedArticle = null }
    }
    OpenListScreen(
        app = app,
        cacheKey = "articles",
        tagSetting = "article_tags",
        parser = { ArticleSummary.fromJson(it) },
        loader = { updatedAfter -> app.api.allArticles(updatedAfter) },
        imageUrls = { listOfNotNull(it.coverImageUrl ?: it.coverImageOriginalUrl) },
        row = { item -> ArticleRow(item) { selectedArticle = item } }
    )
}

@Composable
internal fun ArticleRow(item: ArticleSummary, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        RemoteImage(url = item.coverImageUrl, title = item.title, cacheNamespace = MediaDiskCache.ARTICLE_COVERS, modifier = Modifier.size(86.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.summary.orEmpty().stripHtml(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Tags(item.tags)
                Text(item.sourceName.orEmpty(), color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.updatedAt.take(10), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun ArticleDialog(item: ArticleSummary, close: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(item.id) {
        val imageUrls = Regex("<img [^>]*src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            .findAll(item.content.orEmpty())
            .mapNotNull { it.groups[1]?.value }
            .toList()
        context.let { MediaDiskCache(it).prefetch(imageUrls, MediaDiskCache.ARTICLE_COVERS) }
    }
    ComposeDialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { context.openUrl(item.originalUrl) }) { Text(l10n("original", LanguageCode.EN)) }
                    TextButton(onClick = close) { Text(l10n("done", LanguageCode.EN)) }
                }
                HorizontalDivider()
                LazyColumn(Modifier.fillMaxSize()) {
                    if (!item.coverImageUrl.isNullOrBlank()) {
                        item {
                            RemoteImage(
                                url = item.coverImageUrl,
                                title = item.title,
                                cacheNamespace = MediaDiskCache.ARTICLE_COVERS,
                                modifier = Modifier.fillMaxWidth().height(240.dp)
                            )
                        }
                    }
                    item {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(listOfNotNull(item.sourceName, item.updatedAt.take(10)).joinToString(" • "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Tags(item.tags)
                        }
                    }
                    item {
                        HtmlWebView(
                            html = item.content ?: item.summary.orEmpty(),
                            modifier = Modifier.fillMaxWidth().height(620.dp)
                        )
                    }
                }
            }
        }
    }
}
