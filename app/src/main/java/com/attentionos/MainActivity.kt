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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.attentionos.ui.MainUiState
import com.attentionos.ui.MainViewModel
import com.attentionos.ui.UiEvent
import com.attentionos.ui.theme.AttentionTheme
import com.attentionos.ui.theme.Forest950
import com.attentionos.ui.theme.LocalMotionEnabled
import com.attentionos.service.ReviewReminderWorker
import com.attentionos.ui.components.rememberNotificationAccess
import com.attentionos.ui.home.DashboardScreen
import com.attentionos.ui.insights.SimpleSummaryScreen
import com.attentionos.ui.navigation.AppDestination
import com.attentionos.ui.navigation.HelperBottomBar
import com.attentionos.ui.onboarding.OnboardingScreen
import com.attentionos.ui.review.ActivityScreen
import com.attentionos.ui.settings.SettingsScreen
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























































