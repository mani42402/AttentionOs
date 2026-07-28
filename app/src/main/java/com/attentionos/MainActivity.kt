package com.attentionos

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.attentionos.core.common.TimeConstants
import com.attentionos.data.local.NotificationEventEntity
import com.attentionos.presentation.MainUiState
import com.attentionos.presentation.MainViewModel
import com.attentionos.presentation.UiEvent
import com.attentionos.presentation.theme.AttentionTheme
import com.attentionos.presentation.theme.Coral500
import com.attentionos.presentation.theme.Forest800
import com.attentionos.presentation.theme.Forest950
import com.attentionos.presentation.theme.Ice500
import com.attentionos.presentation.theme.LocalMotionEnabled
import com.attentionos.presentation.theme.Mint300
import com.attentionos.presentation.theme.Mint500
import com.attentionos.presentation.theme.Sun500
import com.attentionos.presentation.theme.Violet400
import com.attentionos.service.ReviewReminderWorker
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val openReviewRequest = MutableStateFlow(0)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory((application as AttentionApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(ReviewReminderWorker.EXTRA_OPEN_REVIEW, false)) {
            openReviewRequest.value += 1
        }
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            AttentionTheme(
                darkTheme = uiState.settings.darkTheme,
                motionEnabled = uiState.settings.motionEnabled,
            ) {
                val reviewRequest by openReviewRequest.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        when (event) {
                            is UiEvent.ExportReady -> shareExport(event.result.uri, event.result.count)
                            UiEvent.NothingToExport ->
                                snackbar.showSnackbar("No learned examples to export yet.")
                            UiEvent.DataDeleted -> snackbar.showSnackbar("All local attention data deleted.")
                            UiEvent.PersonalModelReset ->
                                snackbar.showSnackbar("Personalization reset. New choices start fresh.")
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    if (!uiState.isLoading && !uiState.settings.onboardingComplete) {
                        OnboardingScreen(
                            state = uiState,
                            onFocusChanged = viewModel::setFocusMode,
                            onCriticalSoundChanged = viewModel::setCriticalSound,
                            onCriticalVibrationChanged = viewModel::setCriticalVibration,
                            onHighSoundChanged = viewModel::setHighSound,
                            onHighVibrationChanged = viewModel::setHighVibration,
                            onReminderChanged = viewModel::setReviewReminderEnabled,
                            onRunTestLab = viewModel::runTestLab,
                            onComplete = viewModel::completeOnboarding,
                            onOpenNotificationAccess = {
                                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            },
                            onRequestNotificationPermission = ::requestNotificationPermission,
                        )
                    } else if (!uiState.isLoading) {
                        AttentionApp(
                        state = uiState,
                        snackbarHostState = snackbar,
                        reviewRequest = reviewRequest,
                        onFocusChanged = viewModel::setFocusMode,
                        onStoreContentChanged = viewModel::setStoreContent,
                        onLearningChanged = viewModel::setLearningEnabled,
                        onCriticalSoundChanged = viewModel::setCriticalSound,
                        onCriticalVibrationChanged = viewModel::setCriticalVibration,
                        onHighSoundChanged = viewModel::setHighSound,
                        onHighVibrationChanged = viewModel::setHighVibration,
                        onMediumSoundChanged = viewModel::setMediumSound,
                        onMediumVibrationChanged = viewModel::setMediumVibration,
                        onReminderChanged = viewModel::setReviewReminderEnabled,
                        onReminderTimesChanged = viewModel::setReviewReminderTimes,
                        onDarkThemeChanged = viewModel::setDarkTheme,
                        onMotionChanged = viewModel::setMotionEnabled,
                        onRequestNotificationPermission = ::requestNotificationPermission,
                        onRetentionChanged = viewModel::setRetentionDays,
                        onReplayOnboarding = viewModel::replayOnboarding,
                        onExport = viewModel::exportTrainingData,
                        onResetPersonalizedModel = viewModel::resetPersonalizedModel,
                        onRunTestLab = viewModel::runTestLab,
                        onDelete = viewModel::deleteAllData,
                        onFeedback = viewModel::submitFeedback,
                        onOpenNotificationAccess = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(ReviewReminderWorker.EXTRA_OPEN_REVIEW, false)) {
            openReviewRequest.value += 1
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun shareExport(uri: android.net.Uri, count: Int) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/x-ndjson"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "AttentionOS training data ($count examples)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Export private training data"))
    }
}

private enum class AppDestination(
    val label: String,
) {
    TODAY("Home"),
    ACTIVITY("Review"),
    INSIGHTS("Summary"),
    SETTINGS("Settings"),
}

@Composable
private fun HelperBottomBar(
    selected: AppDestination,
    onSelected: (AppDestination) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(66.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppDestination.entries.forEach { destination ->
                    val active = destination == selected
                    val duration = if (LocalMotionEnabled.current) 220 else 0
                    val iconScale by animateFloatAsState(
                        if (active) 1f else 0.94f,
                        tween(duration),
                        label = "navigation-scale",
                    )
                    val indicatorColor by animateColorAsState(
                        if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        tween(duration),
                        label = "navigation-indicator",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable { onSelected(destination) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 42.dp, height = 30.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                                .background(indicatorColor, RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            NavigationGlyph(destination, active)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            destination.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) MaterialTheme.colorScheme.primary else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(
                        WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding(),
                    )
                    .background(MaterialTheme.colorScheme.surface),
            )
        }
    }
}

@Composable
private fun NavigationGlyph(destination: AppDestination, active: Boolean) {
    val color by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        tween(if (LocalMotionEnabled.current) 220 else 0),
        label = "navigation-color",
    )
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(width = 2.1.dp.toPx(), cap = StrokeCap.Round)
        val w = size.width
        val h = size.height
        when (destination) {
            AppDestination.TODAY -> {
                val roof = Path().apply {
                    moveTo(w * 0.12f, h * 0.47f)
                    lineTo(w * 0.50f, h * 0.15f)
                    lineTo(w * 0.88f, h * 0.47f)
                }
                drawPath(roof, color, style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.22f, h * 0.43f), androidx.compose.ui.geometry.Offset(w * 0.22f, h * 0.86f), stroke.width, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.78f, h * 0.43f), androidx.compose.ui.geometry.Offset(w * 0.78f, h * 0.86f), stroke.width, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.22f, h * 0.86f), androidx.compose.ui.geometry.Offset(w * 0.78f, h * 0.86f), stroke.width, StrokeCap.Round)
            }

            AppDestination.ACTIVITY -> {
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.10f, h * 0.15f),
                    size = androidx.compose.ui.geometry.Size(w * 0.80f, h * 0.62f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f),
                    style = stroke,
                )
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.28f, h * 0.86f), androidx.compose.ui.geometry.Offset(w * 0.43f, h * 0.76f), stroke.width, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.33f, h * 0.38f), androidx.compose.ui.geometry.Offset(w * 0.70f, h * 0.38f), stroke.width, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.33f, h * 0.56f), androidx.compose.ui.geometry.Offset(w * 0.58f, h * 0.56f), stroke.width, StrokeCap.Round)
            }

            AppDestination.INSIGHTS -> {
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.14f, h * 0.84f), androidx.compose.ui.geometry.Offset(w * 0.86f, h * 0.84f), stroke.width, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.25f, h * 0.70f), androidx.compose.ui.geometry.Offset(w * 0.25f, h * 0.53f), stroke.width * 2.2f, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.50f, h * 0.70f), androidx.compose.ui.geometry.Offset(w * 0.50f, h * 0.33f), stroke.width * 2.2f, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.75f, h * 0.70f), androidx.compose.ui.geometry.Offset(w * 0.75f, h * 0.18f), stroke.width * 2.2f, StrokeCap.Round)
            }

            AppDestination.SETTINGS -> {
                listOf(0.25f to 0.66f, 0.50f to 0.34f, 0.75f to 0.60f).forEach {
                    (y, knobX) ->
                    drawLine(color, androidx.compose.ui.geometry.Offset(w * 0.12f, h * y), androidx.compose.ui.geometry.Offset(w * 0.88f, h * y), stroke.width, StrokeCap.Round)
                    drawCircle(color, radius = stroke.width * 1.35f, center = androidx.compose.ui.geometry.Offset(w * knobX, h * y))
                }
            }
        }
    }
}

@Composable
private fun OnboardingScreen(
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
private fun AmbientBackdrop() {
    val motionEnabled = LocalMotionEnabled.current
    val drift = if (motionEnabled) {
        val transition = rememberInfiniteTransition(label = "ambient")
        val animatedDrift by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(7_000),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "ambient-drift",
        )
        animatedDrift
    } else {
        0f
    }
    Canvas(
        Modifier
            .fillMaxSize()
            .alpha(0.72f),
    ) {
        drawCircle(
            color = Violet400.copy(alpha = 0.10f),
            radius = size.minDimension * 0.55f,
            center = androidx.compose.ui.geometry.Offset(
                x = size.width * (0.92f - drift * 0.07f),
                y = size.height * 0.12f,
            ),
        )
        drawCircle(
            color = Mint500.copy(alpha = 0.08f),
            radius = size.minDimension * 0.44f,
            center = androidx.compose.ui.geometry.Offset(
                x = size.width * (0.04f + drift * 0.08f),
                y = size.height * 0.78f,
            ),
        )
    }
}

@Composable
private fun BrandMark() {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(
                Brush.linearGradient(listOf(Violet400, Forest800, Mint500)),
                RoundedCornerShape(15.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            drawCircle(Color.White.copy(alpha = 0.22f))
            drawCircle(
                Color.White,
                radius = size.minDimension * 0.19f,
            )
            drawArc(
                color = Color.White,
                startAngle = -80f,
                sweepAngle = 230f,
                useCenter = false,
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun OnboardingStepIndicator(step: Int) {
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
private fun FeatureOverviewPanel() {
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
private fun OnboardingFeature(
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
private fun NotificationDemoCard(
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
private fun AccessCard(connected: Boolean, onClick: () -> Unit) {
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
private fun FeatureToggleCard(
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
private fun InterruptionSetupCard(
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
private fun SafetyTestPanel(
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



@Composable
private fun AttentionApp(
    state: MainUiState,
    snackbarHostState: SnackbarHostState,
    reviewRequest: Int,
    onFocusChanged: (Boolean) -> Unit,
    onStoreContentChanged: (Boolean) -> Unit,
    onLearningChanged: (Boolean) -> Unit,
    onCriticalSoundChanged: (Boolean) -> Unit,
    onCriticalVibrationChanged: (Boolean) -> Unit,
    onHighSoundChanged: (Boolean) -> Unit,
    onHighVibrationChanged: (Boolean) -> Unit,
    onMediumSoundChanged: (Boolean) -> Unit,
    onMediumVibrationChanged: (Boolean) -> Unit,
    onReminderChanged: (Boolean) -> Unit,
    onReminderTimesChanged: (Set<Int>) -> Unit,
    onDarkThemeChanged: (Boolean) -> Unit,
    onMotionChanged: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRetentionChanged: (Int) -> Unit,
    onReplayOnboarding: () -> Unit,
    onExport: () -> Unit,
    onResetPersonalizedModel: () -> Unit,
    onRunTestLab: () -> Unit,
    onDelete: () -> Unit,
    onFeedback: (String, Boolean) -> Unit,
    onOpenNotificationAccess: () -> Unit,
) {
    var destination by rememberSaveable { mutableStateOf(AppDestination.TODAY) }
    val motionEnabled = LocalMotionEnabled.current
    LaunchedEffect(reviewRequest) {
        if (reviewRequest > 0) destination = AppDestination.ACTIVITY
    }
    val hasAccess = rememberNotificationAccess()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            HelperBottomBar(
                selected = destination,
                onSelected = { destination = it },
            )
        },
    ) { scaffoldPadding ->
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    if (motionEnabled) {
                        (fadeIn(tween(240)) + slideInVertically(tween(260)) { it / 16 }) togetherWith
                            (fadeOut(tween(150)) + slideOutVertically(tween(180)) { -it / 18 })
                    } else {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    }
                },
                label = "destination",
                modifier = Modifier.padding(bottom = scaffoldPadding.calculateBottomPadding()),
            ) { current ->
                when (current) {
                    AppDestination.TODAY -> DashboardScreen(
                        state = state,
                        hasAccess = hasAccess,
                        onFocusChanged = onFocusChanged,
                        onOpenNotificationAccess = onOpenNotificationAccess,
                        onSeeActivity = { destination = AppDestination.ACTIVITY },
                    )
                    AppDestination.ACTIVITY -> ActivityScreen(
                        state = state,
                        onFeedback = onFeedback,
                        reviewRequest = reviewRequest,
                    )
                    AppDestination.INSIGHTS -> SimpleSummaryScreen(
                        state = state,
                        onReview = { destination = AppDestination.ACTIVITY },
                    )
                    AppDestination.SETTINGS -> SettingsScreen(
                        state = state,
                        hasAccess = hasAccess,
                        onFocusChanged = onFocusChanged,
                        onOpenNotificationAccess = onOpenNotificationAccess,
                        onStoreContentChanged = onStoreContentChanged,
                        onLearningChanged = onLearningChanged,
                        onCriticalSoundChanged = onCriticalSoundChanged,
                        onCriticalVibrationChanged = onCriticalVibrationChanged,
                        onHighSoundChanged = onHighSoundChanged,
                        onHighVibrationChanged = onHighVibrationChanged,
                        onMediumSoundChanged = onMediumSoundChanged,
                        onMediumVibrationChanged = onMediumVibrationChanged,
                        onReminderChanged = onReminderChanged,
                        onReminderTimesChanged = onReminderTimesChanged,
                        onDarkThemeChanged = onDarkThemeChanged,
                        onMotionChanged = onMotionChanged,
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        onRetentionChanged = onRetentionChanged,
                        onReplayOnboarding = onReplayOnboarding,
                        onExport = onExport,
                        onResetPersonalizedModel = onResetPersonalizedModel,
                        onDelete = onDelete,
                    )
                }
            }
            // Android 15+ enforces edge-to-edge. Keep status icons readable on every tab.
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                    .background(Forest950),
            )
        }
    }
}

@Composable
private fun DashboardScreen(
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
private fun HelperDashboardHero(
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
private fun HelperLogo() {
    Surface(
        modifier = Modifier.size(42.dp),
        shape = RoundedCornerShape(13.dp),
        color = Ice500,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(23.dp)) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.16f, size.height * 0.18f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.68f, size.height * 0.52f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.15f),
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                )
                drawLine(
                    Color.White,
                    androidx.compose.ui.geometry.Offset(size.width * 0.32f, size.height * 0.82f),
                    androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.70f),
                    2.dp.toPx(),
                    StrokeCap.Round,
                )
                drawCircle(
                    Color.White,
                    radius = 1.7.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.44f),
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String, positive: Boolean) {
    Surface(
        color = if (positive) Mint500.copy(alpha = 0.18f) else Sun500.copy(alpha = 0.18f),
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(if (positive) Mint300 else Sun500, CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun TodayOverviewCard(state: MainUiState) {
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
private fun OutcomeItem(value: Int, label: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge)
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}





@Composable
private fun PermissionCard(onOpen: () -> Unit) {
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
private fun ReviewNudgeCard(count: Int, onClick: () -> Unit) {
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






@Composable
private fun SectionTitle(
    title: String,
    action: String?,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun EmptyActivity(hasAccess: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (hasAccess) Icons.Default.CheckCircle else Icons.Default.Info,
            contentDescription = null,
            tint = Mint500,
            modifier = Modifier.size(38.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (hasAccess) "Quiet so far" else "Waiting for notification access",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (hasAccess) "New attention decisions will appear here." else {
                "Complete setup to begin local classification."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private enum class ActivityFilter(val label: String) {
    ALL("All"),
    IMPORTANT("Important"),
    QUEUED("Quiet"),
}

@Composable
private fun ActivityScreen(
    state: MainUiState,
    onFeedback: (String, Boolean) -> Unit,
    reviewRequest: Int,
) {
    var filter by rememberSaveable { mutableStateOf(ActivityFilter.ALL) }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    var skippedIds by remember { mutableStateOf(emptySet<Long>()) }
    LaunchedEffect(reviewRequest) {
        if (reviewRequest > 0) reviewing = true
    }
    val reviewEvents = state.unreviewedEvents.filterNot { it.id in skippedIds }
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
                eyebrow = "YOUR NOTIFICATIONS",
                title = "Review",
                description = "See how notifications were handled and correct anything that feels wrong.",
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
                            Text("Quick review", style = MaterialTheme.typography.titleMedium)
                            val decisionCount = state.unreviewedEvents.size
                            Text(
                                "$decisionCount ${if (decisionCount == 1) "decision" else "decisions"} ready for feedback",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = { reviewing = true }) { Text("Review") }
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
private fun ModernReviewSession(
    event: NotificationEventEntity?,
    reviewedCount: Int,
    remainingToGoal: Int,
    onImportant: (NotificationEventEntity) -> Unit,
    onNotImportant: (NotificationEventEntity) -> Unit,
    onSkip: (NotificationEventEntity) -> Unit,
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
                    Text("Teach what matters", style = MaterialTheme.typography.headlineMedium)
                }
                TextButton(onClick = onDone) { Text("Close") }
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
                            Text("You’re all caught up", style = MaterialTheme.typography.headlineMedium)
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
                    Text("Return to activity")
                }
            } else {
                DecisionButton(
                    label = "This matters",
                    subtitle = "Remember this as important",
                    accent = Violet400,
                    filled = true,
                    onClick = { onImportant(event) },
                )
                Spacer(Modifier.height(10.dp))
                DecisionButton(
                    label = "This can wait",
                    subtitle = "Remember that this can wait",
                    accent = Mint500,
                    filled = false,
                    onClick = { onNotImportant(event) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(onClick = { onSkip(event) }) { Text("Skip this one") }
                }
            }
        }
    }
}

@Composable
private fun ReviewDecisionCard(event: NotificationEventEntity) {
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
private fun DecisionButton(
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
private fun EventRow(
    event: NotificationEventEntity,
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
                            ) { Text("Important") }
                            TextButton(
                                onClick = { onFeedback(event.notificationKey, false) },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) { Text("Not important") }
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

@Composable
private fun PriorityPill(priority: String, color: Color) {
    Surface(color = color.copy(alpha = 0.13f), shape = CircleShape) {
        Text(
            text = if (priority == "SILENT") "QUIET" else priority,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}




@Composable
private fun SimpleSummaryScreen(
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
                eyebrow = "YOUR HELPER",
                title = "Summary",
                description = "Only the useful parts—what was handled and what you can improve.",
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
                        Text("Important alerts stay protected", style = MaterialTheme.typography.titleMedium)
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
private fun PersonalizationSummaryCard(state: MainUiState) {
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



private fun com.attentionos.domain.NotificationCategory.readableUiName(): String =
    name.lowercase().replaceFirstChar(Char::titlecase)


@Composable
private fun ControlStatusCard(
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
                title = "Notification access",
                value = if (hasAccess) "Connected" else "Not connected",
                positive = hasAccess,
            )
            SoftDivider()
            StatusLine(
                title = "Attention Mode",
                value = if (state.settings.focusMode) "On" else "Off",
                positive = state.settings.focusMode,
            )
            SoftDivider()
            StatusLine(
                title = "Personalization",
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
private fun StatusLine(
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

@Composable
private fun SettingsScreen(
    state: MainUiState,
    hasAccess: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onStoreContentChanged: (Boolean) -> Unit,
    onLearningChanged: (Boolean) -> Unit,
    onCriticalSoundChanged: (Boolean) -> Unit,
    onCriticalVibrationChanged: (Boolean) -> Unit,
    onHighSoundChanged: (Boolean) -> Unit,
    onHighVibrationChanged: (Boolean) -> Unit,
    onMediumSoundChanged: (Boolean) -> Unit,
    onMediumVibrationChanged: (Boolean) -> Unit,
    onReminderChanged: (Boolean) -> Unit,
    onReminderTimesChanged: (Set<Int>) -> Unit,
    onDarkThemeChanged: (Boolean) -> Unit,
    onMotionChanged: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRetentionChanged: (Int) -> Unit,
    onReplayOnboarding: () -> Unit,
    onExport: () -> Unit,
    onResetPersonalizedModel: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showResetModelDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 18.dp,
            start = 16.dp,
            end = 16.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                eyebrow = "CONTROL CENTER",
                title = "Settings",
                description = "Every behavior, clearly under your control.",
                horizontalPadding = 4.dp,
            )
        }
        item {
            ControlStatusCard(
                state = state,
                hasAccess = hasAccess,
            )
        }
        item {
            SettingsGroup(title = "Core behavior") {
                ToggleSettingRow(
                    icon = Icons.Default.Lock,
                    title = "Attention Mode",
                    subtitle = if (state.settings.focusMode) {
                        "Your helper manages sound and vibration; nothing is hidden"
                    } else {
                        "Off — source apps control their normal effects"
                    },
                    checked = state.settings.focusMode,
                    onCheckedChange = onFocusChanged,
                )
            }
        }
        item {
            SettingsGroup(title = "Access") {
                ActionSettingRow(
                    icon = Icons.Default.Notifications,
                    title = "Notification access",
                    subtitle = if (hasAccess) "Active" else "Required for classification",
                    valueColor = if (hasAccess) Mint500 else Sun500,
                    onClick = onOpenNotificationAccess,
                )
                SoftDivider()
                ActionSettingRow(
                    icon = Icons.Default.Info,
                    title = "Replay guided setup",
                    subtitle = "Review guarantees, controls, and the safety test",
                    onClick = onReplayOnboarding,
                )
            }
        }
        item {
            SettingsGroup(title = "Interruption behavior") {
                InterruptionPriorityRow(
                    priority = "Critical",
                    description = "Security, safety and immediate action",
                    sound = state.settings.criticalSound,
                    vibration = state.settings.criticalVibration,
                    onSoundChanged = onCriticalSoundChanged,
                    onVibrationChanged = onCriticalVibrationChanged,
                )
                HorizontalDivider()
                InterruptionPriorityRow(
                    priority = "High",
                    description = "Important and time-sensitive",
                    sound = state.settings.highSound,
                    vibration = state.settings.highVibration,
                    onSoundChanged = onHighSoundChanged,
                    onVibrationChanged = onHighVibrationChanged,
                )
                HorizontalDivider()
                InterruptionPriorityRow(
                    priority = "Medium",
                    description = "Useful, but usually able to wait",
                    sound = state.settings.mediumSound,
                    vibration = state.settings.mediumVibration,
                    onSoundChanged = onMediumSoundChanged,
                    onVibrationChanged = onMediumVibrationChanged,
                )
                Text(
                    "Low and silent priorities never make AttentionOS sound or vibrate. The original notifications always remain visible.",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SettingsGroup(title = "Privacy & learning") {
                ToggleSettingRow(
                    icon = Icons.Default.Star,
                    title = "Local learning",
                    subtitle = "Remember what you mark Important or Can wait",
                    checked = state.settings.learningEnabled,
                    onCheckedChange = onLearningChanged,
                )
                HorizontalDivider()
                ToggleSettingRow(
                    icon = Icons.Default.Lock,
                    title = "Store message content",
                    subtitle = if (state.settings.storeContent) {
                        "Raw title and text stay in the local database"
                    } else {
                        "Off — only private features and hashes are kept"
                    },
                    checked = state.settings.storeContent,
                    onCheckedChange = onStoreContentChanged,
                )
                HorizontalDivider()
                ActionSettingRow(
                    icon = Icons.Default.Delete,
                    title = "Reset personalization",
                    subtitle = if (state.personalizedModel.exampleCount == 0) {
                        "No preferences learned yet"
                    } else {
                        "Clear ${state.personalizedModel.exampleCount} learned corrections"
                    },
                    valueColor = Coral500,
                    onClick = { showResetModelDialog = true },
                )
            }
        }
        item {
            SettingsGroup(title = "Appearance & effects") {
                ToggleSettingRow(
                    icon = Icons.Default.Star,
                    title = "Dark appearance",
                    subtitle = if (state.settings.darkTheme) {
                        "Midnight theme is active"
                    } else {
                        "Light theme is active"
                    },
                    checked = state.settings.darkTheme,
                    onCheckedChange = onDarkThemeChanged,
                )
                SoftDivider()
                ToggleSettingRow(
                    icon = Icons.Default.CheckCircle,
                    title = "Motion effects",
                    subtitle = if (state.settings.motionEnabled) {
                        "Smooth transitions, graphs, and status effects"
                    } else {
                        "Reduced motion across the interface"
                    },
                    checked = state.settings.motionEnabled,
                    onCheckedChange = onMotionChanged,
                )
            }
        }
        item {
            SettingsGroup(title = "Review reminders") {
                ToggleSettingRow(
                    icon = Icons.Default.Notifications,
                    title = "Daily review reminders",
                    subtitle = if (state.settings.reviewReminderEnabled) {
                        val count = state.settings.reviewReminderTimes.size
                        "$count quiet ${if (count == 1) "reminder" else "reminders"} each day"
                    } else {
                        "Off — review whenever you choose"
                    },
                    checked = state.settings.reviewReminderEnabled,
                    onCheckedChange = {
                        onReminderChanged(it)
                        if (it) onRequestNotificationPermission()
                    },
                )
                if (state.settings.reviewReminderEnabled) {
                    SoftDivider()
                    ReminderScheduleEditor(
                        times = state.settings.reviewReminderTimes,
                        onTimesChanged = onReminderTimesChanged,
                    )
                }
            }
        }
        item {
            SettingsGroup(title = "Data retention") {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Automatically delete decision history",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7, 30, 90).forEach { days ->
                            FilterChip(
                                selected = state.settings.retentionDays == days,
                                onClick = { onRetentionChanged(days) },
                                label = { Text("$days days") },
                            )
                        }
                    }
                }
            }
        }
        item {
            SettingsGroup(title = "Your data") {
                ActionSettingRow(
                    icon = Icons.Default.Share,
                    title = "Export learning data",
                    subtitle = "JSON Lines, with hashed sender identities",
                    onClick = onExport,
                )
                HorizontalDivider()
                ActionSettingRow(
                    icon = Icons.Default.Delete,
                    title = "Delete all local data",
                    subtitle = "Decisions, memory, and training examples",
                    valueColor = Coral500,
                    onClick = { showDeleteDialog = true },
                )
            }
        }
        item {
            Text(
                "AttentionOS 0.1 · On-device by design",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete all local data?") },
            text = {
                Text(
                    "This permanently removes your decision history, learned sender memory, and training examples. Settings stay unchanged.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showResetModelDialog) {
        AlertDialog(
            onDismissRequest = { showResetModelDialog = false },
            title = { Text("Reset personalization?") },
            text = {
                Text(
                    "This clears only your personalized preferences and progress. " +
                        "Notification history and exported training examples remain.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetModelDialog = false
                        onResetPersonalizedModel()
                    },
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetModelDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderScheduleEditor(
    times: Set<Int>,
    onTimesChanged: (Set<Int>) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val pickerState = rememberTimePickerState(
        initialHour = 19,
        initialMinute = 0,
        is24Hour = false,
    )
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Reminder schedule", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Choose any time · up to 6 quiet reminders daily",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
            ) {
                Text(
                    "${times.size}/6",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        times.sorted().forEach { minuteOfDay ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(Mint500, CircleShape),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        formatReminderTime(minuteOfDay),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(
                        onClick = { onTimesChanged(times - minuteOfDay) },
                        enabled = times.size > 1,
                    ) {
                        Text("Remove")
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { showPicker = true },
            enabled = times.size < TimeConstants.MAX_DAILY_REMINDERS,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(if (times.size < TimeConstants.MAX_DAILY_REMINDERS) "Add another time" else "Six reminders maximum")
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Add reminder time") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val minuteOfDay = pickerState.hour * 60 + pickerState.minute
                        onTimesChanged(times + minuteOfDay)
                        showPicker = false
                    },
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
        )
    }
}

private fun formatReminderTime(minuteOfDay: Int): String {
    val normalized = minuteOfDay.coerceIn(0, 24 * 60 - 1)
    val hour = normalized / 60
    val minute = normalized % 60
    val display = when (val twelveHour = hour % 12) {
        0 -> 12
        else -> twelveHour
    }
    return "$display:${minute.toString().padStart(2, '0')} ${if (hour < 12) "AM" else "PM"}"
}


@Composable
private fun InterruptionPriorityRow(
    priority: String,
    description: String,
    sound: Boolean,
    vibration: Boolean,
    onSoundChanged: (Boolean) -> Unit,
    onVibrationChanged: (Boolean) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
        Text(priority, style = MaterialTheme.typography.titleMedium)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilterChip(
                selected = sound,
                onClick = { onSoundChanged(!sound) },
                label = { Text(if (sound) "Sound on" else "Sound off") },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = vibration,
                onClick = { onVibrationChanged(!vibration) },
                label = { Text(if (vibration) "Vibrate on" else "Vibrate off") },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Text(
            title.uppercase(),
            modifier = Modifier.padding(start = 8.dp, bottom = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun ToggleSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon, Forest800)
        Spacer(Modifier.width(12.dp))
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
            colors = modernSwitchColors(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun ActionSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    valueColor: Color = Forest800,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon, valueColor)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = valueColor)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingIcon(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ElevatedPanel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SoftDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun modernSwitchColors(accent: Color) = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = accent,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
)

@Composable
private fun ScreenHeader(
    eyebrow: String,
    title: String,
    description: String,
    horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 6.dp),
    ) {
        Text(
            eyebrow,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            title,
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun rememberNotificationAccess(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return remember(refresh) {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        enabled.split(':').any { component ->
            component.startsWith("${context.packageName}/")
        }
    }
}


private fun priorityColor(priority: String): Color = when (priority) {
    "CRITICAL" -> Coral500
    "HIGH" -> Sun500
    "MEDIUM" -> Ice500
    else -> Mint500
}

private fun formatEventTime(timestamp: Long): String =
    DateTimeFormatter.ofPattern("h:mm a")
        .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
