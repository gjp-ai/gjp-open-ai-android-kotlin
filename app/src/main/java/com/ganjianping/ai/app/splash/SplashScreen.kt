package com.ganjianping.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Splash screen matching the iOS SplashScreen.swift + SplashModel.swift behavior:
 * 1. Try to refresh settings from API.
 * 2. If it takes > 3s and we have cached settings → proceed anyway.
 * 3. If it fails and we have cached settings → proceed.
 * 4. If no cache and fails/timeouts → show error + retry.
 */
@Composable
fun SplashScreen(app: AppController, onComplete: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var isInitializing by remember { mutableStateOf(true) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(retryTrigger) {
        isInitializing = true
        error = null

        var refreshJob: Job? = null

        refreshJob = launch {
            app.refreshSettings()
        }

        // Timeout after 3 seconds
        launch {
            delay(3_000)
            if (app.settings.isNotEmpty()) {
                // We have cache, proceed without waiting
                refreshJob?.cancel()
            }
        }

        // Wait for refresh to complete (or be cancelled by timeout)
        refreshJob.join()

        isInitializing = false

        if (app.settings.isNotEmpty()) {
            onComplete()
        } else {
            error = app.settingsError ?: l10n("failed", app.language)
        }
    }

    // Animations
    val infiniteTransition = rememberInfiniteTransition(label = "splash")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val spinnerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinner"
    )

    val logoAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(600),
        label = "logoAlpha"
    )

    val brandAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(800, delayMillis = 300),
        label = "brandAlpha"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to backgroundColor,
                    0.6f to backgroundColor,
                    1f to primaryColor.copy(alpha = 0.06f)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(Modifier.weight(1f))

            // App icon with pulsing animation
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "GJP AI",
                modifier = Modifier
                    .size(140.dp)
                    .scale(pulseScale)
                    .alpha(logoAlpha)
                    .clip(RoundedCornerShape(28.dp))
            )

            Spacer(Modifier.height(32.dp))

            AnimatedVisibility(
                visible = error != null,
                enter = slideInVertically { it / 2 } + fadeIn()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 48.dp)
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { retryTrigger += 1 },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) {
                        Text(l10n("retry", app.language))
                    }
                }
            }

            AnimatedVisibility(
                visible = isInitializing && error == null,
                enter = fadeIn()
            ) {
                // Custom circular spinner matching iOS style
                val spinColor = primaryColor
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .drawBehind {
                            // Background circle
                            drawArc(
                                color = spinColor.copy(alpha = 0.08f),
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                                topLeft = Offset.Zero,
                                size = Size(size.width, size.height)
                            )
                            // Spinning arc
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(spinColor, spinColor.copy(alpha = 0.3f))
                                ),
                                startAngle = spinnerAngle,
                                sweepAngle = 108f,
                                useCenter = false,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                                topLeft = Offset.Zero,
                                size = Size(size.width, size.height)
                            )
                        }
                )
            }

            Spacer(Modifier.weight(1f))

            // Brand footer
            Text(
                text = l10n("brandName", app.language),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 4.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .alpha(brandAlpha)
                    .padding(bottom = 24.dp)
            )
        }
    }
}
