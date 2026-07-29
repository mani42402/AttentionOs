package com.attentionos.ui.navigation

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.attentionos.ui.theme.LocalMotionEnabled
import com.attentionos.ui.theme.rememberHaptics

/**
 * The four top-level tabs.
 *
 * [route] is the navigation-graph identifier; the enum stays the single place that maps a tab
 * to its route so the bottom bar and the graph cannot disagree.
 */
internal enum class AppDestination(
    val label: String,
    val route: String,
) {
    TODAY("Home", "home"),
    ACTIVITY("Review", "review"),
    INSIGHTS("Insights", "insights"),
    SETTINGS("Settings", "settings"),
    ;

    internal companion object {
        fun fromRoute(route: String?): AppDestination =
            entries.firstOrNull { it.route == route } ?: TODAY
    }
}

/**
 * Floating glass navigation bar.
 *
 * A pill that hovers over the aurora rather than a full-width slab pinned to the edge, matching
 * the navigation references. The active tab is a filled violet pill carrying icon *and* label;
 * inactive tabs show the icon alone, so the current location is obvious without reading.
 */
@Composable
internal fun HelperBottomBar(
    selected: AppDestination,
    onSelected: (AppDestination) -> Unit,
) {
    val haptics = rememberHaptics()
    val enabled = LocalMotionEnabled.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding() + 8.dp,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White.copy(alpha = 0.10f))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.24f),
                            Color.White.copy(alpha = 0.06f),
                        ),
                    ),
                    shape = RoundedCornerShape(percent = 50),
                )
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppDestination.entries.forEach { destination ->
                val active = destination == selected
                val pill by animateColorAsState(
                    targetValue = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    animationSpec = tween(if (enabled) 240 else 0),
                    label = "nav-pill",
                )
                val content by animateColorAsState(
                    targetValue = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    },
                    animationSpec = tween(if (enabled) 240 else 0),
                    label = "nav-content",
                )

                Row(
                    modifier = Modifier
                        .weight(if (active) 1.5f else 1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(pill)
                        .selectable(
                            selected = active,
                            role = Role.Tab,
                            onClick = {
                                haptics.select()
                                onSelected(destination)
                            },
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NavigationGlyph(destination, active, content)
                    // The label appears only on the active tab, which is what keeps a
                    // four-item bar from looking crowded.
                    AnimatedVisibility(visible = active) {
                        Row {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                destination.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = content,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NavigationGlyph(
    destination: AppDestination,
    active: Boolean,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(width = 2.1.dp.toPx(), cap = StrokeCap.Round)
        val w = size.width
        val h = size.height
        when (destination) {
            AppDestination.TODAY -> {
                val roof = Path().apply {
                    moveTo(w * 0.12f, h * 0.47f)
                    lineTo(w * 0.50f, h * 0.15f)
                    lineTo(w * 0.88f, h * 0.47f)
                }
                drawPath(roof, color, style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.22f, h * 0.43f), androidx.compose.ui.geometry.Offset(w * 0.22f, h * 0.86f), stroke.width, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.78f, h * 0.43f), androidx.compose.ui.geometry.Offset(w * 0.78f, h * 0.86f), stroke.width, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.22f, h * 0.86f), androidx.compose.ui.geometry.Offset(w * 0.78f, h * 0.86f), stroke.width, StrokeCap.Round)
            }

            AppDestination.ACTIVITY -> {
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.10f, h * 0.15f),
                    size = androidx.compose.ui.geometry.Size(w * 0.80f, h * 0.62f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f),
                    style = stroke,
                )
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.28f, h * 0.86f), androidx.compose.ui.geometry.Offset(w * 0.43f, h * 0.76f), stroke.width, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.33f, h * 0.38f), androidx.compose.ui.geometry.Offset(w * 0.70f, h * 0.38f), stroke.width, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.33f, h * 0.56f), androidx.compose.ui.geometry.Offset(w * 0.58f, h * 0.56f), stroke.width, StrokeCap.Round)
            }

            AppDestination.INSIGHTS -> {
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.14f, h * 0.84f), androidx.compose.ui.geometry.Offset(w * 0.86f, h * 0.84f), stroke.width, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.25f, h * 0.70f), androidx.compose.ui.geometry.Offset(w * 0.25f, h * 0.53f), stroke.width * 2.2f, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.50f, h * 0.70f), androidx.compose.ui.geometry.Offset(w * 0.50f, h * 0.33f), stroke.width * 2.2f, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.75f, h * 0.70f), androidx.compose.ui.geometry.Offset(w * 0.75f, h * 0.18f), stroke.width * 2.2f, StrokeCap.Round)
            }

            AppDestination.SETTINGS -> {
                listOf(0.25f to 0.66f, 0.50f to 0.34f, 0.75f to 0.60f).forEach {
                    (y, knobX) ->
                    drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.12f, h * y), androidx.compose.ui.geometry.Offset(w * 0.88f, h * y), stroke.width, StrokeCap.Round)
                    drawCircle(color, radius = stroke.width * 1.35f, center = androidx.compose.ui.geometry.Offset(w * knobX, h * y))
                }
            }
        }
    }
}
