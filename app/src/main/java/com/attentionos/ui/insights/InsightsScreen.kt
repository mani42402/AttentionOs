package com.attentionos.ui.insights

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.attentionos.R
import com.attentionos.ui.MainUiState
import com.attentionos.ui.components.ScreenHeader
import com.attentionos.ui.components.SettingIcon
import com.attentionos.ui.components.SoftDivider
import com.attentionos.ui.home.ReviewNudgeCard
import com.attentionos.ui.home.TodayOverviewCard
import com.attentionos.ui.theme.Ice500
import com.attentionos.ui.theme.Mint500
import com.attentionos.ui.theme.Sun500

@Composable
internal fun SimpleSummaryScreen(
    state: MainUiState,
    onReview: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                eyebrow = stringResource(R.string.insights_your_helper),
                title = stringResource(R.string.insights_summary),
                description = stringResource(R.string.insights_only_the_useful_parts_what_was_handled),
            )
        }
        item { TodayOverviewCard(state) }
        item {
            PersonalizationSummaryCard(state)
        }
        if (state.unreviewedEvents.isNotEmpty()) {
            item {
                ReviewNudgeCard(
                    count = state.unreviewedEvents.size,
                    onClick = onReview,
                )
            }
        }
        item {
            Card(
                modifier = Modifier.padding(horizontal = 16.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    SettingIcon(Icons.Default.Lock, Mint500)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.insights_important_alerts_stay_protected), style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Security, financial, calls, alarms, and urgent incidents always keep a high-priority safety floor.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Text(
                "Your notification content and preferences stay on this phone.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun PersonalizationSummaryCard(state: MainUiState) {
    val progress = if (state.personalModelActive) 1f else {
        (
            minOf(
                state.pilotDaysElapsed.coerceIn(0, 7) / 7f,
                state.personalizedModel.exampleCount / 50f,
            )
            )
    }
    Card(
        modifier = Modifier.padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingIcon(Icons.Default.CheckCircle, Ice500)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.personalModelActive) "Personalized for you" else "Getting to know you",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        if (state.personalModelActive) {
                            "Your feedback now helps shape notification priority."
                        } else {
                            "Your helper is learning safely without changing notification behavior yet."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = Ice500,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (state.personalModelActive) {
                    "${state.personalizedModel.exampleCount} preferences learned"
                } else {
                    "Day ${state.pilotDaysElapsed.coerceIn(1, 7)} of 7 · " +
                        "${state.personalizedModel.exampleCount} of 50 quick checks"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ControlStatusCard(
    state: MainUiState,
    hasAccess: Boolean,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Helper status",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                Surface(
                    color = if (hasAccess) Mint500.copy(alpha = 0.11f) else {
                        Sun500.copy(alpha = 0.13f)
                    },
                    shape = CircleShape,
                ) {
                    Text(
                        if (hasAccess) "Ready" else "Action needed",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (hasAccess) Mint500 else Sun500,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            StatusLine(
                title = stringResource(R.string.insights_notification_access),
                value = if (hasAccess) "Connected" else "Not connected",
                positive = hasAccess,
            )
            SoftDivider()
            StatusLine(
                title = stringResource(R.string.insights_attention_mode),
                value = if (state.settings.focusMode) "On" else "Off",
                positive = state.settings.focusMode,
            )
            SoftDivider()
            StatusLine(
                title = stringResource(R.string.insights_personalization),
                value = when {
                    !state.settings.learningEnabled -> "Paused"
                    state.personalModelActive -> "Personalized"
                    else -> "Learning · day ${state.pilotDaysElapsed.coerceIn(1, 7)}"
                },
                positive = state.settings.learningEnabled,
            )
        }
    }
}

@Composable
internal fun StatusLine(
    title: String,
    value: String,
    positive: Boolean,
) {
    Row(
        modifier = Modifier.padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(if (positive) Mint500 else Sun500, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
