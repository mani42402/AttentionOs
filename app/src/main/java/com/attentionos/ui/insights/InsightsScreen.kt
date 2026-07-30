package com.attentionos.ui.insights

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.attentionos.data.repository.AttentionTestResult
import com.attentionos.R
import com.attentionos.ui.MainUiState
import com.attentionos.ui.components.ErrorState
import com.attentionos.ui.components.HSpace
import com.attentionos.ui.components.OnDeviceBadge
import com.attentionos.ui.components.PriorityChip
import com.attentionos.ui.components.SignalCard
import com.attentionos.ui.components.SignalDot
import com.attentionos.ui.components.SignalEyebrow
import com.attentionos.ui.components.FeatureSurfaceMutedColor
import com.attentionos.ui.components.SignalFeatureSurface
import com.attentionos.ui.components.SignalScreenHeader
import com.attentionos.ui.components.SignalSectionHeader
import com.attentionos.ui.components.VSpace
import com.attentionos.ui.theme.AttentionTheme
import com.attentionos.ui.theme.Motion
import com.attentionos.ui.theme.SignalColors
import com.attentionos.ui.theme.Spacing
import com.attentionos.ui.theme.ThemeMode
import com.attentionos.ui.theme.motionEnabled
import com.attentionos.ui.theme.rememberHaptics
import kotlin.math.roundToInt

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
            SignalScreenHeader(
                title = stringResource(R.string.insights_summary),
                subtitle = stringResource(R.string.insights_what_your_helper_handled_and_how_safely),
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = Spacing.lg),
                trailing = { OnDeviceBadge() },
            )
        }
        item { TodaySummary(state) }
        item { ImpactMetrics(state) }
        item { LearningJourney(state, onReview) }
        item { SafetyPromise() }
        item { SignalSectionHeader(stringResource(R.string.insights_see_the_model_work)) }
        item { TestLab(state, onRunTestLab) }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                SignalDot(MaterialTheme.colorScheme.primary, size = 6.dp)
                HSpace(Spacing.sm)
                Text(
                    stringResource(R.string.insights_computed_only_on_this_device_nothing_was),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TodaySummary(state: MainUiState) {
    val total = state.receivedToday.coerceAtLeast(1)
    val normal = (state.receivedToday - state.importantToday - state.queuedToday).coerceAtLeast(0)
    val important = state.importantToday.toFloat() / total
    val normalFraction = normal.toFloat() / total
    val quiet = state.queuedToday.toFloat() / total

    SignalFeatureSurface {
        Column {
            SignalEyebrow(stringResource(R.string.insights_today_at_a_glance), color = FeatureSurfaceMutedColor)
            VSpace(Spacing.md)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    state.receivedToday.toString(),
                    style = MaterialTheme.typography.displayLarge,
                )
                HSpace(Spacing.sm)
                Text(
                    "checked",
                    style = MaterialTheme.typography.titleMedium,
                    color = FeatureSurfaceMutedColor,
                    modifier = Modifier.padding(bottom = Spacing.md),
                )
            }
            VSpace(Spacing.lg)
            DistributionBar(
                important = important,
                normal = normalFraction,
                quiet = quiet,
                hasData = state.receivedToday > 0,
            )
            VSpace(Spacing.lg)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SummaryLegend(
                    state.importantToday,
                    stringResource(R.string.insights_needed_attention),
                    SignalColors.Tangerine,
                    Modifier.weight(1f),
                )
                SummaryLegend(normal, "normal", SignalColors.Sun, Modifier.weight(1f))
                SummaryLegend(
                    state.queuedToday,
                    stringResource(R.string.insights_stayed_quiet),
                    SignalColors.Mint,
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DistributionBar(
    important: Float,
    normal: Float,
    quiet: Float,
    hasData: Boolean,
) {
    val enabled = motionEnabled()
    val reveal by animateFloatAsState(
        targetValue = if (hasData) 1f else 0f,
        animationSpec = Motion.gentle(enabled),
        label = "summary-distribution",
    )
    val spoken = if (hasData) {
        stringResource(R.string.insights_percent_needed_attention, (important * 100).roundToInt()) +
            stringResource(R.string.insights_percent_stayed_quiet, (quiet * 100).roundToInt())
    } else {
        stringResource(R.string.insights_no_notifications_checked_today)
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .semantics { contentDescription = spoken },
    ) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(SignalColors.Cream.copy(alpha = 0.12f), cornerRadius = radius)
        if (!hasData) return@Canvas

        var x = 0f
        listOf(
            important to SignalColors.Tangerine,
            normal to SignalColors.Sun,
            quiet to SignalColors.Mint,
        ).forEach { (fraction, color) ->
            val width = size.width * fraction.coerceAtLeast(0f) * reveal
            if (width > 0f) {
                drawRoundRect(
                    color,
                    topLeft = Offset(x, 0f),
                    size = Size(width, size.height),
                    cornerRadius = radius,
                )
                x += width
            }
        }
    }
}

@Composable
private fun SummaryLegend(value: Int, label: String, color: Color, modifier: Modifier = Modifier) {
    // "2, needed attention" as one announcement rather than two disconnected fragments.
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall, color = color)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = FeatureSurfaceMutedColor,
        )
    }
}

@Composable
private fun ImpactMetrics(state: MainUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SignalCard(
            modifier = Modifier.weight(1f),
            fill = MaterialTheme.colorScheme.primaryContainer,
            border = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
        ) {
            Text(
                stringResource(R.string.insights_min, state.estimatedMinutesSaved),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            VSpace(Spacing.xs)
            Text(
                stringResource(R.string.insights_focus_protected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.74f),
            )
        }
        SignalCard(
            modifier = Modifier.weight(1f),
            fill = MaterialTheme.colorScheme.tertiaryContainer,
            border = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f),
        ) {
            Text(
                state.averageAnalysisMillis?.let { stringResource(R.string.insights_ms, it.roundToInt()) } ?: stringResource(R.string.insights_local),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            VSpace(Spacing.xs)
            Text(
                if (state.averageAnalysisMillis == null) {
                    stringResource(R.string.insights_analysis_ready)
                } else {
                    stringResource(R.string.insights_average_analysis)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.74f),
            )
        }
    }
}

@Composable
private fun LearningJourney(state: MainUiState, onReview: () -> Unit) {
    val corrections = state.personalizedModel.exampleCount
    val enabled = motionEnabled()
    val progress by animateFloatAsState(
        targetValue = (corrections.toFloat() / TARGET_CORRECTIONS).coerceIn(0f, 1f),
        animationSpec = Motion.gentle(enabled),
        label = "learning-progress",
    )

    SignalCard {
        SignalEyebrow(stringResource(R.string.insights_personal_learning))
        VSpace(Spacing.sm)
        Text(
            text = when {
                state.personalModelActive -> stringResource(R.string.insights_personalized_to_you)
                corrections == 0 -> stringResource(R.string.insights_ready_for_your_first_correction)
                else -> stringResource(R.string.insights_learning_your_rhythm)
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        VSpace(Spacing.sm)
        Text(
            text = when {
                state.personalModelActive ->
                    stringResource(R.string.insights_your_corrections_now_help_rank_notifications_with)
                !state.pilotComplete ->
                    stringResource(R.string.insights_the_model_is_observing_during_its_seven)
                else ->
                    stringResource(R.string.insights_keep_reviewing_decisions_so_the_personal_model)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VSpace(Spacing.lg)
        LearningTrack(progress)
        VSpace(Spacing.sm)
        Text(
            stringResource(R.string.insights_of_corrections, corrections, TARGET_CORRECTIONS),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VSpace(Spacing.md)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Milestone(stringResource(R.string.insights_observing), true)
            Milestone(stringResource(R.string.insights_adapting), progress >= 0.35f)
            Milestone(stringResource(R.string.insights_personalized), state.personalModelActive)
        }
        AnimatedVisibility(
            visible = state.unreviewedEvents.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                VSpace(Spacing.md)
                TextButton(onClick = onReview) {
                    Text(stringResource(R.string.insights_review_waiting, state.unreviewedEvents.size))
                }
            }
        }
    }
}

@Composable
private fun LearningTrack(progress: Float) {
    val fill = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val spoken =
        stringResource(R.string.insights_percent_toward_personalization, (progress * 100).roundToInt())
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(9.dp)
            .semantics {
                contentDescription = spoken
                progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
            },
    ) {
        val gap = 4.dp.toPx()
        val segments = 7
        val width = (size.width - gap * (segments - 1)) / segments
        repeat(segments) { index ->
            val complete = progress * segments > index
            drawRoundRect(
                color = if (complete) fill else track,
                topLeft = Offset(index * (width + gap), 0f),
                size = Size(width, size.height),
                cornerRadius = CornerRadius(size.height / 2f),
            )
        }
    }
}

@Composable
private fun Milestone(label: String, active: Boolean) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(
                    if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    androidx.compose.foundation.shape.CircleShape,
                ),
        )
        HSpace(Spacing.xs)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun SafetyPromise() {
    SignalCard(
        fill = MaterialTheme.colorScheme.secondaryContainer,
        border = MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f),
    ) {
        Text(
            stringResource(R.string.insights_protected_alerts_stay_protected),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        VSpace(Spacing.md)
        listOf(
            stringResource(R.string.insights_security_codes_and_login_alerts),
            stringResource(R.string.insights_bank_and_payment_activity),
            stringResource(R.string.insights_incoming_calls_and_alarms),
        ).forEach { line ->
            Row(
                modifier = Modifier.padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SignalDot(MaterialTheme.colorScheme.secondary, size = 6.dp)
                HSpace(Spacing.sm)
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        VSpace(Spacing.sm)
        Text(
            stringResource(R.string.insights_personalization_can_never_lower_these_deterministic_safety),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun TestLab(state: MainUiState, onRun: () -> Unit) {
    val lab = state.testLab
    val haptics = rememberHaptics()

    SignalCard {
        Text(stringResource(R.string.insights_watch_it_decide), style = MaterialTheme.typography.titleMedium)
        VSpace(Spacing.xs)
        Text(
            stringResource(R.string.insights_runs_five_examples_through_the_real_local),
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
                Text(stringResource(R.string.insights_running))
            } else {
                Text(if (lab.results.isEmpty()) stringResource(R.string.insights_run_the_check) else stringResource(R.string.insights_run_it_again))
            }
        }

        lab.error?.let { message ->
            VSpace(Spacing.md)
            ErrorState(
                title = stringResource(R.string.insights_could_not_run_the_check),
                description = message,
                onRetry = onRun,
                retryLabel = stringResource(R.string.insights_try_again),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(result.name, style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.insights_ms_2, result.durationMillis) +
                    result.category.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (result.safetyProtected) {
            Text(
                stringResource(R.string.insights_protected),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        MaterialTheme.shapes.small,
                    )
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            )
            HSpace(Spacing.sm)
        }
        PriorityChip(result.finalPriority)
    }
}

private const val TARGET_CORRECTIONS = 50

@Preview(name = "Summary · light", heightDp = 1200)
@Composable
private fun SummaryPreview() {
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

@Preview(name = "Summary · dark", heightDp = 1200)
@Composable
private fun SummaryDarkPreview() {
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
