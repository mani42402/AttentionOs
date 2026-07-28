package com.attentionos.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.attentionos.data.db.NotificationListItem
import com.attentionos.domain.AttentionPriority
import com.attentionos.ui.MainUiState
import com.attentionos.ui.components.AttentionCard
import com.attentionos.ui.components.EmptyState
import com.attentionos.ui.components.HSpace
import com.attentionos.ui.components.LoadingState
import com.attentionos.ui.components.PriorityChip
import com.attentionos.ui.components.SectionHeading
import com.attentionos.ui.components.StatusDot
import com.attentionos.ui.components.VSpace
import com.attentionos.ui.components.accentForPriorityName
import com.attentionos.ui.theme.AttentionTheme
import com.attentionos.ui.theme.Motion
import com.attentionos.ui.theme.PriorityColors
import com.attentionos.ui.theme.Radius
import com.attentionos.ui.theme.Spacing
import com.attentionos.ui.theme.ThemeMode
import com.attentionos.ui.theme.motionEnabled
import com.attentionos.ui.theme.rememberHaptics
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Home.
 *
 * Rebuilt around a single question — *is my phone being looked after right now?* The previous
 * version led with a full-bleed dark slab that pushed the answer below the fold and left
 * status-bar icons unreadable against it, which is why the old build painted an opaque strip
 * over the status bar on every screen.
 *
 * The hero is now an inset card. Content respects the status bar inset, so system icons sit on
 * the app background where the theme guarantees contrast, and edge-to-edge works as intended
 * instead of being fought.
 */
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
        item { Box(Modifier.statusBarsPadding().height(Spacing.sm)) }

        item {
            ProtectionHero(
                focusMode = state.settings.focusMode,
                hasAccess = hasAccess,
                pilotDay = state.pilotDaysElapsed,
                personalModelActive = state.personalModelActive,
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

        item { TodayCard(state) }

        item {
            AnimatedVisibility(
                visible = state.unreviewedEvents.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                ReviewPrompt(count = state.unreviewedEvents.size, onReview = onSeeActivity)
            }
        }

        item {
            SectionHeading(
                text = "Recent",
                trailing = {
                    if (state.events.isNotEmpty()) {
                        TextButton(onClick = onSeeActivity) { Text("See all") }
                    }
                },
            )
        }

        if (state.events.isEmpty()) {
            item {
                EmptyState(
                    title = "Quiet so far",
                    description = if (hasAccess) {
                        "Decisions appear here as notifications arrive."
                    } else {
                        "Connect notification access and your helper starts working."
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

/**
 * The status card.
 *
 * Its colour carries the state — primary tone when protection is on, neutral when it is off — so
 * "is this working?" is answerable at a glance rather than by reading a toggle label.
 */
@Composable
private fun ProtectionHero(
    focusMode: Boolean,
    hasAccess: Boolean,
    pilotDay: Int,
    personalModelActive: Boolean,
    onFocusChanged: (Boolean) -> Unit,
) {
    val enabled = motionEnabled()
    val haptics = rememberHaptics()
    val active = focusMode && hasAccess

    val container by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = Motion.gentle(enabled),
        label = "hero-container",
    )
    val onContainer by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = Motion.gentle(enabled),
        label = "hero-content",
    )

    Surface(shape = Radius.hero, color = container) {
        Column(Modifier.padding(Spacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(
                    color = if (active) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
                HSpace(Spacing.sm)
                Text(
                    text = when {
                        !hasAccess -> "Needs access"
                        active -> "Protecting your attention"
                        else -> "Standing by"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = onContainer.copy(alpha = 0.75f),
                )
            }

            VSpace(Spacing.md)
            Text(
                text = if (active) {
                    "Your phone is calmer."
                } else {
                    "A calmer phone,\nwhen you're ready."
                },
                style = MaterialTheme.typography.headlineMedium,
                color = onContainer,
            )
            VSpace(Spacing.sm)
            Text(
                text = if (active) {
                    "Only what matters makes a sound. Nothing is hidden or deleted."
                } else {
                    "Turn on Attention Mode and your helper takes over sound and vibration."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer.copy(alpha = 0.78f),
            )

            VSpace(Spacing.lg)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(onContainer.copy(alpha = 0.07f), Radius.card)
                    .toggleable(
                        value = focusMode,
                        role = Role.Switch,
                        enabled = hasAccess,
                        onValueChange = {
                            haptics.confirm()
                            onFocusChanged(it)
                        },
                    )
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Attention Mode",
                        style = MaterialTheme.typography.titleMedium,
                        color = onContainer,
                    )
                    Text(
                        text = when {
                            !hasAccess -> "Connect access first"
                            focusMode -> "On · managing interruptions"
                            else -> "Off · apps behave normally"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainer.copy(alpha = 0.7f),
                    )
                }
                Switch(
                    checked = focusMode,
                    onCheckedChange = null,
                    enabled = hasAccess,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }

            VSpace(Spacing.md)
            Text(
                text = if (personalModelActive) {
                    "Learning from you · personalizing"
                } else {
                    "Getting to know you · day $pilotDay of 7"
                },
                style = MaterialTheme.typography.labelMedium,
                color = onContainer.copy(alpha = 0.65f),
            )
        }
    }
}

/** Today's counts, with a bar that shows the split rather than describing it. */
@Composable
private fun TodayCard(state: MainUiState) {
    AttentionCard {
        Text(
            "TODAY",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        VSpace(Spacing.md)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = state.receivedToday.toString(),
                style = MaterialTheme.typography.displaySmall,
            )
            HSpace(Spacing.sm)
            Text(
                text = if (state.receivedToday == 1) {
                    "notification checked"
                } else {
                    "notifications checked"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        VSpace(Spacing.lg)
        DistributionBar(
            important = state.importantToday,
            quiet = state.queuedToday,
            total = state.receivedToday,
        )

        VSpace(Spacing.lg)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxl)) {
            Metric(state.importantToday, "needed attention", PriorityColors.high)
            Metric(state.queuedToday, "stayed quiet", PriorityColors.low)
        }

        if (state.estimatedMinutesSaved > 0) {
            VSpace(Spacing.md)
            Text(
                "About ${state.estimatedMinutesSaved} min of focus protected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Proportional bar for the day's split.
 *
 * Widths animate so an arriving notification visibly shifts the balance, and the whole bar
 * carries a text description — the shape is the only thing conveying the numbers, so without one
 * it would be invisible to a screen reader.
 */
@Composable
private fun DistributionBar(important: Int, quiet: Int, total: Int) {
    val enabled = motionEnabled()
    val safeTotal = total.coerceAtLeast(1)
    val importantShare by animateFloatAsState(
        targetValue = important.toFloat() / safeTotal,
        animationSpec = Motion.gentle(enabled),
        label = "share-important",
    )
    val quietShare by animateFloatAsState(
        targetValue = quiet.toFloat() / safeTotal,
        animationSpec = Motion.gentle(enabled),
        label = "share-quiet",
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(Spacing.sm)
            .semantics {
                contentDescription = if (total == 0) {
                    "No notifications yet today"
                } else {
                    "$important of $total needed attention, $quiet stayed quiet"
                }
            },
    ) {
        val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
        drawRoundRect(color = track, cornerRadius = radius)
        if (total == 0) return@Canvas
        drawRoundRect(
            color = PriorityColors.high,
            size = androidx.compose.ui.geometry.Size(size.width * importantShare, size.height),
            cornerRadius = radius,
        )
        val quietWidth = size.width * quietShare
        drawRoundRect(
            color = PriorityColors.low,
            topLeft = androidx.compose.ui.geometry.Offset(size.width - quietWidth, 0f),
            size = androidx.compose.ui.geometry.Size(quietWidth, size.height),
            cornerRadius = radius,
        )
    }
}

@Composable
private fun Metric(value: Int, label: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(accent, size = 7.dp)
        HSpace(Spacing.sm)
        Column {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccessPrompt(onOpen: () -> Unit) {
    AttentionCard(tone = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            "Finish setup",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        VSpace(Spacing.xs)
        Text(
            "Allow notification access so your helper can classify alerts on this device. " +
                "Nothing leaves your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
        )
        VSpace(Spacing.md)
        TextButton(onClick = onOpen) { Text("Allow access") }
    }
}

@Composable
private fun ReviewPrompt(count: Int, onReview: () -> Unit) {
    AttentionCard(tone = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            "Teach it what matters",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        VSpace(Spacing.xs)
        Text(
            text = if (count == 1) {
                "1 decision is waiting for your feedback."
            } else {
                "$count decisions are waiting for your feedback."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
        )
        VSpace(Spacing.md)
        TextButton(onClick = onReview) { Text("Start review") }
    }
}

/** One notification in the recent list. */
@Composable
internal fun NotificationRow(event: NotificationListItem, modifier: Modifier = Modifier) {
    val accent = accentForPriorityName(event.priority)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = Radius.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(Spacing.huge)
                    .background(accent.copy(alpha = 0.14f), Radius.card),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = event.appLabel.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
            HSpace(Spacing.md)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = event.appLabel,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    HSpace(Spacing.sm)
                    Text(
                        text = formatTime(event.postedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = event.title ?: event.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HSpace(Spacing.sm)
            runCatching { AttentionPriority.valueOf(event.priority) }
                .getOrNull()
                ?.let { PriorityChip(it) }
        }
    }
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

private fun formatTime(epochMillis: Long): String =
    timeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

private const val RECENT_LIMIT = 5

@Preview(name = "Home · light", heightDp = 900)
@Composable
private fun HomePreview() {
    AttentionTheme(themeMode = ThemeMode.Light) {
        Surface(color = MaterialTheme.colorScheme.background) {
            DashboardScreen(
                state = MainUiState(
                    isLoading = false,
                    receivedToday = 24,
                    importantToday = 5,
                    queuedToday = 12,
                ),
                hasAccess = true,
                onFocusChanged = {},
                onOpenNotificationAccess = {},
                onSeeActivity = {},
            )
        }
    }
}

@Preview(name = "Home · dark", heightDp = 900)
@Composable
private fun HomeDarkPreview() {
    AttentionTheme(themeMode = ThemeMode.Dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            DashboardScreen(
                state = MainUiState(
                    isLoading = false,
                    receivedToday = 24,
                    importantToday = 5,
                    queuedToday = 12,
                ),
                hasAccess = true,
                onFocusChanged = {},
                onOpenNotificationAccess = {},
                onSeeActivity = {},
            )
        }
    }
}
