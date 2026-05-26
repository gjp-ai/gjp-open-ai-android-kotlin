package com.ganjianping.ai

import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun AudiosScreen(app: AppController) {
    var activeItem by remember { mutableStateOf<MediaItem?>(null) }
    Box {
        OpenListScreen(
            app = app,
            cacheKey = "audios",
            tagSetting = "audio_tags",
            parser = { MediaItem.fromJson(it) },
            loader = { updatedAfter -> app.api.allAudios(updatedAfter) },
            row = { item -> AudioRow(item, isActive = activeItem?.id == item.id) { activeItem = item } }
        )
        activeItem?.let {
            AudioMiniPlayer(
                app = app,
                item = it,
                modifier = Modifier.align(Alignment.BottomCenter),
                close = { activeItem = null }
            )
        }
    }
}

@Composable
internal fun AudioRow(item: MediaItem, isActive: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover image or fallback icon
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .then(
                    if (isActive) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                    else Modifier
                )
        ) {
            RemoteImage(
                url = item.coverImageUrl,
                title = item.displayTitle,
                cacheNamespace = MediaDiskCache.AUDIOS,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.displayTitle,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            if (!item.artist.isNullOrEmpty()) {
                Text(item.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Tags(item.tags)
        }
        Spacer(Modifier.width(8.dp))
        // Play/pause icon
        Text(
            text = if (isActive) "⏸" else "▶",
            style = MaterialTheme.typography.headlineSmall,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun AudioMiniPlayer(app: AppController, item: MediaItem, modifier: Modifier, close: () -> Unit) {
    var playing by remember(item.id) { mutableStateOf(true) }
    var showSubtitle by remember(item.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val player = remember(item.id) {
        MediaPlayer().apply {
            item.url?.let { source ->
                setDataSource(context, android.net.Uri.parse(source))
                setOnPreparedListener { start() }
                prepareAsync()
            }
        }
    }
    DisposableEffect(player) {
        onDispose {
            player.stop()
            player.release()
        }
    }
    val primaryColor = MaterialTheme.colorScheme.primary
    Surface(modifier.fillMaxWidth(), tonalElevation = 8.dp) {
        Column {
            // Top accent line
            Box(Modifier.fillMaxWidth().height(2.dp).background(primaryColor.copy(alpha = 0.6f)))

            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                // Subtitle panel
                AnimatedVisibility(visible = showSubtitle && !item.subtitle.isNullOrEmpty()) {
                    Text(
                        text = item.subtitle.orEmpty().stripHtml(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(10.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Styled play/pause button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(primaryColor, primaryColor.copy(alpha = 0.8f))))
                            .clickable {
                                if (playing) player.pause() else player.start()
                                playing = !playing
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (playing) "⏸" else "▶",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.displayTitle, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!item.artist.isNullOrEmpty()) {
                            Text(item.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                    // Subtitle toggle
                    if (!item.subtitle.isNullOrEmpty()) {
                        TextButton(onClick = { showSubtitle = !showSubtitle }) {
                            Text(
                                "CC",
                                color = if (showSubtitle) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    // Close button
                    TextButton(onClick = close) {
                        Text(l10n("done", app.language))
                    }
                }
            }
        }
    }
}
