package com.attentionos.ui.navigation

import android.provider.Settings
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.attentionos.R
import com.attentionos.ui.theme.LocalMotionEnabled
import com.attentionos.ui.theme.LocalDarkTheme
import com.attentionos.ui.theme.SignalColors
import com.attentionos.ui.theme.rememberHaptics

/**
 * The four top-level tabs.
 *
 * [route] is the navigation-graph identifier; the enum stays the single place that maps a tab
 * to its route so the bottom bar and the graph cannot disagree.
 */
internal enum class AppDestination(
    @StringRes val label: Int,
    val route: String,
) {
    TODAY(R.string.nav_home, "home"),
    ACTIVITY(R.string.nav_review, "review"),
    INSIGHTS(R.string.nav_summary, "insights"),
    SETTINGS(R.string.nav_settings, "settings"),
    ;

    internal companion object {
        fun fromRoute(route: String?): AppDestination =
            entries.firstOrNull { it.route == route } ?: TODAY
    }
}

/**
 * The four tabs as a floating dock, for phones.
 *
 * A rail is used instead on wide windows — see [HelperNavigationRail]. Both render the same
 * [NavigationItem], so the two layouts cannot drift apart visually.
 */
@Composable
internal fun HelperBottomBar(
    selected: AppDestination,
    onSelected: (AppDestination) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 12.dp,
                end = 12.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding() + 8.dp,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(28.dp),
                )
                .padding(horizontal = 6.dp, vertical = 7.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            AppDestination.entries.forEach { destination ->
                NavigationItem(
                    destination = destination,
                    active = destination == selected,
                    onSelected = onSelected,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The same four tabs as a vertical rail, for tablets, foldables and landscape.
 *
 * On a wide window a bottom dock puts the primary controls an arm's length from where the eyes
 * are and wastes the height that the content wants.
 */
@Composable
internal fun HelperNavigationRail(
    selected: AppDestination,
    onSelected: (AppDestination) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
            .width(88.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(28.dp),
            )
            .padding(vertical = 10.dp)
            .selectableGroup(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        AppDestination.entries.forEach { destination ->
            NavigationItem(
                destination = destination,
                active = destination == selected,
                onSelected = onSelected,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NavigationItem(
    destination: AppDestination,
    active: Boolean,
    onSelected: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val enabled = LocalMotionEnabled.current
    val dark = LocalDarkTheme.current
    val activeFill = if (dark) SignalColors.Cream else SignalColors.Ink
    val activeContent = if (dark) SignalColors.Ink else SignalColors.Cream

    val iconContainer by animateColorAsState(
        targetValue = if (active) activeFill else Color.Transparent,
        animationSpec = tween(if (enabled) 240 else 0),
        label = "nav-icon-container",
    )
    // Two different backgrounds, so two different content colours: the glyph sits inside the
    // inverted pill, the label sits outside it on the dock. Using one colour for both painted
    // the active label ink-on-ink in dark and cream-on-cream in light — invisible either way.
    val glyphContent by animateColorAsState(
        targetValue = if (active) activeContent else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(if (enabled) 240 else 0),
        label = "nav-glyph",
    )
    val labelContent by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(if (enabled) 240 else 0),
        label = "nav-label",
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.92f,
        animationSpec = tween(if (enabled) 240 else 0),
        label = "nav-scale",
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .selectable(
                selected = active,
                role = Role.Tab,
                onClick = {
                    haptics.select()
                    onSelected(destination)
                },
            )
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(iconContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // No dot here: the filled pill already carries selection. A dot on the selected tab
            // reads as an unread badge and means nothing of the sort.
            NavigationGlyph(destination, active, glyphContent)
        }
        Text(
            stringResource(destination.label),
            style = MaterialTheme.typography.labelSmall,
            color = labelContent,
            maxLines = 1,
        )
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
                    size = Size(w * 0.80f, h * 0.62f),
                    cornerRadius = CornerRadius(w * 0.12f),
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
