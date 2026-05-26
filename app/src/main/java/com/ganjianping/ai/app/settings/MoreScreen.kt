package com.ganjianping.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal sealed interface MoreRoute {
    data object More : MoreRoute
    data object Analytics : MoreRoute
    data object Videos : MoreRoute
    data object Audios : MoreRoute
    data object Files : MoreRoute
    data object Cache : MoreRoute
    data object AppConfig : MoreRoute
}

@Composable
internal fun MoreScreen(app: AppController, navigate: (MoreRoute) -> Unit) {
    LazyColumn {
        item { SectionTitle(l10n("library", app.language)) }
        item { MoreRow(l10n("analytics", app.language), l10n("analyticsSubtitle", app.language), "▥") { navigate(MoreRoute.Analytics) } }
        item { MoreRow(l10n("videos", app.language), l10n("videosSubtitle", app.language), "▶") { navigate(MoreRoute.Videos) } }
        item { MoreRow(l10n("audios", app.language), l10n("audiosSubtitle", app.language), "♪") { navigate(MoreRoute.Audios) } }
        item { MoreRow(l10n("files", app.language), l10n("filesSubtitle", app.language), "▣") { navigate(MoreRoute.Files) } }
        item { SectionTitle(l10n("settings", app.language)) }
        item {
            MenuChoiceRow(
                title = l10n("language", app.language),
                value = app.language.rawValue,
                options = LanguageCode.entries.map { it.rawValue to it }
            ) { app.updateLanguage(it) }
        }
        item {
            MenuChoiceRow(
                title = l10n("appearance", app.language),
                value = app.themeMode.name.lowercase(),
                options = ThemeMode.entries.map { it.name.lowercase() to it }
            ) { app.updateTheme(it) }
        }
        item {
            MenuChoiceRow(
                title = l10n("accent", app.language),
                value = app.accentChoice.name.lowercase(),
                options = AccentChoice.entries.map { it.name.lowercase() to it }
            ) { app.updateAccent(it) }
        }
        item { MoreRow(l10n("cache", app.language), l10n("clearAllCache", app.language), "⌫") { navigate(MoreRoute.Cache) } }
        item { MoreRow("App Config", "API and cache configuration", "⚙") { navigate(MoreRoute.AppConfig) } }
        item {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(l10n("brandName", app.language), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Text("v1.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun MoreRow(title: String, subtitle: String, mark: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Text(mark, color = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun <T> MenuChoiceRow(title: String, value: String, options: List<Pair<String, T>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = true }.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title)
            Text(value, color = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (label, option) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}
