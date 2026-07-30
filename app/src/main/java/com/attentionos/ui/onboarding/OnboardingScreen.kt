package com.attentionos.ui.onboarding

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.attentionos.R
import com.attentionos.ui.MainUiState
import com.attentionos.ui.components.AttentionBrand
import com.attentionos.ui.components.HSpace
import com.attentionos.ui.components.SignalDot
import com.attentionos.ui.components.VSpace
import com.attentionos.ui.components.rememberNotificationAccess
import com.attentionos.ui.theme.AttentionTheme
import com.attentionos.ui.theme.LocalDarkTheme
import com.attentionos.ui.theme.Radius
import com.attentionos.ui.theme.SignalColors
import com.attentionos.ui.theme.Spacing
import com.attentionos.ui.theme.ThemeMode
import com.attentionos.ui.theme.motionEnabled
import com.attentionos.ui.theme.rememberHaptics
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * Three-page Signal Garden onboarding.
 *
 * The feature set is unchanged: notification access and the complete interruption-preference
 * controls remain available. They are folded into the privacy and learning pages so the product
 * story is three focused chapters rather than five disconnected forms.
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
    @Suppress("UNUSED_PARAMETER") onRunTestLab: () -> Unit,
    onComplete: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    ForceDarkSystemBars()
    val pages = OnboardingPage.entries
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val hasAccess = rememberNotificationAccess()
    var showPreferences by remember { mutableStateOf(false) }

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(SignalColors.Ink),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            val pageOffset = (
                (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction
                ).absoluteValue.coerceIn(0f, 1f)
            OnboardingPageFrame(
                page = pages[index],
                pageOffset = pageOffset,
                hasAccess = hasAccess,
                onConnectAccess = {
                    haptics.select()
                    onOpenNotificationAccess()
                    onRequestNotificationPermission()
                },
                onEditPreferences = { showPreferences = true },
                onNext = {
                    haptics.select()
                    scope.launch { pagerState.animateScrollToPage(index + 1) }
                },
                onFinish = {
                    haptics.celebrate()
                    onComplete()
                },
            )
        }

        OnboardingTopBar(
            canSkip = pagerState.currentPage < pages.lastIndex,
            onSkip = onComplete,
        )
    }

    if (showPreferences) {
        PreferencesSheet(
            state = state,
            onDismiss = { showPreferences = false },
            onFocusChanged = onFocusChanged,
            onCriticalSoundChanged = onCriticalSoundChanged,
            onCriticalVibrationChanged = onCriticalVibrationChanged,
            onHighSoundChanged = onHighSoundChanged,
            onHighVibrationChanged = onHighVibrationChanged,
            onReminderChanged = onReminderChanged,
        )
    }
}

private enum class OnboardingPage(
    @DrawableRes val image: Int,
    @StringRes val eyebrow: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
    val accent: Color,
) {
    Sort(
        image = R.drawable.onboarding_sorting,
        eyebrow = R.string.onboarding_smart_prioritization,
        title = R.string.onboarding_know_what_matters,
        body = R.string.onboarding_local_ai_sorts_every_notification_into_urgent,
        accent = SignalColors.Tangerine,
    ),
    Private(
        image = R.drawable.onboarding_private,
        eyebrow = R.string.onboarding_private_by_design,
        title = R.string.onboarding_your_data_stays_yours,
        body = R.string.onboarding_messages_choices_and_learning_stay_encrypted_on,
        accent = SignalColors.Mint,
    ),
    Learn(
        image = R.drawable.onboarding_learning,
        eyebrow = R.string.onboarding_learns_with_you,
        title = R.string.onboarding_made_personal_safely,
        body = R.string.onboarding_correct_a_few_decisions_and_attentionos_adapts,
        accent = SignalColors.Tangerine,
    ),
}

@Composable
private fun OnboardingPageFrame(
    page: OnboardingPage,
    pageOffset: Float,
    hasAccess: Boolean,
    onConnectAccess: () -> Unit,
    onEditPreferences: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    val enabled = motionEnabled()
    val transition = rememberInfiniteTransition(label = "onboarding-${page.name}")
    val visualOffset = if (enabled) pageOffset else 0f
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (enabled) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(8_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "art-drift",
    )

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(page.image),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1.035f + drift * 0.018f
                    scaleX = scale
                    scaleY = scale
                    translationX = -visualOffset * 54.dp.toPx()
                    alpha = 1f - visualOffset * 0.20f
                },
        )
        OnboardingArtMotion(page)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.48f to SignalColors.Ink.copy(alpha = 0.08f),
                        0.64f to SignalColors.Ink.copy(alpha = 0.88f),
                        0.76f to SignalColors.Ink,
                        1f to SignalColors.Ink,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.xxl),
        ) {
            VSpace(76.dp)
            Box(Modifier.weight(1f))

            Column(
                modifier = Modifier.graphicsLayer {
                    translationX = visualOffset * 32.dp.toPx()
                    alpha = 1f - visualOffset * 0.45f
                },
            ) {
                Text(
                    text = stringResource(page.eyebrow).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = page.accent,
                )
                VSpace(Spacing.md)
                Text(
                    text = stringResource(page.title),
                    style = MaterialTheme.typography.displaySmall,
                    color = SignalColors.Cream,
                )
                VSpace(Spacing.md)
                Text(
                    text = stringResource(page.body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = SignalColors.CreamMuted,
                )

                when (page) {
                    OnboardingPage.Private -> {
                        VSpace(Spacing.lg)
                        AccessAction(hasAccess, onConnectAccess)
                    }
                    OnboardingPage.Learn -> {
                        VSpace(Spacing.md)
                        TextButton(
                            onClick = onEditPreferences,
                            colors = ButtonDefaults.textButtonColors(contentColor = SignalColors.Mint),
                            contentPadding = ButtonDefaults.TextButtonContentPadding,
                        ) {
                            Text(stringResource(R.string.onboarding_review_interruption_preferences))
                        }
                    }
                    else -> Unit
                }

                VSpace(Spacing.lg)
                OnboardingFooter(
                    current = page.ordinal,
                    accent = page.accent,
                    isLast = page == OnboardingPage.Learn,
                    onNext = onNext,
                    onFinish = onFinish,
                )
                VSpace(Spacing.md)
            }
        }
    }
}

@Composable
private fun OnboardingTopBar(canSkip: Boolean, onSkip: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Spacing.xxl, vertical = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AttentionBrand(
            modifier = Modifier.weight(1f),
            nameColor = SignalColors.Cream,
        )
        if (canSkip) {
            TextButton(
                onClick = onSkip,
                colors = ButtonDefaults.textButtonColors(contentColor = SignalColors.Cream),
            ) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }
    }
}

@Composable
private fun OnboardingFooter(
    current: Int,
    accent: Color,
    isLast: Boolean,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    if (isLast) {
        PageIndicator(current, accent)
        VSpace(Spacing.lg)
        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = Radius.card,
            colors = ButtonDefaults.buttonColors(
                containerColor = SignalColors.Tangerine,
                contentColor = Color(0xFF3F1307),
            ),
        ) {
            Text(stringResource(R.string.onboarding_start_using_attentionos), style = MaterialTheme.typography.labelLarge)
            HSpace(Spacing.md)
            ArrowGlyph(color = Color(0xFF3F1307))
        }
        Text(
            text = stringResource(R.string.onboarding_replay_anytime_in_settings),
            style = MaterialTheme.typography.bodySmall,
            color = SignalColors.CreamMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageIndicator(current, accent, Modifier.weight(1f))
            Button(
                onClick = onNext,
                modifier = Modifier.size(58.dp),
                shape = CircleShape,
                contentPadding = ButtonDefaults.ContentPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = SignalColors.Ink,
                ),
            ) {
                ArrowGlyph(color = SignalColors.Ink)
            }
        }
    }
}

@Composable
private fun PageIndicator(current: Int, accent: Color, modifier: Modifier = Modifier) {
    val spoken = stringResource(R.string.onboarding_step_of_3, current + 1)
    Row(
        modifier = modifier.semantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            Box(
                Modifier
                    .height(4.dp)
                    .width(if (index == current) 48.dp else 30.dp)
                    .background(
                        if (index == current) accent else SignalColors.CreamMuted.copy(alpha = 0.32f),
                        Radius.pill,
                    )
                    .clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun AccessAction(hasAccess: Boolean, onConnect: () -> Unit) {
    if (hasAccess) {
        Row(
            modifier = Modifier
                .clip(Radius.pill)
                .background(SignalColors.Mint.copy(alpha = 0.12f))
                .border(1.dp, SignalColors.Mint.copy(alpha = 0.5f), Radius.pill)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SignalDot(SignalColors.Mint)
            HSpace(Spacing.sm)
            Text(
                stringResource(R.string.onboarding_notification_access_connected),
                style = MaterialTheme.typography.labelMedium,
                color = SignalColors.Mint,
            )
        }
    } else {
        OutlinedButton(
            onClick = onConnect,
            border = androidx.compose.foundation.BorderStroke(1.dp, SignalColors.Mint),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SignalColors.Mint),
        ) {
            Text(stringResource(R.string.onboarding_connect_notification_access))
        }
    }
}

@Composable
private fun OnboardingArtMotion(page: OnboardingPage) {
    val enabled = motionEnabled()
    val transition = rememberInfiniteTransition(label = "onboarding-overlay-${page.name}")
    val loop by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (enabled) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(5_600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "overlay-loop",
    )
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clearAndSetSemantics { },
    ) {
        when (page) {
            OnboardingPage.Sort -> {
                repeat(6) { index ->
                    val phase = (loop + index / 6f) % 1f
                    val x = size.width * (0.22f + ((index * 0.19f) % 0.58f))
                    val y = size.height * (0.09f + phase * 0.40f)
                    drawCircle(
                        color = when (index % 3) {
                            0 -> SignalColors.Tangerine
                            1 -> SignalColors.Mint
                            else -> SignalColors.Cream
                        }.copy(alpha = sin(phase * PI).toFloat().coerceAtLeast(0f) * 0.55f),
                        radius = 3.5.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(x, y),
                    )
                }
            }
            OnboardingPage.Private -> {
                repeat(3) { index ->
                    val pulse = ((loop + index * 0.22f) % 1f)
                    drawCircle(
                        color = SignalColors.Mint.copy(alpha = (1f - pulse) * 0.18f),
                        radius = size.minDimension * (0.18f + pulse * 0.24f),
                        center = androidx.compose.ui.geometry.Offset(
                            size.width * 0.5f,
                            size.height * 0.37f,
                        ),
                        style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            OnboardingPage.Learn -> {
                repeat(7) { index ->
                    val angle = PI + (PI * index / 6.0)
                    val center = androidx.compose.ui.geometry.Offset(
                        x = size.width * 0.5f + kotlin.math.cos(angle).toFloat() * size.width * 0.34f,
                        y = size.height * 0.29f + kotlin.math.sin(angle).toFloat() * size.height * 0.13f,
                    )
                    val glow = (0.35f + 0.65f * sin((loop * PI * 2) - index * 0.55f).toFloat())
                        .coerceIn(0.15f, 1f)
                    drawCircle(SignalColors.Sun.copy(alpha = glow * 0.24f), 9.dp.toPx(), center)
                    drawCircle(SignalColors.Sun.copy(alpha = glow), 3.dp.toPx(), center)
                }
            }
        }
    }
}

@Composable
private fun ArrowGlyph(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = 2.4.dp.toPx()
        val centerY = size.height / 2f
        drawLine(
            color,
            androidx.compose.ui.geometry.Offset(size.width * 0.12f, centerY),
            androidx.compose.ui.geometry.Offset(size.width * 0.84f, centerY),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            color,
            androidx.compose.ui.geometry.Offset(size.width * 0.58f, size.height * 0.23f),
            androidx.compose.ui.geometry.Offset(size.width * 0.86f, centerY),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            color,
            androidx.compose.ui.geometry.Offset(size.width * 0.58f, size.height * 0.77f),
            androidx.compose.ui.geometry.Offset(size.width * 0.86f, centerY),
            stroke,
            StrokeCap.Round,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreferencesSheet(
    state: MainUiState,
    onDismiss: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onCriticalSoundChanged: (Boolean) -> Unit,
    onCriticalVibrationChanged: (Boolean) -> Unit,
    onHighSoundChanged: (Boolean) -> Unit,
    onHighVibrationChanged: (Boolean) -> Unit,
    onReminderChanged: (Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SignalColors.InkRaised,
        contentColor = SignalColors.Cream,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = Spacing.md)
                    .size(width = 44.dp, height = 4.dp)
                    .background(SignalColors.CreamMuted.copy(alpha = 0.35f), Radius.pill),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Spacing.xl),
        ) {
            Text(stringResource(R.string.onboarding_interruption_preferences), style = MaterialTheme.typography.headlineSmall)
            VSpace(Spacing.xs)
            Text(
                stringResource(R.string.onboarding_these_controls_are_also_available_in_settings),
                style = MaterialTheme.typography.bodyMedium,
                color = SignalColors.CreamMuted,
            )
            VSpace(Spacing.lg)

            PreferenceToggle(
                stringResource(R.string.onboarding_attention_mode),
                stringResource(R.string.onboarding_use_your_priority_preferences_while_the_helper),
                state.settings.focusMode,
                onFocusChanged,
            )
            PreferenceDivider()
            PreferenceToggle(
                stringResource(R.string.onboarding_urgent_alert_sound),
                stringResource(R.string.onboarding_security_finance_calls_and_alarms),
                state.settings.criticalSound,
                onCriticalSoundChanged,
            )
            PreferenceToggle(
                stringResource(R.string.onboarding_urgent_alert_vibration),
                stringResource(R.string.onboarding_a_physical_nudge_for_protected_alerts),
                state.settings.criticalVibration,
                onCriticalVibrationChanged,
            )
            PreferenceDivider()
            PreferenceToggle(
                stringResource(R.string.onboarding_important_alert_sound),
                stringResource(R.string.onboarding_alerts_likely_to_need_your_attention),
                state.settings.highSound,
                onHighSoundChanged,
            )
            PreferenceToggle(
                stringResource(R.string.onboarding_important_alert_vibration),
                stringResource(R.string.onboarding_vibrate_for_likely_important_notifications),
                state.settings.highVibration,
                onHighVibrationChanged,
            )
            PreferenceDivider()
            PreferenceToggle(
                stringResource(R.string.onboarding_daily_review_reminder),
                stringResource(R.string.onboarding_a_quiet_reminder_to_correct_recent_decisions),
                state.settings.reviewReminderEnabled,
                onReminderChanged,
            )

            VSpace(Spacing.lg)
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SignalColors.Mint,
                    contentColor = SignalColors.Ink,
                ),
            ) {
                Text(stringResource(R.string.onboarding_save_preferences))
            }
            VSpace(Spacing.xxl)
        }
    }
}

@Composable
private fun PreferenceToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.md)
            .semantics(mergeDescendants = true) {
                toggleableState = ToggleableState(checked)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = SignalColors.Cream)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SignalColors.CreamMuted,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                haptics.select()
                onChanged(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = SignalColors.Ink,
                checkedTrackColor = SignalColors.Mint,
                uncheckedThumbColor = SignalColors.CreamMuted,
                uncheckedTrackColor = SignalColors.InkHigh,
                uncheckedBorderColor = SignalColors.InkBorder,
            ),
        )
    }
}

@Composable
private fun PreferenceDivider() {
    HorizontalDivider(color = SignalColors.InkBorder)
}

@Composable
private fun ForceDarkSystemBars() {
    val view = LocalView.current
    val darkTheme = LocalDarkTheme.current
    DisposableEffect(view, darkTheme) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        onDispose {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
}

@Preview(name = "Onboarding")
@Composable
private fun OnboardingPreview() {
    AttentionTheme(themeMode = ThemeMode.Dark) {
        OnboardingScreen(
            state = MainUiState(isLoading = false),
            onFocusChanged = {},
            onCriticalSoundChanged = {},
            onCriticalVibrationChanged = {},
            onHighSoundChanged = {},
            onHighVibrationChanged = {},
            onReminderChanged = {},
            onRunTestLab = {},
            onComplete = {},
            onOpenNotificationAccess = {},
            onRequestNotificationPermission = {},
        )
    }
}
