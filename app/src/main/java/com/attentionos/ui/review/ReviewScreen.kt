package com.attentionos.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.attentionos.R
import com.attentionos.data.db.NotificationListItem
import com.attentionos.ui.MainUiState
import com.attentionos.ui.components.AmbientBackdrop
import com.attentionos.ui.components.BrandMark
import com.attentionos.ui.components.EmptyActivity
import com.attentionos.ui.components.PriorityPill
import com.attentionos.ui.components.ScreenHeader
import com.attentionos.ui.components.formatEventTime
import com.attentionos.ui.components.priorityColor
import com.attentionos.ui.theme.LocalMotionEnabled
import com.attentionos.ui.theme.Mint500
import com.attentionos.ui.theme.Violet400

internal enum class ActivityFilter(val label: String) {
    ALL("All"),
    IMPORTANT("Important"),
    QUEUED("Quiet"),
}

@Composable
internal fun ActivityScreen(
    state: MainUiState,
    onFeedback: (String, Boolean) -> Unit,
    reviewRequest: Int,
) {
    var filter by rememberSaveable { mutableStateOf(ActivityFilter.ALL) }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    // Saveable: rotating mid-session used to replay notifications already reviewed.
    var skippedIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    LaunchedEffect(reviewRequest) {
        if (reviewRequest > 0) reviewing = true
    }
    val reviewEvents = state.unreviewedEvents.filterNot { it.id in skippedIds }
    // The session is a full-screen takeover, so back should leave it rather than exit the app.
    BackHandler(enabled = reviewing) { reviewing = false }
    if (reviewing) {
        ModernReviewSession(
            event = reviewEvents.firstOrNull(),
            reviewedCount = skippedIds.size,
            remainingToGoal = (50 - state.personalizedModel.exampleCount).coerceAtLeast(0),
            onImportant = { event ->
                skippedIds = skippedIds + event.id
                onFeedback(event.notificationKey, true)
            },
            onNotImportant = { event ->
                skippedIds = skippedIds + event.id
                onFeedback(event.notificationKey, false)
            },
            onSkip = { event -> skippedIds = skippedIds + event.id },
            onDone = { reviewing = false },
        )
        return
    }
    val displayedEvents = when (filter) {
        ActivityFilter.ALL -> state.events
        ActivityFilter.IMPORTANT -> state.events.filter {
            it.priority == "CRITICAL" || it.priority == "HIGH"
        }
        ActivityFilter.QUEUED -> state.events.filter { it.queued }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 18.dp,
            bottom = 28.dp,
        ),
    ) {
        item {
            ScreenHeader(
                eyebrow = stringResource(R.string.review_your_notifications),
                title = stringResource(R.string.review_review),
                description = stringResource(R.string.review_see_how_notifications_were_handled_and_correct),
            )
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActivityFilter.entries.forEach { option ->
                    FilterChip(
                        selected = option == filter,
                        onClick = { filter = option },
                        label = { Text(option.label) },
                    )
                }
            }
            if (state.unreviewedEvents.isNotEmpty()) {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.review_quick_review), style = MaterialTheme.typography.titleMedium)
                            val decisionCount = state.unreviewedEvents.size
                            Text(
                                "$decisionCount ${if (decisionCount == 1) "decision" else "decisions"} ready for feedback",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = { reviewing = true }) { Text(stringResource(R.string.review_review)) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        if (displayedEvents.isEmpty()) {
            item { EmptyActivity(hasAccess = true) }
        } else {
            items(displayedEvents, key = { it.id }) { event ->
                EventRow(
                    event = event,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                    showExplanation = true,
                    onFeedback = onFeedback,
                )
            }
        }
    }
}

@Composable
internal fun ModernReviewSession(
    event: NotificationListItem?,
    reviewedCount: Int,
    remainingToGoal: Int,
    onImportant: (NotificationListItem) -> Unit,
    onNotImportant: (NotificationListItem) -> Unit,
    onSkip: (NotificationListItem) -> Unit,
    onDone: () -> Unit,
) {
    val motionEnabled = LocalMotionEnabled.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AmbientBackdrop()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 18.dp,
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 20.dp,
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark()
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "GUIDED REVIEW",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(R.string.review_teach_what_matters), style = MaterialTheme.typography.headlineMedium)
                }
                TextButton(onClick = onDone) { Text(stringResource(R.string.review_close)) }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$reviewedCount handled",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.width(10.dp))
                LinearProgressIndicator(
                    progress = { ((50 - remainingToGoal) / 50f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(7.dp)
                        .clip(CircleShape),
                    color = Violet400,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "${(50 - remainingToGoal).coerceAtLeast(0)}/50",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))
            AnimatedContent(
                targetState = event,
                transitionSpec = {
                    if (motionEnabled) {
                        (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.97f)) togetherWith
                            fadeOut(tween(140))
                    } else {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    }
                },
                label = "review-card",
                modifier = Modifier.weight(1f),
            ) { current ->
                if (current == null) {
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(30.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(76.dp)
                                    .background(Mint500.copy(alpha = 0.16f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Mint500,
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                            Spacer(Modifier.height(18.dp))
                            Text(stringResource(R.string.review_youre_all_caught_up), style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(7.dp))
                            Text(
                                "There are no more notifications waiting for feedback.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    ReviewDecisionCard(current)
                }
            }
            Spacer(Modifier.height(16.dp))
            if (event == null) {
                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(stringResource(R.string.review_return_to_activity))
                }
            } else {
                DecisionButton(
                    label = stringResource(R.string.review_this_matters),
                    subtitle = stringResource(R.string.review_remember_this_as_important),
                    accent = Violet400,
                    filled = true,
                    onClick = { onImportant(event) },
                )
                Spacer(Modifier.height(10.dp))
                DecisionButton(
                    label = stringResource(R.string.review_this_can_wait),
                    subtitle = stringResource(R.string.review_remember_that_this_can_wait),
                    accent = Mint500,
                    filled = false,
                    onClick = { onNotImportant(event) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(onClick = { onSkip(event) }) { Text(stringResource(R.string.review_skip_this_one)) }
                }
            }
        }
    }
}

@Composable
internal fun ReviewDecisionCard(event: NotificationListItem) {
    val accent = priorityColor(event.priority)
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(54.dp)
                        .background(accent.copy(alpha = 0.13f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        event.appLabel.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = accent,
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        event.appLabel,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        formatEventTime(event.postedAt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PriorityPill(event.priority, accent)
            }
            Spacer(Modifier.height(26.dp))
            Text(
                event.title ?: event.category.lowercase().replaceFirstChar(Char::titlecase),
                style = MaterialTheme.typography.headlineMedium,
            )
            event.message?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            Surface(
                color = accent.copy(alpha = 0.10f),
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "WHY IT WAS HANDLED THIS WAY",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(event.explanation, style = MaterialTheme.typography.bodyMedium)
                    event.personalProbability?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Personal estimate · ${(it * 100).toInt()}% likely important",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DecisionButton(
    label: String,
    subtitle: String,
    accent: Color,
    filled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp),
        shape = RoundedCornerShape(19.dp),
        color = if (filled) accent else accent.copy(alpha = 0.11f),
        border = if (filled) null else androidx.compose.foundation.BorderStroke(
            1.dp,
            accent.copy(alpha = 0.26f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(if (filled) Color.White else accent, CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (filled) Color.White else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (filled) Color.White.copy(alpha = 0.72f) else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (filled) Color.White else accent,
            )
        }
    }
}

@Composable
internal fun EventRow(
    event: NotificationListItem,
    modifier: Modifier = Modifier,
    showExplanation: Boolean = false,
    onFeedback: ((String, Boolean) -> Unit)? = null,
) {
    val accent = priorityColor(event.priority)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(accent.copy(alpha = 0.17f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    event.appLabel.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        event.appLabel,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    PriorityPill(event.priority, accent)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    event.title ?: event.category.lowercase().replaceFirstChar(Char::titlecase),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (event.message != null) {
                    Text(
                        event.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (showExplanation) {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        event.explanation,
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                    )
                    event.personalProbability?.let { probability ->
                        Text(
                            if (event.personalModelApplied) {
                                "Personal preference applied · ${(probability * 100).toInt()}% important"
                            } else {
                                "Personal estimate · ${(probability * 100).toInt()}% important"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (
                        event.action != "IMPORTANT" &&
                        event.action != "NOT_IMPORTANT" &&
                        onFeedback != null
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = { onFeedback(event.notificationKey, true) },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) { Text(stringResource(R.string.review_important)) }
                            TextButton(
                                onClick = { onFeedback(event.notificationKey, false) },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) { Text(stringResource(R.string.review_not_important)) }
                        }
                    } else if (event.action == "IMPORTANT" || event.action == "NOT_IMPORTANT") {
                        Text(
                            if (event.action == "IMPORTANT") "Marked important" else "Marked not important",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    formatEventTime(event.postedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}
