package com.attentionos.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.attentionos.ui.MainUiState
import com.attentionos.ui.components.AttentionCard
import com.attentionos.ui.components.HSpace
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
import kotlinx.coroutines.launch

/**
 * Onboarding.
 *
 * Rebuilt as a swipeable pager. The previous flow was button-only with no gesture support, no
 * back handling — system back exited the app rather than stepping back — and, most oddly, a
 * mandatory five-scenario diagnostic before setup could be completed. That test now lives in
 * Insights, where it answers "show me it works" for someone who is curious rather than blocking
 * someone who is trying to start.
 *
 * Every page can be skipped. Nothing here collects information the app cannot infer or ask for
 * later, so forcing a linear path would only cost patience.
 */
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
    val pages = OnboardingPage.entries
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val enabled = motionEnabled()

    // Back steps through pages instead of leaving the app, which is what it did before.
    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Box {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            OnboardingHeader(
                page = pagerState.currentPage,
                total = pages.size,
                onSkip = {
                    haptics.select()
                    onComplete()
                },
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { index ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.screenHorizontal),
                    // Pages are short; centring keeps them from stranding content at the top
                    // with a large void beneath.
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (pages[index]) {
                        OnboardingPage.Welcome -> WelcomePage()
                        OnboardingPage.Promise -> PromisePage()
                        OnboardingPage.Access -> AccessPage(
                            connected = state.settings.onboardingComplete,
                            onOpenAccess = onOpenNotificationAccess,
                            onRequestPermission = onRequestNotificationPermission,
                        )
                        OnboardingPage.Preferences -> PreferencesPage(
                            state = state,
                            onFocusChanged = onFocusChanged,
                            onCriticalSoundChanged = onCriticalSoundChanged,
                            onCriticalVibrationChanged = onCriticalVibrationChanged,
                            onHighSoundChanged = onHighSoundChanged,
                            onHighVibrationChanged = onHighVibrationChanged,
                            onReminderChanged = onReminderChanged,
                        )
                        OnboardingPage.Ready -> ReadyPage()
                    }
                    VSpace(Spacing.xxxl)
                }
            }

            PageIndicator(
                current = pagerState.currentPage,
                total = pages.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.md),
            )

            OnboardingActions(
                isFirst = pagerState.currentPage == 0,
                isLast = pagerState.currentPage == pages.lastIndex,
                onBack = {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                },
                onNext = {
                    haptics.select()
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                onFinish = {
                    haptics.celebrate()
                    onComplete()
                },
            )
        }
    }
}

private enum class OnboardingPage { Welcome, Promise, Access, Preferences, Ready }

@Composable
private fun OnboardingHeader(page: Int, total: Int, onSkip: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "AttentionOS",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${page + 1} / $total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HSpace(Spacing.sm)
        TextButton(onClick = onSkip) { Text("Skip") }
    }
}

/**
 * Progress dots.
 *
 * The active dot stretches into a bar, which reads as position rather than decoration. Announced
 * as a single "step N of M" rather than as five separate elements.
 */
@Composable
private fun PageIndicator(current: Int, total: Int, modifier: Modifier = Modifier) {
    val enabled = motionEnabled()
    Row(
        modifier = modifier.semantics {
            contentDescription = "Step ${current + 1} of $total"
        },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            val active = index == current
            val width by animateDpAsState(
                targetValue = if (active) Spacing.xxl else Spacing.sm,
                animationSpec = Motion.snappy(enabled),
                label = "indicator-width",
            )
            val color by animateColorAsState(
                targetValue = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                animationSpec = Motion.snappy(enabled),
                label = "indicator-color",
            )
            Box(
                Modifier
                    .padding(horizontal = Spacing.xs)
                    .height(Spacing.sm)
                    .width(width)
                    .background(color, Radius.pill)
                    .clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun OnboardingActions(
    isFirst: Boolean,
    isLast: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = Spacing.screenHorizontal,
                vertical = Spacing.lg,
            ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(visible = !isFirst) {
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        Button(
            onClick = if (isLast) onFinish else onNext,
            modifier = Modifier.weight(1f),
        ) {
            Text(if (isLast) "Start using my helper" else "Continue")
        }
    }
}

// ── Pages ─────────────────────────────────────────────────────────────────────

@Composable
private fun WelcomePage() {
    Column {
        VSpace(Spacing.xxxl)
        BreathingMark()
        VSpace(Spacing.xxl)
        Text(
            "Your notifications.\nOn your terms.",
            style = MaterialTheme.typography.displaySmall,
        )
        VSpace(Spacing.md)
        Text(
            "A helper that learns what deserves your attention, quiets the rest, and never " +
                "hides anything from you.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PromisePage() {
    Column {
        VSpace(Spacing.xxl)
        Text("Three promises", style = MaterialTheme.typography.headlineMedium)
        VSpace(Spacing.lg)
        listOf(
            "Nothing is hidden" to
                "Quiet means no sound or vibration. Every notification stays in your shade.",
            "Urgent always reaches you" to
                "Security codes, bank alerts, calls and alarms are never held back.",
            "It stays on your phone" to
                "Analysis runs on this device, encrypted. There is no account and no upload.",
        ).forEachIndexed { index, (title, body) ->
            PromiseRow(index + 1, title, body)
            VSpace(Spacing.md)
        }
    }
}

@Composable
private fun PromiseRow(number: Int, title: String, body: String) {
    AttentionCard {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(Spacing.xxl + Spacing.xs)
                    .background(MaterialTheme.colorScheme.primaryContainer, Radius.pill),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    number.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
            HSpace(Spacing.md)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                VSpace(Spacing.xxs)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AccessPage(
    connected: Boolean,
    onOpenAccess: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    Column {
        VSpace(Spacing.xxl)
        Text("One permission", style = MaterialTheme.typography.headlineMedium)
        VSpace(Spacing.md)
        Text(
            "Your helper needs to see notifications to sort them. That access stays on this " +
                "device — the app has no internet permission at all.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VSpace(Spacing.xl)
        AttentionCard(tone = MaterialTheme.colorScheme.tertiaryContainer) {
            Text(
                "Notification access",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            VSpace(Spacing.xs)
            Text(
                "Opens Android's settings. You can turn it off any time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
            )
            VSpace(Spacing.md)
            Button(onClick = onOpenAccess) { Text("Allow access") }
        }
        VSpace(Spacing.md)
        TextButton(onClick = onRequestPermission) {
            Text("Also allow reminders")
        }
    }
}

@Composable
private fun PreferencesPage(
    state: MainUiState,
    onFocusChanged: (Boolean) -> Unit,
    onCriticalSoundChanged: (Boolean) -> Unit,
    onCriticalVibrationChanged: (Boolean) -> Unit,
    onHighSoundChanged: (Boolean) -> Unit,
    onHighVibrationChanged: (Boolean) -> Unit,
    onReminderChanged: (Boolean) -> Unit,
) {
    Column {
        VSpace(Spacing.xxl)
        Text("How should it reach you?", style = MaterialTheme.typography.headlineMedium)
        VSpace(Spacing.md)
        Text(
            "You can change any of this later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VSpace(Spacing.lg)

        ChoiceCard(
            title = "Attention Mode",
            body = "Let your helper manage sound and vibration.",
            checked = state.settings.focusMode,
            onCheckedChange = onFocusChanged,
        )
        VSpace(Spacing.md)
        ChoiceCard(
            title = "Urgent alerts make a sound",
            body = "Security, bank, calls and alarms.",
            accent = PriorityColors.critical,
            checked = state.settings.criticalSound,
            onCheckedChange = onCriticalSoundChanged,
        )
        VSpace(Spacing.md)
        ChoiceCard(
            title = "Urgent alerts vibrate",
            body = "A physical nudge for the things that matter most.",
            accent = PriorityColors.critical,
            checked = state.settings.criticalVibration,
            onCheckedChange = onCriticalVibrationChanged,
        )
        VSpace(Spacing.md)
        ChoiceCard(
            title = "Important alerts make a sound",
            body = "Things your helper thinks probably need you.",
            accent = PriorityColors.high,
            checked = state.settings.highSound,
            onCheckedChange = onHighSoundChanged,
        )
        VSpace(Spacing.md)
        ChoiceCard(
            title = "Important alerts vibrate",
            body = "Vibration for likely-important notifications.",
            accent = PriorityColors.high,
            checked = state.settings.highVibration,
            onCheckedChange = onHighVibrationChanged,
        )
        VSpace(Spacing.md)
        ChoiceCard(
            title = "Daily review reminder",
            body = "A quiet nudge to teach your helper what matters.",
            checked = state.settings.reviewReminderEnabled,
            onCheckedChange = onReminderChanged,
        )
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accent: androidx.compose.ui.graphics.Color? = null,
) {
    val enabled = motionEnabled()
    val haptics = rememberHaptics()
    val container by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = Motion.snappy(enabled),
        label = "choice-container",
    )
    val onContainer = if (checked) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        shape = Radius.card,
        color = container,
        onClick = {
            haptics.select()
            onCheckedChange(!checked)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            accent?.let {
                StatusDot(it, size = Spacing.sm)
                HSpace(Spacing.md)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = onContainer)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.75f),
                )
            }
            androidx.compose.material3.Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/** The payoff page: a moment of arrival rather than another form. */
@Composable
private fun ReadyPage() {
    val enabled = motionEnabled()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        VSpace(Spacing.giant)
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(Motion.gentle(enabled)) +
                scaleIn(initialScale = 0.7f, animationSpec = Motion.playful(enabled)),
        ) {
            BreathingMark(size = 112.dp)
        }
        VSpace(Spacing.xxl)
        Text("You're all set.", style = MaterialTheme.typography.displaySmall)
        VSpace(Spacing.md)
        Text(
            "Your helper starts watching quietly. For the first week it only learns — it won't " +
                "change how anything reaches you until it has checked itself.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VSpace(Spacing.xl)
        AttentionCard(tone = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                "Curious how it decides?",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            VSpace(Spacing.xxs)
            Text(
                "Insights has a live check you can run any time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
            )
        }
    }
}

/** The brand mark: two rings, slowly breathing. */
@Composable
private fun BreathingMark(size: androidx.compose.ui.unit.Dp = 88.dp) {
    val enabled = motionEnabled()
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = Motion.playful(enabled),
        label = "mark-scale",
    )
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Canvas(
        modifier = Modifier
            .size(size)
            .clearAndSetSemantics { },
    ) {
        val radius = this.size.minDimension / 2f
        drawCircle(color = primary.copy(alpha = 0.10f), radius = radius * scale)
        drawCircle(
            color = primary,
            radius = radius * 0.62f * scale,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
        drawCircle(color = secondary, radius = radius * 0.18f * scale)
    }
}

@Preview(name = "Onboarding · light", heightDp = 900)
@Composable
private fun OnboardingPreview() {
    AttentionTheme(themeMode = ThemeMode.Light) {
        OnboardingScreen(
            state = MainUiState(isLoading = false),
            onFocusChanged = {}, onCriticalSoundChanged = {}, onCriticalVibrationChanged = {},
            onHighSoundChanged = {}, onHighVibrationChanged = {}, onReminderChanged = {},
            onRunTestLab = {}, onComplete = {}, onOpenNotificationAccess = {},
            onRequestNotificationPermission = {},
        )
    }
}

@Preview(name = "Onboarding · dark", heightDp = 900)
@Composable
private fun OnboardingDarkPreview() {
    AttentionTheme(themeMode = ThemeMode.Dark) {
        OnboardingScreen(
            state = MainUiState(isLoading = false),
            onFocusChanged = {}, onCriticalSoundChanged = {}, onCriticalVibrationChanged = {},
            onHighSoundChanged = {}, onHighVibrationChanged = {}, onReminderChanged = {},
            onRunTestLab = {}, onComplete = {}, onOpenNotificationAccess = {},
            onRequestNotificationPermission = {},
        )
    }
}
