package com.attentionos.ui.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.attentionos.ui.MainUiState
import com.attentionos.ui.theme.Forest800
import com.attentionos.ui.theme.Forest950
import com.attentionos.ui.theme.Ice500
import com.attentionos.ui.theme.Mint500
import com.attentionos.ui.theme.Sun500
import com.attentionos.ui.theme.Violet400
import com.attentionos.ui.components.EmptyActivity
import com.attentionos.ui.components.HelperLogo
import com.attentionos.ui.components.OutcomeItem
import com.attentionos.ui.components.SectionTitle
import com.attentionos.ui.components.StatusBadge
import com.attentionos.ui.review.EventRow

@Composable
internal fun DashboardScreen(
    state: MainUiState,
    hasAccess: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onSeeActivity: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            HelperDashboardHero(
                state = state,
                hasAccess = hasAccess,
                onFocusChanged = onFocusChanged,
            )
        }
        if (!hasAccess) {
            item {
                PermissionCard(onOpenNotificationAccess)
            }
        }
        if (state.unreviewedEvents.isNotEmpty()) {
            item {
                ReviewNudgeCard(
                    count = state.unreviewedEvents.size,
                    onClick = onSeeActivity,
                )
            }
        }
        item {
            TodayOverviewCard(state)
        }
        item {
            SectionTitle(
                title = "Recent notifications",
                action = "See all",
                onAction = onSeeActivity,
                modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 24.dp),
            )
        }
        if (state.events.isEmpty()) {
            item {
                EmptyActivity(hasAccess)
            }
        } else {
            items(state.events.take(4), key = { it.id }) { event ->
                EventRow(
                    event = event,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
internal fun HelperDashboardHero(
    state: MainUiState,
    hasAccess: Boolean,
    onFocusChanged: (Boolean) -> Unit,
) {
    val focusMode = state.settings.focusMode
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Forest950,
                RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
            )
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 18.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = 24.dp,
            ),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HelperLogo()
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "AttentionOS",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Text(
                        "Your personal notification helper",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.64f),
                    )
                }
                StatusBadge(
                    label = if (hasAccess) "Connected" else "Needs access",
                    positive = hasAccess,
                )
            }
            Spacer(Modifier.height(26.dp))
            Text(
                if (focusMode) "Your notifications,\non your terms." else {
                    "A calmer phone,\nwhen you’re ready."
                },
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (focusMode) {
                    "Important alerts can get your attention. Everything else still waits safely in your notification shade."
                } else {
                    "Turn on Attention Mode when you want your helper to manage sound and vibration."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.70f),
            )
            Spacer(Modifier.height(20.dp))
            Surface(
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Color.White.copy(alpha = 0.11f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Attention Mode",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                        Text(
                            if (focusMode) "On · helper controls interruption" else "Off · apps behave normally",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.62f),
                        )
                    }
                    Switch(
                        checked = focusMode,
                        onCheckedChange = onFocusChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Ice500,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.16f),
                            uncheckedBorderColor = Color.White.copy(alpha = 0.28f),
                        ),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(Mint500, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.personalModelActive) {
                        "Personalized for you"
                    } else {
                        "Getting to know you · day ${state.pilotDaysElapsed.coerceIn(1, 7)} of 7"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.76f),
                )
            }
        }
    }
}

@Composable
internal fun TodayOverviewCard(state: MainUiState) {
    val total = state.receivedToday.coerceAtLeast(1)
    val importantFraction = state.importantToday.toFloat() / total
    val quietFraction = state.queuedToday.toFloat() / total
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "TODAY",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    state.receivedToday.toString(),
                    style = MaterialTheme.typography.displaySmall,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "notifications checked",
                    modifier = Modifier.padding(bottom = 4.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(18.dp))
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp),
            ) {
                drawRoundRect(
                    trackColor,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                )
                drawRoundRect(
                    Sun500,
                    size = androidx.compose.ui.geometry.Size(size.width * importantFraction, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                )
                drawRoundRect(
                    Mint500,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        size.width * (1f - quietFraction),
                        0f,
                    ),
                    size = androidx.compose.ui.geometry.Size(size.width * quietFraction, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutcomeItem(
                    value = state.importantToday,
                    label = "needed attention",
                    color = Sun500,
                    modifier = Modifier.weight(1f),
                )
                OutcomeItem(
                    value = state.queuedToday,
                    label = "stayed quiet",
                    color = Mint500,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun PermissionCard(onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        colors = CardDefaults.cardColors(containerColor = Sun500.copy(alpha = 0.17f)),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = Forest800)
                Spacer(Modifier.width(10.dp))
                Text("Finish private setup", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Allow notification access so AttentionOS can classify alerts on this device. No data leaves your phone.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onOpen,
                colors = ButtonDefaults.buttonColors(containerColor = Forest800),
            ) {
                Text("Allow access")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}

@Composable
internal fun ReviewNudgeCard(count: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(
                        Brush.linearGradient(listOf(Violet400, Forest800)),
                        RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    count.coerceAtMost(99).toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Help it understand you", style = MaterialTheme.typography.titleMedium)
                Text(
                    "$count ${if (count == 1) "notification" else "notifications"} ready for a quick check",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
