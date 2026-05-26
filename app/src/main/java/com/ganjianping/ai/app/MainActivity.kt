package com.ganjianping.ai

import android.app.PictureInPictureParams
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.ganjianping.ai.ui.theme.GJPAITheme

class MainActivity : ComponentActivity() {
    private lateinit var appController: AppController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appController = AppController(this)
        enableEdgeToEdge()
        setContent {
            val app = remember { appController }
            GJPAITheme(darkTheme = app.isDarkTheme) {
                GjpOpenApp(app)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isInPictureInPictureMode && appController.backgroundVideoUrl != null) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }
    }
}

private enum class MainTab(val key: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    WEBSITES("websites", Icons.Default.Language),
    QUESTIONS("questions", Icons.Default.QuestionAnswer),
    ARTICLES("articles", Icons.Default.Description),
    IMAGES("images", Icons.Default.Image),
    MORE("more", Icons.Default.MoreHoriz)
}

@Composable
private fun GjpOpenApp(app: AppController) {
    var showingSplash by remember { mutableStateOf(true) }

    Crossfade(targetState = showingSplash, label = "splash") { isSplash ->
        if (isSplash) {
            SplashScreen(app = app) { showingSplash = false }
        } else {
            MainContent(app)
        }
    }
}

@Composable
private fun MainContent(app: AppController) {
    var selectedTab by remember { mutableStateOf(MainTab.WEBSITES) }
    var moreRoute by remember { mutableStateOf<MoreRoute>(MoreRoute.More) }
    var isSearchActive by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = when (selectedTab) {
                    MainTab.MORE -> when (moreRoute) {
                        MoreRoute.More -> l10n("more", app.language)
                        MoreRoute.Analytics -> l10n("analytics", app.language)
                        MoreRoute.Videos -> l10n("videos", app.language)
                        MoreRoute.Audios -> l10n("audios", app.language)
                        MoreRoute.Files -> l10n("files", app.language)
                        MoreRoute.Cache -> l10n("cache", app.language)
                        MoreRoute.AppConfig -> "App Config"
                    }
                    else -> l10n(selectedTab.key, app.language)
                },
                canGoBack = selectedTab == MainTab.MORE && moreRoute != MoreRoute.More,
                onBack = {
                    moreRoute = MoreRoute.More
                },
                actions = {
                    if (selectedTab == MainTab.WEBSITES) {
                        IconButton(onClick = { isSearchActive = !isSearchActive }) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = {
                            selectedTab = tab
                            isSearchActive = false // Reset search when switching tabs
                            if (tab != MainTab.MORE) moreRoute = MoreRoute.More
                        },
                        icon = { Icon(imageVector = tab.icon, contentDescription = l10n(tab.key, app.language)) },
                        label = { Text(l10n(tab.key, app.language), maxLines = 1) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                MainTab.WEBSITES -> com.ganjianping.ai.features.websites.views.WebsitesScreen(app, isSearchActive)
                MainTab.QUESTIONS -> QuestionsScreen(app)
                MainTab.ARTICLES -> ArticlesScreen(app)
                MainTab.IMAGES -> ImagesScreen(app)
                MainTab.MORE -> when (moreRoute) {
                    MoreRoute.More -> MoreScreen(app) { moreRoute = it }
                    MoreRoute.Analytics -> AnalyticsScreen(app)
                    MoreRoute.Videos -> VideosScreen(app)
                    MoreRoute.Audios -> AudiosScreen(app)
                    MoreRoute.Files -> FilesScreen(app)
                    MoreRoute.Cache -> CacheScreen(app)
                    MoreRoute.AppConfig -> AppConfigScreen(app)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    title: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}
) {
    CenterAlignedTopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (canGoBack) TextButton(onClick = onBack) { Text("Back") }
        },
        actions = actions
    )
}
