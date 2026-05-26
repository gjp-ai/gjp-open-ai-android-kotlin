package com.ganjianping.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun FilesScreen(app: AppController) {
    OpenListScreen(
        app = app,
        cacheKey = "files",
        tagSetting = "file_tags",
        parser = { FileItem.fromJson(it) },
        loader = { updatedAfter -> app.api.allFiles(updatedAfter) },
        row = { item -> FileRow(item) }
    )
}

@Composable
internal fun FileRow(item: FileItem) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(fileIcon(item.name), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, fontWeight = FontWeight.SemiBold)
            Text(item.description.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Tags(item.tags)
        }
        TextButton(onClick = { context.downloadToFiles(item.url, item.name) }) { Text(l10n("download", LanguageCode.EN)) }
    }
}
