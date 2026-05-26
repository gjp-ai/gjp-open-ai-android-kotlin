package com.ganjianping.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun AppConfigScreen(app: AppController) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("App Config", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        DetailRow("Base URL", "https://www.ganjianping.com/api/open")
        DetailRow("Data Folder", "databases/gjp_open_content_cache.db")
        ChoiceRow("List freshness", freshnessLabel(app.listFreshnessMillis)) {
            val options = listOf(5 * 60 * 1000L, 30 * 60 * 1000L, 60 * 60 * 1000L, 24 * 60 * 60 * 1000L)
            val currentIndex = options.indexOf(app.listFreshnessMillis).takeIf { it >= 0 } ?: 1
            app.updateListFreshness(options[(currentIndex + 1) % options.size])
        }
        SectionTitle("Media Cache Policies")
        DetailRow("Websites Capacity", "50 MB")
        DetailRow("Articles Capacity", "100 MB")
        DetailRow("Images / media Capacity", "200 MB")
        DetailRow("Videos Capacity", "500 MB")
        DetailRow("Audio Capacity", "200 MB")
        DetailRow("Memory Capacity", "20 MB")
        MediaDiskCache.NAMESPACES.forEach { namespace ->
            DetailRow("${mediaCacheTitle(namespace, app.language)} Namespace", namespace)
        }
        Text("Changes to cache namespaces or capacities require app code/config reload on Android.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun DetailRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ChoiceRow(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title)
        Text(value, color = MaterialTheme.colorScheme.primary)
    }
}
