package com.attentionos.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.attentionos.data.db.NotificationListItem
import com.attentionos.domain.AttentionPriority
import com.attentionos.R
import com.attentionos.ui.MainUiState
import com.attentionos.ui.components.AttentionBrand
import com.attentionos.ui.components.EmptyState
import com.attentionos.ui.components.HSpace
import com.attentionos.ui.components.LoadingState
import com.attentionos.ui.components.OnDeviceBadge
import com.attentionos.ui.components.PriorityChip
import com.attentionos.ui.components.SignalCard
import com.attentionos.ui.components.SignalDot
import com.attentionos.ui.components.SignalEyebrow
import com.attentionos.ui.components.FeatureSurfaceMutedColor
import com.attentionos.ui.components.SignalFeatureSurface
import com.attentionos.ui.components.SignalSectionHeader
import com.attentionos.ui.components.VSpace
import com.attentionos.ui.components.accentForPriorityName
import com.attentionos.ui.theme.AttentionTheme
import com.attentionos.ui.theme.Motion
import com.attentionos.ui.theme.SignalColors
import com.attentionos.ui.theme.Spacing
import com.attentionos.ui.theme.ThemeMode
import com.attentionos.ui.theme.motionEnabled
import com.attentionos.ui.theme.rememberHaptics
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.sin

@Composable
internal fun DashboardScreen(
    state: MainUiState,
    hasAccess: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onSeeActivity: () -> Unit,
) {
    if (state.isLoading) {
        LoadingState(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = Spacing.xxl),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.screenHorizontal,
            end = Spacing.screenHorizontal,
            bottom = Spacing.bottomBarClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item { HomeHeader() }
        item {
            ProtectionHeading(
                enabled = state.settings.focusMode,
                hasAccess = hasAccess,
                onFocusChanged = onFocusChanged,
            )
        }
        item {
            AnimatedVisibility(
                visible = !hasAccess,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                AccessPrompt(onOpenNotificationAccess)
            }
        }
        item { AttentionFlow(state) }
        item { ImpactStrip(state) }

        item {
            AnimatedVisibility(
                visible = state.unreviewedEvents.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                ReviewPrompt(state.unreviewedEvents.size, onSeeActivity)
            }
        }

        item {
            SignalSectionHeader(
                title = stringResource(R.string.home_recent_decisions),
                action = if (state.events.isNotEmpty()) stringResource(R.string.home_see_all) else null,
                onAction = onSeeActivity,
            )
        }

        if (state.events.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.home_quiet_so_far),
                    description = if (hasAccess) {
                        stringResource(R.string.home_decisions_appear_here_as_notifications_arrive)
                    } else {
                        stringResource(R.string.home_connect_notification_access_and_your_helper_can)
                    },
                )
            }
        } else {
            items(state.events.take(RECENT_LIMIT), key = { it.id }) { event ->
                NotificationRow(event)
            }
        }
    }
}

@Composable
private fun HomeHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AttentionBrand(modifier = Modifier.weight(1f))
        OnDeviceBadge()
    }
}

@Composable
private fun ProtectionHeading(
    enabled: Boolean,
    hasAccess: Boolean,
    onFocusChanged: (Boolean) -> Unit,
) {
    val haptics = rememberHaptics()
    Column {
        SignalEyebrow(greeting())
        VSpace(Spacing.sm)
        Text(
            text = when {
                !hasAccess -> stringResource(R.string.home_one_step_from_a_calmer_notification_day)
                enabled -> stringResource(R.string.home_your_attention_is_protected)
                else -> stringResource(R.string.home_your_helper_is_standing_by)
            },
            style = MaterialTheme.typography.displaySmall,
        )
        VSpace(Spacing.lg)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .toggleable(
                    value = enabled,
                    enabled = hasAccess,
                    role = Role.Switch,
                    onValueChange = {
                        haptics.confirm()
                        onFocusChanged(it)
                    },
                )
                .padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SignalDot(
                color = if (enabled && hasAccess) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                size = 12.dp,
            )
            HSpace(Spacing.md)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_attention_mode), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when {
                        !hasAccess -> stringResource(R.string.home_connect_notification_access_first)
                        enabled -> stringResource(R.string.home_on_using_your_priority_preferences)
                        else -> stringResource(R.string.home_off_observing_and_learning_only)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = null,
                enabled = hasAccess,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun AccessPrompt(onOpenNotificationAccess: () -> Unit) {
    SignalCard(
        fill = MaterialTheme.colorScheme.tertiaryContainer,
        border = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f),
    ) {
        Text(
            stringResource(R.string.home_notification_access_is_disconnected),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        VSpace(Spacing.xs)
        Text(
            stringResource(R.string.home_connect_it_in_android_settings_analysis_remains),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.78f),
        )
        VSpace(Spacing.md)
        Button(onClick = onOpenNotificationAccess) { Text(stringResource(R.string.home_connect_access)) }
    }
}

@Composable
private fun AttentionFlow(state: MainUiState) {
    val normal = (state.receivedToday - state.importantToday - state.queuedToday).coerceAtLeast(0)
    SignalFeatureSurface {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SignalEyebrow(
                    stringResource(R.string.home_today_s_flow),
                    color = FeatureSurfaceMutedColor,
                    modifier = Modifier.weight(1f),
                )
                SignalDot(SignalColors.Mint, size = 7.dp)
                HSpace(Spacing.xs)
                Text(
                    stringResource(R.string.home_live),
                    style = MaterialTheme.typography.labelMedium,
                    color = SignalColors.Mint,
                )
            }
            VSpace(Spacing.sm)
            Text(
                state.receivedToday.toString(),
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                stringResource(R.string.home_notifications_checked_today),
                style = MaterialTheme.typography.bodySmall,
                color = FeatureSurfaceMutedColor,
            )
            VSpace(Spacing.lg)

            FlowLane(
                value = state.importantToday,
                total = state.receivedToday,
                label = stringResource(R.string.home_flow_important),
                color = SignalColors.Tangerine,
            )
            VSpace(Spacing.md)
            FlowLane(
                value = normal,
                total = state.receivedToday,
                label = stringResource(R.string.home_flow_normal),
                color = SignalColors.Sun,
            )
            VSpace(Spacing.md)
            FlowLane(
                value = state.queuedToday,
                total = state.receivedToday,
                label = stringResource(R.string.home_flow_quiet),
                color = SignalColors.Mint,
            )
        }
    }
}

/** One node per notification, up to the point where more dots stop being countable. */
private const val MAX_FLOW_NODES = 6
private val ARROW_LENGTH = 12.dp

@Composable
private fun FlowLane(
    value: Int,
    total: Int,
    label: String,
    color: Color,
) {
    val motion = motionEnabled()
    var target by remember { mutableFloatStateOf(if (motion) 0f else 1f) }
    LaunchedEffect(value, motion) { target = 1f }
    val reveal by animateFloatAsState(
        targetValue = target,
        animationSpec = Motion.timed(motion, Motion.SLOW),
        label = "flow-$label",
    )

    val spoken = if (total > 0) {
        stringResource(R.string.home_of_notifications_classified_as, value, total, label.lowercase())
    } else {
        stringResource(R.string.home_no_notifications_classified_as_yet, label.lowercase())
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The lane is the measurement, not decoration: its length is this priority's share of
        // the day and each node is one notification. An empty lane therefore reads empty.
        val share = if (total > 0) value.toFloat() / total else 0f
        val nodes = value.coerceAtMost(MAX_FLOW_NODES)

        // A Canvas draws in physical coordinates, so the lane would keep flowing left-to-right
        // after the surrounding Row has mirrored — pointing away from the count it belongs to.
        val mirror = LocalLayoutDirection.current == LayoutDirection.Rtl

        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .clearAndSetSemantics { }
                .graphicsLayer { scaleX = if (mirror) -1f else 1f },
        ) {
            val mid = size.height / 2f
            val amplitude = size.height * 0.10f
            val trackEnd = size.width - ARROW_LENGTH.toPx()
            fun waveY(progress: Float) = mid + sin(progress * 7.2f) * amplitude

            fun lanePath(from: Float, to: Float): Path = Path().apply {
                moveTo(from * trackEnd, waveY(from))
                val steps = 36
                repeat(steps) { index ->
                    val progress = from + (to - from) * (index + 1) / steps
                    lineTo(progress * trackEnd, waveY(progress))
                }
            }

            // Unfilled remainder, so the lane always has a full-width frame to be read against.
            drawPath(
                lanePath(0f, 1f),
                color.copy(alpha = 0.14f),
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
            )

            val filled = share * reveal
            if (filled > 0f) {
                drawPath(
                    lanePath(0f, filled),
                    color,
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                )
                repeat(nodes) { index ->
                    val progress = filled * (index + 1f) / (nodes + 1f)
                    val center = Offset(progress * trackEnd, waveY(progress))
                    drawCircle(color.copy(alpha = 0.15f), 11.dp.toPx(), center)
                    drawCircle(color, 5.dp.toPx(), center)
                }
            }

            val head = if (filled > 0f) color else color.copy(alpha = 0.14f)
            drawLine(
                head,
                Offset(size.width - ARROW_LENGTH.toPx(), mid - 6.dp.toPx()),
                Offset(size.width, mid),
                2.dp.toPx(),
                StrokeCap.Round,
            )
            drawLine(
                head,
                Offset(size.width - ARROW_LENGTH.toPx(), mid + 6.dp.toPx()),
                Offset(size.width, mid),
                2.dp.toPx(),
                StrokeCap.Round,
            )
        }
        HSpace(Spacing.md)
        Column(
            modifier = Modifier.size(width = 76.dp, height = 48.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = color,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
private fun ImpactStrip(state: MainUiState) {
    SignalCard(contentPadding = PaddingValues(Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShieldGlyph()
            HSpace(Spacing.md)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        state.estimatedMinutesSaved.toString(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    HSpace(Spacing.xs)
                    Text(
                        "min",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                Text(
                    stringResource(R.string.home_estimated_focus_protected_today),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FocusSparkline(state.queuedToday)
        }
        VSpace(Spacing.md)
        Text(
            text = if (state.personalModelActive) {
                stringResource(R.string.home_personal_learning_is_active)
            } else {
                stringResource(R.string.home_learning_safely_day_of_7, state.pilotDaysElapsed.coerceAtMost(7))
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShieldGlyph() {
    Canvas(
        Modifier
            .size(44.dp)
            .clearAndSetSemantics { },
    ) {
        val path = Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.06f)
            lineTo(size.width * 0.86f, size.height * 0.20f)
            lineTo(size.width * 0.82f, size.height * 0.62f)
            cubicTo(
                size.width * 0.76f,
                size.height * 0.82f,
                size.width * 0.61f,
                size.height * 0.91f,
                size.width * 0.5f,
                size.height * 0.96f,
            )
            cubicTo(
                size.width * 0.39f,
                size.height * 0.91f,
                size.width * 0.24f,
                size.height * 0.82f,
                size.width * 0.18f,
                size.height * 0.62f,
            )
            lineTo(size.width * 0.14f, size.height * 0.20f)
            close()
        }
        drawPath(path, SignalColors.Mint.copy(alpha = 0.16f))
        drawPath(path, SignalColors.Mint, style = Stroke(2.dp.toPx()))
        drawLine(
            SignalColors.Mint,
            Offset(size.width * 0.31f, size.height * 0.52f),
            Offset(size.width * 0.45f, size.height * 0.66f),
            2.4.dp.toPx(),
            StrokeCap.Round,
        )
        drawLine(
            SignalColors.Mint,
            Offset(size.width * 0.45f, size.height * 0.66f),
            Offset(size.width * 0.71f, size.height * 0.38f),
            2.4.dp.toPx(),
            StrokeCap.Round,
        )
    }
}

@Composable
private fun FocusSparkline(seed: Int) {
    val line = MaterialTheme.colorScheme.primary
    val trend = stringResource(R.string.home_seven_day_focus_trend)
    val mirror = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(
        modifier = Modifier
            .size(width = 104.dp, height = 44.dp)
            .semantics { contentDescription = trend }
            .graphicsLayer { scaleX = if (mirror) -1f else 1f },
    ) {
        val values = listOf(0.35f, 0.58f, 0.44f, 0.72f, 0.52f, 0.68f, 0.78f)
            .map { (it + (seed % 5) * 0.018f).coerceAtMost(0.92f) }
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / (values.lastIndex.toFloat())
            val y = size.height * (1f - value)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        values.forEachIndexed { index, value ->
            drawCircle(
                line,
                2.5.dp.toPx(),
                Offset(
                    size.width * index / values.lastIndex.toFloat(),
                    size.height * (1f - value),
                ),
            )
        }
    }
}

@Composable
private fun ReviewPrompt(count: Int, onReview: () -> Unit) {
    SignalCard(
        fill = MaterialTheme.colorScheme.secondaryContainer,
        border = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f),
        onClick = onReview,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_help_it_learn_your_choices),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    pluralStringResource(R.plurals.home_ready_for_review, count, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.76f),
                )
            }
            Text(
                stringResource(R.string.home_review),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
internal fun NotificationRow(event: NotificationListItem, modifier: Modifier = Modifier) {
    val priority = runCatching { AttentionPriority.valueOf(event.priority) }
        .getOrDefault(AttentionPriority.SILENT)
    val accent = accentForPriorityName(event.priority)

    Column(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.copy(alpha = 0.32f), MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    event.appLabel.firstOrNull()?.uppercase() ?: "•",
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                )
            }
            HSpace(Spacing.md)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = event.title?.takeIf { it.isNotBlank() } ?: event.appLabel,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    HSpace(Spacing.sm)
                    Text(
                        formatTime(event.postedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                VSpace(2.dp)
                Text(
                    text = event.message?.takeIf { it.isNotBlank() }
                        ?: event.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                VSpace(Spacing.xs)
                PriorityChip(priority)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
    }
}

@Composable
@ReadOnlyComposable
private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> stringResource(R.string.home_good_morning)
    in 12..16 -> stringResource(R.string.home_good_afternoon)
    else -> stringResource(R.string.home_good_evening)
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

private fun formatTime(epochMillis: Long): String =
    timeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

private const val RECENT_LIMIT = 4

@Preview(name = "Home · light", heightDp = 1100)
@Composable
private fun HomePreview() {
    AttentionTheme(themeMode = ThemeMode.Light) {
        com.attentionos.ui.theme.AppCanvas(dark = false) {
            DashboardScreen(
                state = MainUiState(
                    isLoading = false,
                    receivedToday = 24,
                    importantToday = 5,
                    queuedToday = 16,
                ),
                hasAccess = true,
                onFocusChanged = {},
                onOpenNotificationAccess = {},
                onSeeActivity = {},
            )
        }
    }
}

@Preview(name = "Home · dark", heightDp = 1100)
@Composable
private fun HomeDarkPreview() {
    AttentionTheme(themeMode = ThemeMode.Dark) {
        com.attentionos.ui.theme.AppCanvas(dark = true) {
            DashboardScreen(
                state = MainUiState(
                    isLoading = false,
                    receivedToday = 24,
                    importantToday = 5,
                    queuedToday = 16,
                ),
                hasAccess = true,
                onFocusChanged = {},
                onOpenNotificationAccess = {},
                onSeeActivity = {},
            )
        }
    }
}
