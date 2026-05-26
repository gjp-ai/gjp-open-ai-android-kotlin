package com.ganjianping.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun AnalyticsScreen(app: AppController) {
    var counts by remember { mutableStateOf<Map<String, Int>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(app.language) {
        runCatching {
            ContentCache.RESOURCE_KEYS.associateWith { key ->
                app.contentCache.count(key, app.language)
            }
        }.onSuccess { counts = it }.onFailure { error = it.message }
    }
    when {
        error != null -> ErrorView(error!!, app.language) {}
        counts == null -> LoadingView(app.language)
        else -> LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(l10n("analyticsHeadline", app.language), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(l10n("analyticsSummary", app.language), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                OpenCard {
                    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(l10n("totalItems", app.language), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(counts!!.values.sum().toString(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        }
                        Text("▥", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            items(counts!!.entries.toList()) { (key, count) ->
                val max = counts!!.values.maxOrNull()?.coerceAtLeast(1) ?: 1
                OpenCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(l10n(key, app.language), fontWeight = FontWeight.SemiBold)
                            Text(count.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            Box(Modifier.fillMaxWidth(count.toFloat() / max.toFloat()).height(8.dp).clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                }
            }
        }
    }
}
