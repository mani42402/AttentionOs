package com.attentionos.ui.onboarding

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.attentionos.ui.MainUiState
import com.attentionos.ui.theme.Forest950
import com.attentionos.ui.theme.Ice500
import com.attentionos.ui.theme.LocalMotionEnabled
import com.attentionos.ui.theme.Mint500
import com.attentionos.ui.theme.Sun500
import com.attentionos.ui.theme.Violet400
import com.attentionos.ui.components.ElevatedPanel
import com.attentionos.ui.components.HelperLogo
import com.attentionos.ui.components.PriorityPill
import com.attentionos.ui.components.SettingIcon
import com.attentionos.ui.components.SoftDivider
import com.attentionos.ui.components.modernSwitchColors
import com.attentionos.ui.components.priorityColor
import com.attentionos.ui.components.readableUiName
import com.attentionos.ui.components.rememberNotificationAccess
import com.attentionos.ui.settings.InterruptionPriorityRow

@Composable
internal fun OnboardingScreen(
    state: MainUiState,
    onFocusChanged: (Boolean) -> Unit,
    onCriticalSoundChanged: (Boolean) -> Unit,
    onCriticalVibrationChanged: (Boolean) -> Unit,
    onHighSoundChanged: (Boolean) -> Unit,
    onHighVibrationChanged: (Boolean) -> Unit,
    onReminderChanged: (Boolean) -> Unit,
    onRunTestLab: () -> Unit,
    onComplete: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var demoChoice by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val hasAccess = rememberNotificationAccess()
    val motionEnabled = LocalMotionEnabled.current
    val enter = if (motionEnabled) {
        fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 8 }
    } else {
        fadeIn(tween(0))
    }
    val exit = if (motionEnabled) fadeOut(tween(180)) else fadeOut(tween(0))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .background(Forest950)
                .align(Alignment.TopCenter),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 18.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HelperLogo()
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "AttentionOS",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Your personal notification helper",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        Text(
                            "${step + 1} / 4",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            item { OnboardingStepIndicator(step) }
            item {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = { enter togetherWith exit },
                    label = "onboarding-step",
                ) { currentStep ->
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            when (currentStep) {
                                0 -> "Your notifications.\nOn your terms."
                                1 -> "Try your helper\nwith a notification."
                                2 -> "Choose how it\ngets your attention."
                                else -> "Check the safety\nrules together."
                            },
                            style = MaterialTheme.typography.displaySmall,
                        )
                        Text(
                            when (currentStep) {
                                0 -> "A personal helper that learns what matters to you, quiets unnecessary interruption, and never hides your notifications."
                                1 -> "Use this safe demo to see how quick feedback shapes future notification behavior."
                                2 -> "You decide which priority levels may make sound or vibrate. Every option remains available in Settings."
                                else -> "Run five examples through the real on-device safety path before beginning."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        when (currentStep) {
                            0 -> {
                                FeatureOverviewPanel()
                                AccessCard(
                                    connected = hasAccess,
                                    onClick = onOpenNotificationAccess,
                                )
                            }

                            1 -> NotificationDemoCard(
                                choice = demoChoice,
                                onChoice = { demoChoice = it },
                            )

                            2 -> {
                                FeatureToggleCard(
                                    icon = Icons.Default.Lock,
                                    title = "Attention Mode",
                                    subtitle = "Your helper manages which priorities may interrupt",
                                    checked = state.settings.focusMode,
                                    accent = Violet400,
                                    onCheckedChange = onFocusChanged,
                                )
                                InterruptionSetupCard(
                                    state = state,
                                    onCriticalSoundChanged = onCriticalSoundChanged,
                                    onCriticalVibrationChanged = onCriticalVibrationChanged,
                                    onHighSoundChanged = onHighSoundChanged,
                                    onHighVibrationChanged = onHighVibrationChanged,
                                )
                                FeatureToggleCard(
                                    icon = Icons.Default.Notifications,
                                    title = "Quiet review reminders",
                                    subtitle = "Choose any time, up to six times daily, in Settings",
                                    checked = state.settings.reviewReminderEnabled,
                                    accent = Mint500,
                                    onCheckedChange = {
                                        onReminderChanged(it)
                                        if (it) onRequestNotificationPermission()
                                    },
                                )
                            }

                            else -> SafetyTestPanel(
                                state = state,
                                onRunTestLab = onRunTestLab,
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (step > 0) {
                        TextButton(onClick = { step-- }) { Text("Back") }
                    }
                    Button(
                        onClick = {
                            if (step < 3) step++ else onComplete()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        enabled = when {
                            step == 1 -> demoChoice != null
                            step < 3 -> true
                            else -> state.testLab.results.isNotEmpty()
                        },
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(
                            if (step < 3) "Continue" else "Start using my helper",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    }
                }
                if (step == 3 && state.testLab.results.isEmpty()) {
                    Text(
                        "Complete the safety check to finish setup.",
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (step == 1 && demoChoice == null) {
                    Text(
                        "Try one demo choice to continue.",
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun OnboardingStepIndicator(step: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(4) { index ->
            val selected = index <= step
            val width by animateFloatAsState(
                targetValue = if (index == step) 1.65f else 1f,
                animationSpec = tween(if (LocalMotionEnabled.current) 260 else 0),
                label = "step-width",
            )
            Box(
                Modifier
                    .weight(width)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            Brush.horizontalGradient(listOf(Ice500, Ice500))
                        } else {
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.outlineVariant,
                                    MaterialTheme.colorScheme.outlineVariant,
                                ),
                            )
                        },
                    ),
            )
        }
    }
}

@Composable
internal fun FeatureOverviewPanel() {
    ElevatedPanel {
        OnboardingFeature(
            number = "1",
            title = "Everything remains visible",
            description = "Quiet means no sound or vibration—not deleted, hidden, or blocked.",
        )
        SoftDivider()
        OnboardingFeature(
            number = "2",
            title = "Important alerts can reach you",
            description = "You choose which priorities may make sound or vibrate.",
        )
        SoftDivider()
        OnboardingFeature(
            number = "3",
            title = "It adapts to your choices",
            description = "Short Important or Can wait reviews build your private preferences.",
        )
    }
}

@Composable
internal fun OnboardingFeature(
    number: String,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(34.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(11.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    number,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun NotificationDemoCard(
    choice: Boolean?,
    onChoice: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    "1",
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Demo hint", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Imagine this just arrived. Choose how you would want your phone to behave.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline,
            ),
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        color = Ice500.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(13.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("M", color = Ice500, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Messages · Maya", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "just now",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "Can you call me when you’re free?",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { onChoice(true) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (choice == true) Ice500 else {
                            MaterialTheme.colorScheme.primary
                        },
                    ),
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Text("Notify me")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onChoice(false) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Text("Keep it quiet")
                }
            }
        }
        AnimatedVisibility(
            visible = choice != null,
            enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 4 },
        ) {
            Surface(
                color = Mint500.copy(alpha = 0.10f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Mint500.copy(alpha = 0.22f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        "2",
                        modifier = Modifier
                            .background(Mint500, CircleShape)
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            if (choice == true) "This would be allowed to notify you" else {
                                "This would stay visible without interruption"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "In the real app, choices like this help your helper understand you. This demo saves nothing.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AccessCard(connected: Boolean, onClick: () -> Unit) {
    val accent = if (connected) Mint500 else Sun500
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingIcon(
                if (connected) Icons.Default.CheckCircle else Icons.Default.Notifications,
                accent,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (connected) "Notification access connected" else "Connect notification access",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (connected) "Ready for private classification" else "Required to analyze alerts on this device",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = accent,
            )
        }
    }
}

@Composable
internal fun FeatureToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) accent.copy(alpha = 0.12f) else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (checked) accent.copy(alpha = 0.28f) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingIcon(icon, accent)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = modernSwitchColors(accent),
            )
        }
    }
}

@Composable
internal fun InterruptionSetupCard(
    state: MainUiState,
    onCriticalSoundChanged: (Boolean) -> Unit,
    onCriticalVibrationChanged: (Boolean) -> Unit,
    onHighSoundChanged: (Boolean) -> Unit,
    onHighVibrationChanged: (Boolean) -> Unit,
) {
    ElevatedPanel {
        Text(
            "INTERRUPTION RULES",
            modifier = Modifier.padding(start = 18.dp, top = 18.dp, bottom = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        InterruptionPriorityRow(
            priority = "Critical",
            description = "Security, safety and immediate action",
            sound = state.settings.criticalSound,
            vibration = state.settings.criticalVibration,
            onSoundChanged = onCriticalSoundChanged,
            onVibrationChanged = onCriticalVibrationChanged,
        )
        SoftDivider()
        InterruptionPriorityRow(
            priority = "High",
            description = "Important and time-sensitive",
            sound = state.settings.highSound,
            vibration = state.settings.highVibration,
            onSoundChanged = onHighSoundChanged,
            onVibrationChanged = onHighVibrationChanged,
        )
    }
}

@Composable
internal fun SafetyTestPanel(
    state: MainUiState,
    onRunTestLab: () -> Unit,
) {
    ElevatedPanel {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingIcon(Icons.Default.Star, Violet400)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Notification safety check", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Runs privately · posts nothing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRunTestLab,
                enabled = !state.testLab.isRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    when {
                        state.testLab.isRunning -> "Analyzing on device…"
                        state.testLab.results.isEmpty() -> "Run five scenarios"
                        else -> "Run safety check again"
                    },
                )
            }
            state.testLab.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            AnimatedVisibility(
                visible = state.testLab.results.isNotEmpty(),
                enter = fadeIn() + slideInVertically { it / 5 },
            ) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    state.testLab.results.forEachIndexed { index, result ->
                        if (index > 0) SoftDivider()
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .background(
                                        priorityColor(result.finalPriority.name),
                                        CircleShape,
                                    ),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(result.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${result.durationMillis} ms · ${result.category.readableUiName()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            PriorityPill(
                                result.finalPriority.name,
                                priorityColor(result.finalPriority.name),
                            )
                        }
                    }
                }
            }
        }
    }
}
