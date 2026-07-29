package com.attentionos.ui.insights

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.attentionos.data.repository.AttentionTestResult
import com.attentionos.ui.MainUiState
import com.attentionos.ui.components.AttentionCard
import com.attentionos.ui.components.ErrorState
import com.attentionos.ui.components.GroupLabel
import com.attentionos.ui.components.HSpace
import com.attentionos.ui.components.PriorityChip
import com.attentionos.ui.components.StatusDot
import com.attentionos.ui.components.VSpace
import com.attentionos.ui.theme.AttentionTheme
import com.attentionos.ui.theme.Motion
import com.attentionos.ui.theme.PriorityColors
import com.attentionos.ui.theme.Radius
import com.attentionos.ui.theme.Spacing
import com.attentionos.ui.theme.ThemeMode
import com.attentionos.ui.theme.motionEnabled
import com.attentionos.ui.theme.rememberHaptics
import kotlin.math.roundToInt

/**
 * Insights.
 *
 * The tab this replaces was a 65-line placeholder repeating a card from Home, while a much
 * richer screen sat in the codebase wired to nothing. This rebuilds that idea properly: what the
 * helper did, how well it is learning, and a way to watch it think.
 *
 * The on-device test moved here from onboarding. Making a five-scenario diagnostic a *gate*
 * before someone could finish setup was backwards — it answers "show me it works", which people
 * want once they are curious, not before they have used the app once.
 */
@Composable
internal fun InsightsScreen(
    state: MainUiState,
    onReview: () -> Unit,
    onRunTestLab: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.screenHorizontal,
            end = Spacing.screenHorizontal,
            bottom = Spacing.bottomBarClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
            Column(Modifier.statusBarsPadding()) {
                VSpace(Spacing.lg)
                Text("Insights", style = MaterialTheme.typography.headlineMedium)
                VSpace(Spacing.xs)
                Text(
                    "What your helper handled, and how well it knows you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { AttentionSplitCard(state) }
        item { LearningJourneyCard(state, onReview) }
        item { ProtectionCard() }
        item { GroupLabel("See it work") }
        item { TestLabCard(state, onRunTestLab) }
        item {
            Text(
                "Everything here was computed on this device. Nothing was uploaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

/** How today's notifications were split, as a ring rather than a row of numbers. */
@Composable
private fun AttentionSplitCard(state: MainUiState) {
    val enabled = motionEnabled()
    val total = state.receivedToday.coerceAtLeast(1)
    val importantFraction by animateFloatAsState(
        targetValue = state.importantToday.toFloat() / total,
        animationSpec = Motion.gentle(enabled),
        label = "ring-important",
    )
    val quietFraction by animateFloatAsState(
        targetValue = state.queuedToday.toFloat() / total,
        animationSpec = Motion.gentle(enabled),
        label = "ring-quiet",
    )

    AttentionCard {
        Text(
            "TODAY",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        VSpace(Spacing.lg)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                AttentionRing(
                    importantFraction = importantFraction,
                    quietFraction = quietFraction,
                    hasData = state.receivedToday > 0,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        state.receivedToday.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        "checked",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HSpace(Spacing.xl)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                LegendRow(PriorityColors.high, state.importantToday, "needed attention")
                LegendRow(PriorityColors.low, state.queuedToday, "stayed quiet")
                if (state.estimatedMinutesSaved > 0) {
                    Text(
                        "≈ ${state.estimatedMinutesSaved} min of focus protected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AttentionRing(importantFraction: Float, quietFraction: Float, hasData: Boolean) {
    val track = Color.White.copy(alpha = 0.16f)
    Canvas(
        modifier = Modifier
            .size(116.dp)
            .semantics {
                contentDescription = if (!hasData) {
                    "No notifications yet today"
                } else {
                    "${(importantFraction * 100).roundToInt()} percent needed attention, " +
                        "${(quietFraction * 100).roundToInt()} percent stayed quiet"
                }
            },
    ) {
        val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
        drawArc(color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = stroke)
        if (!hasData) return@Canvas
        drawArc(
            color = PriorityColors.high,
            startAngle = -90f,
            sweepAngle = 360f * importantFraction,
            useCenter = false,
            style = stroke,
        )
        drawArc(
            color = PriorityColors.low,
            startAngle = -90f + 360f * importantFraction,
            sweepAngle = 360f * quietFraction,
            useCenter = false,
            style = stroke,
        )
    }
}

@Composable
private fun LegendRow(accent: Color, value: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(accent, size = 8.dp)
        HSpace(Spacing.sm)
        Text(value.toString(), style = MaterialTheme.typography.titleMedium)
        HSpace(Spacing.xs)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Learning progress in plain language.
 *
 * Deliberately does not expose the gate arithmetic. Accuracy thresholds and evaluation counts
 * are an implementation concern, and showing them would invite people to optimise a number
 * rather than answer honestly.
 */
@Composable
private fun LearningJourneyCard(state: MainUiState, onReview: () -> Unit) {
    val corrections = state.personalizedModel.exampleCount
    val enabled = motionEnabled()
    val progress by animateFloatAsState(
        targetValue = (corrections.toFloat() / TARGET_CORRECTIONS).coerceIn(0f, 1f),
        animationSpec = Motion.gentle(enabled),
        label = "learning-progress",
    )

    AttentionCard {
        Text(
            "LEARNING",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        VSpace(Spacing.md)
        Text(
            text = when {
                state.personalModelActive -> "Personalized to you"
                corrections == 0 -> "Not learning yet"
                else -> "Getting to know you"
            },
            style = MaterialTheme.typography.titleLarge,
        )
        VSpace(Spacing.xs)
        Text(
            text = when {
                state.personalModelActive ->
                    "Your corrections now shape how notifications are ranked."
                corrections == 0 ->
                    "Mark a few notifications important or not, and your helper starts adapting."
                !state.pilotComplete ->
                    "Watching quietly for the first week so it can check itself before changing anything."
                else ->
                    "A few more corrections and it can start applying what it has learned."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        VSpace(Spacing.lg)
        ProgressTrack(progress)
        VSpace(Spacing.sm)
        Text(
            "$corrections of $TARGET_CORRECTIONS corrections",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AnimatedVisibility(
            visible = state.unreviewedEvents.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                VSpace(Spacing.md)
                TextButton(onClick = onReview) {
                    Text("Review ${state.unreviewedEvents.size} waiting")
                }
            }
        }
    }
}

@Composable
private fun ProgressTrack(progress: Float) {
    val track = Color.White.copy(alpha = 0.16f)
    val fill = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(Spacing.sm)
            .semantics {
                contentDescription =
                    "${(progress * 100).roundToInt()} percent toward personalization"
            },
    ) {
        val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
        drawRoundRect(color = track, cornerRadius = radius)
        if (progress <= 0f) return@Canvas
        drawRoundRect(
            color = fill,
            size = androidx.compose.ui.geometry.Size(size.width * progress, size.height),
            cornerRadius = radius,
        )
    }
}

/** The safety promise, stated plainly. */
@Composable
private fun ProtectionCard() {
    AttentionCard(tone = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            "Always gets through",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        VSpace(Spacing.sm)
        listOf(
            "Security codes and login alerts",
            "Bank and payment activity",
            "Incoming calls and alarms",
        ).forEach { line ->
            Row(
                modifier = Modifier.padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(MaterialTheme.colorScheme.onSecondaryContainer, size = 5.dp)
                HSpace(Spacing.sm)
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f),
                )
            }
        }
        VSpace(Spacing.sm)
        Text(
            "These are never held back, whatever your helper learns.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
        )
    }
}

/**
 * Run the real classifier over five example notifications.
 *
 * Moved out of onboarding, where it blocked completion. Here it answers "does this actually
 * work?" for someone who is curious, and the timings are real, measured on their device.
 */
@Composable
private fun TestLabCard(state: MainUiState, onRun: () -> Unit) {
    val lab = state.testLab
    val haptics = rememberHaptics()

    AttentionCard {
        Text("Watch it decide", style = MaterialTheme.typography.titleMedium)
        VSpace(Spacing.xs)
        Text(
            "Runs five example notifications through the real on-device model. Nothing is posted " +
                "and nothing is saved.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VSpace(Spacing.md)

        Button(
            onClick = {
                haptics.select()
                onRun()
            },
            enabled = !lab.isRunning,
        ) {
            if (lab.isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                HSpace(Spacing.sm)
                Text("Running…")
            } else {
                Text(if (lab.results.isEmpty()) "Run the check" else "Run it again")
            }
        }

        lab.error?.let { message ->
            VSpace(Spacing.md)
            ErrorState(
                title = "Could not run the check",
                description = message,
                onRetry = onRun,
                retryLabel = "Try again",
            )
        }

        AnimatedVisibility(
            visible = lab.results.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                VSpace(Spacing.md)
                lab.results.forEach { result -> TestResultRow(result) }
            }
        }
    }
}

@Composable
private fun TestResultRow(result: AttentionTestResult) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        shape = Radius.card,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(result.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${result.durationMillis} ms · " +
                        result.category.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (result.safetyProtected) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.secondaryContainer, Radius.pill)
                        .padding(horizontal = Spacing.sm, vertical = 3.dp),
                ) {
                    Text(
                        "protected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                HSpace(Spacing.sm)
            }
            PriorityChip(result.finalPriority)
        }
    }
}

private const val TARGET_CORRECTIONS = 50

@Preview(name = "Insights · light", heightDp = 1100)
@Composable
private fun InsightsPreview() {
    AttentionTheme(themeMode = ThemeMode.Light) {
        Surface(color = MaterialTheme.colorScheme.background) {
            InsightsScreen(
                state = MainUiState(
                    isLoading = false,
                    receivedToday = 32,
                    importantToday = 7,
                    queuedToday = 18,
                ),
                onReview = {},
                onRunTestLab = {},
            )
        }
    }
}

@Preview(name = "Insights · dark", heightDp = 1100)
@Composable
private fun InsightsDarkPreview() {
    AttentionTheme(themeMode = ThemeMode.Dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            InsightsScreen(
                state = MainUiState(
                    isLoading = false,
                    receivedToday = 32,
                    importantToday = 7,
                    queuedToday = 18,
                ),
                onReview = {},
                onRunTestLab = {},
            )
        }
    }
}
