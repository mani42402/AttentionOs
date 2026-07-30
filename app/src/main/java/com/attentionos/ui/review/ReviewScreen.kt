package com.attentionos.ui.review

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.attentionos.data.db.NotificationListItem
import com.attentionos.domain.AttentionPriority
import com.attentionos.R
import com.attentionos.ui.MainUiState
import com.attentionos.ui.components.CalmMark
import com.attentionos.ui.components.EmptyState
import com.attentionos.ui.components.HSpace
import com.attentionos.ui.components.OnDeviceBadge
import com.attentionos.ui.components.PriorityChip
import com.attentionos.ui.components.SignalCard
import com.attentionos.ui.components.SignalEyebrow
import com.attentionos.ui.components.FeatureSurfaceMutedColor
import com.attentionos.ui.components.SignalFeatureSurface
import com.attentionos.ui.components.SignalScreenHeader
import com.attentionos.ui.components.SignalSectionHeader
import com.attentionos.ui.components.VSpace
import com.attentionos.ui.components.accentForPriorityName
import com.attentionos.ui.home.NotificationRow
import com.attentionos.ui.theme.AttentionTheme
import com.attentionos.ui.theme.LocalDarkTheme
import com.attentionos.ui.theme.Motion
import com.attentionos.ui.theme.PriorityColors
import com.attentionos.ui.theme.Radius
import com.attentionos.ui.theme.SignalColors
import com.attentionos.ui.theme.Spacing
import com.attentionos.ui.theme.ThemeMode
import com.attentionos.ui.theme.motionEnabled
import com.attentionos.ui.theme.rememberHaptics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    // A correction is write-once in the repository: `recordAction` ignores a second explicit
    // action on the same notification, and it moves sender memory by an exponential average that
    // has no inverse. So undo cannot mean "reverse it afterwards" — the decision is instead held
    // here for a few seconds and only then committed. Undo simply drops it.
    var pending by remember { mutableStateOf<PendingDecision?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val commit: (PendingDecision) -> Unit = { decision ->
        onFeedback(decision.event.notificationKey, decision.important)
    }

    LaunchedEffect(reviewRequest) {
        if (reviewRequest > 0) reviewing = true
    }

    val queue = state.unreviewedEvents.filterNot { it.id in judgedIds }

    // Leaving by any route commits, including process death mid-session.
    val pendingRef by rememberUpdatedState(pending)
    DisposableEffect(Unit) {
        onDispose { pendingRef?.let(commit) }
    }

    BackHandler(enabled = reviewing) {
        pending?.let(commit)
        pending = null
        reviewing = false
    }

    if (reviewing) {
        val undoLabel = stringResource(R.string.review_undo)
        val savedImportant = stringResource(R.string.review_saved_important)
        val savedCanWait = stringResource(R.string.review_saved_can_wait)
        ReviewSession(
            event = queue.firstOrNull(),
            judged = judgedIds.size,
            remaining = queue.size,
            snackbarHostState = snackbarHostState,
            onDecide = { event, important ->
                // Any decision still waiting is settled before the next one starts, so at most
                // one correction is ever uncommitted.
                pending?.let(commit)
                judgedIds = judgedIds + event.id
                val decision = PendingDecision(event, important)
                pending = decision
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = if (important) savedImportant else savedCanWait,
                        actionLabel = undoLabel,
                        withDismissAction = true,
                        duration = SnackbarDuration.Short,
                    )
                    if (pending !== decision) return@launch
                    pending = null
                    if (result == SnackbarResult.ActionPerformed) {
                        judgedIds = judgedIds - event.id
                    } else {
                        commit(decision)
                    }
                }
            },
            onSkip = { event -> judgedIds = judgedIds + event.id },
            onClose = {
                // Leaving is consent: flush rather than silently discard the correction.
                pending?.let(commit)
                pending = null
                reviewing = false
            },
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
            SignalScreenHeader(
                title = stringResource(R.string.review_review),
                subtitle = stringResource(R.string.review_see_every_decision_and_teach_your_local),
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = Spacing.lg),
                trailing = { OnDeviceBadge() },
            )
        }

        if (queue.isNotEmpty()) {
            item {
                QuickReviewPanel(queue.size) { reviewing = true }
            }
        }

        item {
            SignalSectionHeader(stringResource(R.string.review_decision_history))
            VSpace(Spacing.md)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ActivityFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(stringResource(option.label)) },
                        modifier = Modifier.semantics { role = Role.RadioButton },
                    )
                }
            }
        }

        if (visible.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.review_nothing_here_yet),
                    description = stringResource(R.string.review_decisions_appear_as_notifications_arrive_on_your),
                )
            }
        } else {
            items(visible, key = { it.id }) { event -> NotificationRow(event) }
        }
    }
}

@Composable
private fun QuickReviewPanel(count: Int, onStart: () -> Unit) {
    SignalFeatureSurface {
        Column {
            SignalEyebrow(stringResource(R.string.review_learning_queue), color = SignalColors.Mint)
            VSpace(Spacing.sm)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.displayMedium,
                )
                HSpace(Spacing.sm)
                Text(
                    if (count == 1) stringResource(R.string.review_decision_ready) else stringResource(R.string.review_decisions_ready),
                    style = MaterialTheme.typography.titleMedium,
                    color = FeatureSurfaceMutedColor,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
            }
            VSpace(Spacing.xs)
            Text(
                stringResource(R.string.review_a_short_review_gives_the_personal_model),
                style = MaterialTheme.typography.bodyMedium,
                color = FeatureSurfaceMutedColor,
            )
            VSpace(Spacing.lg)
            Button(onClick = onStart) { Text(stringResource(R.string.review_start_quick_review)) }
        }
    }
}

internal enum class ActivityFilter(@StringRes val label: Int) {
    ALL(R.string.review_all),
    IMPORTANT(R.string.review_important),
    QUIET(R.string.review_quiet),
}

/** Full-screen judging session. */
@Composable
private fun ReviewSession(
    event: NotificationListItem?,
    judged: Int,
    remaining: Int,
    snackbarHostState: SnackbarHostState,
    onDecide: (NotificationListItem, Boolean) -> Unit,
    onSkip: (NotificationListItem) -> Unit,
    onClose: () -> Unit,
) {
    Box {
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
                    SignalEyebrow(stringResource(R.string.review_review_session))
                    VSpace(Spacing.xs)
                    Text(stringResource(R.string.review_teach_what_matters), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.review_reviewed_remaining, judged, remaining),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onClose) { Text(stringResource(R.string.review_done)) }
            }
            ReviewProgress(judged = judged, remaining = remaining)
            VSpace(Spacing.md)

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
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(Spacing.md),
        )
    }
}

/** A correction that has been made but not yet written, so that undo has something to cancel. */
private data class PendingDecision(
    val event: NotificationListItem,
    val important: Boolean,
)

@Composable
private fun ReviewProgress(judged: Int, remaining: Int) {
    val total = (judged + remaining).coerceAtLeast(1)
    val enabled = motionEnabled()
    val progress by animateFloatAsState(
        targetValue = judged.toFloat() / total,
        animationSpec = Motion.gentle(enabled),
        label = "review-progress",
    )
    val spoken = stringResource(R.string.review_progress_description, judged, total)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(Radius.pill)
            .background(MaterialTheme.colorScheme.outlineVariant)
            // Without this a screen reader hears nothing at all: the bar is drawn, not a widget.
            .semantics {
                contentDescription = spoken
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = judged.toFloat(),
                    range = 0f..total.toFloat(),
                    steps = (total - 1).coerceAtLeast(0),
                )
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .background(MaterialTheme.colorScheme.primary, Radius.pill),
        )
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

    var offset by remember(event.id) { mutableFloatStateOf(0f) }
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
    val cardFill = MaterialTheme.colorScheme.inverseSurface
    val cardContent = MaterialTheme.colorScheme.inverseOnSurface
    val personalAccent = if (LocalDarkTheme.current) SignalColors.MintDark else SignalColors.Mint
    val spoken = stringResource(R.string.review_notification_from, event.appLabel) +
        stringResource(R.string.review_swipe_right_if_important_left_if_it)
    val markImportant = stringResource(R.string.review_mark_important)
    val markCanWait = stringResource(R.string.review_mark_can_wait)

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
                contentDescription = spoken
                customActions = listOf(
                    CustomAccessibilityAction(markImportant) { onDecide(true); true },
                    CustomAccessibilityAction(markCanWait) { onDecide(false); true },
                )
            },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = 0.94f
                    scaleY = 0.96f
                    translationY = -18.dp.toPx()
                    alpha = 0.34f
                }
                .background(cardFill, Radius.card),
        )
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = 0.97f
                    scaleY = 0.98f
                    translationY = -9.dp.toPx()
                    alpha = 0.58f
                }
                .background(cardFill, Radius.card),
        )
        Surface(
            shape = Radius.card,
            color = cardFill,
            contentColor = cardContent,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                cardContent.copy(alpha = 0.14f),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(Spacing.xl)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(Spacing.huge)
                            .background(
                                accentForPriorityName(event.priority).copy(alpha = 0.16f),
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
                        Text(
                            event.appLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = cardContent,
                        )
                        Text(
                            stringResource(R.string.review_handled_as),
                            style = MaterialTheme.typography.bodySmall,
                            color = cardContent.copy(alpha = 0.62f),
                        )
                    }
                    runCatching { AttentionPriority.valueOf(event.priority) }
                        .getOrNull()
                        ?.let { PriorityChip(it) }
                }

                VSpace(Spacing.lg)
                Text(
                    text = event.title ?: stringResource(R.string.review_this_notification),
                    style = MaterialTheme.typography.headlineSmall,
                    color = cardContent,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                event.message?.let { body ->
                    VSpace(Spacing.sm)
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cardContent.copy(alpha = 0.72f),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                VSpace(Spacing.lg)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(cardContent.copy(alpha = 0.07f))
                        .border(
                            1.dp,
                            cardContent.copy(alpha = 0.10f),
                            MaterialTheme.shapes.large,
                        )
                        .padding(Spacing.md),
                ) {
                    Text(
                        stringResource(R.string.review_why_this_rank),
                        style = MaterialTheme.typography.labelSmall,
                        color = cardContent.copy(alpha = 0.58f),
                    )
                    VSpace(Spacing.xs)
                    Text(
                        event.explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cardContent,
                    )
                    event.personalProbability?.let { probability ->
                        VSpace(Spacing.sm)
                        Text(
                            text = if (event.personalModelApplied) {
                                stringResource(R.string.review_your_preferences_applied) +
                                    stringResource(R.string.review_important_2, (probability * 100).roundToInt())
                            } else {
                                stringResource(R.string.review_personal_estimate_important, (probability * 100).roundToInt())
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = personalAccent,
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
                // Physical, not layout-relative: the label marks the edge the card has uncovered,
                // and `translationX` is a physical offset. Start/End would put it under the card
                // in RTL.
                contentAlignment = if (progress > 0) {
                    AbsoluteAlignment.CenterLeft
                } else {
                    AbsoluteAlignment.CenterRight
                },
            ) {
                Text(
                    text = if (progress > 0) stringResource(R.string.review_important_3) else stringResource(R.string.review_can_wait),
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
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
            ) {
                Text(stringResource(R.string.review_can_wait_2))
            }
            Button(
                onClick = {
                    haptics.confirm()
                    onImportant()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
            ) {
                Text(stringResource(R.string.review_important))
            }
        }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.review_skip_for_now)) }
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
        Box(contentAlignment = Alignment.Center) {
            // Only a real session earns the burst; arriving at an already-empty queue does not.
            if (judged > 0) CelebrationBurst(play = shown)
            this@Column.AnimatedVisibility(
                visible = shown,
                enter = fadeIn() +
                    scaleIn(initialScale = 0.6f, animationSpec = Motion.playful(enabled)),
            ) {
                CalmMark()
            }
        }
        VSpace(Spacing.xl)
        Text(stringResource(R.string.review_all_caught_up), style = MaterialTheme.typography.headlineSmall)
        VSpace(Spacing.sm)
        Text(
            text = when (judged) {
                0 -> stringResource(R.string.review_nothing_waiting_for_review_right_now)
                1 -> stringResource(R.string.review_one_correction_saved_your_helper_just_got)
                else -> stringResource(R.string.review_corrections_saved_your_helper_just_got_a, judged)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VSpace(Spacing.xl)
        Button(onClick = onClose) { Text(stringResource(R.string.review_done)) }
    }
}

/**
 * The one celebratory moment in the app, drawn rather than shipped as an animation asset.
 *
 * Rays and seeds radiate once and settle — the reward for finishing a review, not an ambient
 * loop. It collapses to nothing when Reduced Motion is on, because a burst is exactly the kind
 * of movement that setting exists to stop.
 */
@Composable
private fun CelebrationBurst(play: Boolean) {
    val enabled = motionEnabled()
    if (!enabled) return

    val progress by animateFloatAsState(
        targetValue = if (play) 1f else 0f,
        animationSpec = Motion.timed(enabled, Motion.SLOW),
        label = "celebration",
    )
    val tangerine = SignalColors.Tangerine
    val mint = SignalColors.Mint
    val sun = SignalColors.Sun

    Canvas(Modifier.size(200.dp)) {
        if (progress <= 0f) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f
        // Fade out over the back half so the burst resolves instead of freezing mid-flight.
        val fade = (1f - progress).coerceIn(0f, 1f).let { if (progress < 0.5f) 1f else it * 2f }

        repeat(RAY_COUNT) { index ->
            val angle = (index.toFloat() / RAY_COUNT) * 2f * PI.toFloat()
            val colour = when (index % 3) {
                0 -> tangerine
                1 -> mint
                else -> sun
            }
            val inner = maxRadius * (0.34f + progress * 0.30f)
            val outer = inner + maxRadius * 0.14f * progress
            val direction = Offset(cos(angle), sin(angle))
            drawLine(
                color = colour.copy(alpha = 0.75f * fade),
                start = center + direction * inner,
                end = center + direction * outer,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = colour.copy(alpha = 0.55f * fade),
                radius = 2.5.dp.toPx(),
                center = center + direction * (outer + maxRadius * 0.10f * progress),
            )
        }
    }
}

private const val RAY_COUNT = 12

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
