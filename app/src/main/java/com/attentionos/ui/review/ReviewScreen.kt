package com.attentionos.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.attentionos.data.db.NotificationListItem
import com.attentionos.domain.AttentionPriority
import com.attentionos.ui.MainUiState
import com.attentionos.ui.components.AttentionCard
import com.attentionos.ui.components.CalmMark
import com.attentionos.ui.components.EmptyState
import com.attentionos.ui.components.HSpace
import com.attentionos.ui.components.PriorityChip
import com.attentionos.ui.components.VSpace
import com.attentionos.ui.components.accentForPriorityName
import com.attentionos.ui.home.NotificationRow
import com.attentionos.ui.theme.AttentionTheme
import com.attentionos.ui.theme.Motion
import com.attentionos.ui.theme.PriorityColors
import com.attentionos.ui.theme.Radius
import com.attentionos.ui.theme.Spacing
import com.attentionos.ui.theme.ThemeMode
import com.attentionos.ui.theme.motionEnabled
import com.attentionos.ui.theme.rememberHaptics
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Review.
 *
 * The core loop of the app: judge a notification, teach the model. The previous version made
 * that two stacked buttons — functional, but it made the most-repeated interaction in the
 * product feel like filling in a form.
 *
 * It is now a swipeable card. Right means important, left means it can wait, and the card tracks
 * the finger with colour and rotation so the outcome is obvious before releasing. The buttons
 * stay, because a gesture nobody discovers is not a feature — and because swiping alone is not
 * accessible, the card also exposes explicit accessibility actions.
 */
@Composable
internal fun ActivityScreen(
    state: MainUiState,
    onFeedback: (String, Boolean) -> Unit,
    reviewRequest: Int,
) {
    var filter by rememberSaveable { mutableStateOf(ActivityFilter.ALL) }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    var judgedIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }

    LaunchedEffect(reviewRequest) {
        if (reviewRequest > 0) reviewing = true
    }

    val queue = state.unreviewedEvents.filterNot { it.id in judgedIds }

    BackHandler(enabled = reviewing) { reviewing = false }

    if (reviewing) {
        ReviewSession(
            event = queue.firstOrNull(),
            judged = judgedIds.size,
            onDecide = { event, important ->
                judgedIds = judgedIds + event.id
                onFeedback(event.notificationKey, important)
            },
            onSkip = { event -> judgedIds = judgedIds + event.id },
            onClose = { reviewing = false },
        )
        return
    }

    val visible = when (filter) {
        ActivityFilter.ALL -> state.events
        ActivityFilter.IMPORTANT -> state.events.filter {
            it.priority == AttentionPriority.CRITICAL.name ||
                it.priority == AttentionPriority.HIGH.name
        }
        ActivityFilter.QUIET -> state.events.filter { it.queued }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.screenHorizontal,
            end = Spacing.screenHorizontal,
            bottom = Spacing.bottomBarClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Column(Modifier.statusBarsPadding()) {
                VSpace(Spacing.lg)
                Text("Review", style = MaterialTheme.typography.headlineMedium)
                VSpace(Spacing.xs)
                Text(
                    "See how notifications were handled, and correct anything that feels wrong.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (queue.isNotEmpty()) {
            item {
                AttentionCard(tone = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        "Quick review",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    VSpace(Spacing.xs)
                    Text(
                        text = if (queue.size == 1) {
                            "1 decision is ready for your feedback."
                        } else {
                            "${queue.size} decisions are ready for your feedback."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                    VSpace(Spacing.md)
                    Button(onClick = { reviewing = true }) { Text("Start") }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ActivityFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option.label) },
                        modifier = Modifier.semantics { role = Role.RadioButton },
                    )
                }
            }
        }

        if (visible.isEmpty()) {
            item {
                EmptyState(
                    title = "Nothing here yet",
                    description = "Decisions appear as notifications arrive on your device.",
                )
            }
        } else {
            items(visible, key = { it.id }) { event -> NotificationRow(event) }
        }
    }
}

internal enum class ActivityFilter(val label: String) {
    ALL("All"),
    IMPORTANT("Important"),
    QUIET("Quiet"),
}

/** Full-screen judging session. */
@Composable
private fun ReviewSession(
    event: NotificationListItem?,
    judged: Int,
    onDecide: (NotificationListItem, Boolean) -> Unit,
    onSkip: (NotificationListItem) -> Unit,
    onClose: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screenHorizontal),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Teach what matters", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "$judged reviewed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onClose) { Text("Done") }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = event,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "review-card",
                ) { current ->
                    if (current == null) {
                        SessionComplete(judged, onClose)
                    } else {
                        SwipeableDecisionCard(
                            event = current,
                            onDecide = { important -> onDecide(current, important) },
                        )
                    }
                }
            }

            if (event != null) {
                DecisionButtons(
                    onImportant = { onDecide(event, true) },
                    onCanWait = { onDecide(event, false) },
                    onSkip = { onSkip(event) },
                )
            }
        }
    }
}

/**
 * The card itself.
 *
 * Drag tracks the finger with a slight rotation, and the tint previews the outcome before
 * release. Crossing the commit threshold fires a haptic, so the decision can be felt rather than
 * watched.
 */
@Composable
private fun SwipeableDecisionCard(
    event: NotificationListItem,
    onDecide: (Boolean) -> Unit,
) {
    val enabled = motionEnabled()
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val commitPx = with(density) { COMMIT_THRESHOLD.toPx() }

    var offset by remember(event.id) { mutableStateOf(0f) }
    var crossedThreshold by remember(event.id) { mutableStateOf(false) }

    val animatedOffset by animateFloatAsState(
        targetValue = offset,
        animationSpec = Motion.tracking(enabled),
        label = "card-offset",
    )
    val progress = (animatedOffset / commitPx).coerceIn(-1f, 1f)
    val tint = when {
        progress > 0.05f -> PriorityColors.high.copy(alpha = 0.16f * progress)
        progress < -0.05f -> PriorityColors.low.copy(alpha = 0.16f * -progress)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = animatedOffset
                rotationZ = progress * 6f
            }
            .pointerInput(event.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            offset > commitPx -> onDecide(true)
                            offset < -commitPx -> onDecide(false)
                            else -> offset = 0f
                        }
                        crossedThreshold = false
                    },
                    onDragCancel = {
                        offset = 0f
                        crossedThreshold = false
                    },
                ) { _, dragAmount ->
                    offset += dragAmount
                    val past = abs(offset) > commitPx
                    if (past != crossedThreshold) {
                        crossedThreshold = past
                        if (past) haptics.threshold()
                    }
                }
            }
            // Swiping cannot be the only route: these give screen-reader users the same two
            // choices through an explicit action list.
            .semantics {
                contentDescription = "Notification from ${event.appLabel}. " +
                    "Swipe right if important, left if it can wait."
                customActions = listOf(
                    CustomAccessibilityAction("Mark important") { onDecide(true); true },
                    CustomAccessibilityAction("Mark can wait") { onDecide(false); true },
                )
            },
    ) {
        AttentionCard(tone = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(Spacing.huge)
                        .background(
                            accentForPriorityName(event.priority).copy(alpha = 0.14f),
                            Radius.card,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        event.appLabel.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = accentForPriorityName(event.priority),
                    )
                }
                HSpace(Spacing.md)
                Column(Modifier.weight(1f)) {
                    Text(event.appLabel, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Handled as",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                runCatching { AttentionPriority.valueOf(event.priority) }
                    .getOrNull()
                    ?.let { PriorityChip(it) }
            }

            VSpace(Spacing.lg)
            Text(
                text = event.title ?: "This notification",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            event.message?.let { body ->
                VSpace(Spacing.sm)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            VSpace(Spacing.lg)
            Surface(
                shape = Radius.card,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Spacing.md)) {
                    Text(
                        "WHY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    VSpace(Spacing.xs)
                    Text(event.explanation, style = MaterialTheme.typography.bodyMedium)
                    event.personalProbability?.let { probability ->
                        VSpace(Spacing.sm)
                        Text(
                            text = if (event.personalModelApplied) {
                                "Your preferences applied · " +
                                    "${(probability * 100).roundToInt()}% important"
                            } else {
                                "Personal estimate · ${(probability * 100).roundToInt()}% important"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // Outcome preview drawn over the card as it is dragged.
        Box(
            Modifier
                .matchParentSize()
                .background(tint, Radius.card),
        )
        if (abs(progress) > 0.15f) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(Spacing.xl),
                contentAlignment = if (progress > 0) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Text(
                    text = if (progress > 0) "IMPORTANT" else "CAN WAIT",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (progress > 0) PriorityColors.high else PriorityColors.low,
                    modifier = Modifier.alpha(abs(progress)),
                )
            }
        }
    }
}

@Composable
private fun DecisionButtons(
    onImportant: () -> Unit,
    onCanWait: () -> Unit,
    onSkip: () -> Unit,
) {
    val haptics = rememberHaptics()
    Column(Modifier.padding(vertical = Spacing.lg)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            OutlinedButton(
                onClick = {
                    haptics.confirm()
                    onCanWait()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Can wait")
            }
            Button(
                onClick = {
                    haptics.confirm()
                    onImportant()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("This matters")
            }
        }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Skip") }
    }
}

/** The reward for finishing — where the old flow ended with a plain check icon. */
@Composable
private fun SessionComplete(judged: Int, onClose: () -> Unit) {
    val enabled = motionEnabled()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60)
        shown = true
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn() +
                scaleIn(initialScale = 0.6f, animationSpec = Motion.playful(enabled)),
        ) {
            CalmMark()
        }
        VSpace(Spacing.xl)
        Text("All caught up", style = MaterialTheme.typography.headlineSmall)
        VSpace(Spacing.sm)
        Text(
            text = when (judged) {
                0 -> "Nothing waiting for review right now."
                1 -> "One correction saved. Your helper just got a little sharper."
                else -> "$judged corrections saved. Your helper just got a little sharper."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VSpace(Spacing.xl)
        Button(onClick = onClose) { Text("Done") }
    }
}

private val COMMIT_THRESHOLD = 110.dp

@Preview(name = "Review · light", heightDp = 900)
@Composable
private fun ReviewPreview() {
    AttentionTheme(themeMode = ThemeMode.Light) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ActivityScreen(
                state = MainUiState(isLoading = false),
                onFeedback = { _, _ -> },
                reviewRequest = 0,
            )
        }
    }
}

@Preview(name = "Review · dark", heightDp = 900)
@Composable
private fun ReviewDarkPreview() {
    AttentionTheme(themeMode = ThemeMode.Dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ActivityScreen(
                state = MainUiState(isLoading = false),
                onFeedback = { _, _ -> },
                reviewRequest = 0,
            )
        }
    }
}
