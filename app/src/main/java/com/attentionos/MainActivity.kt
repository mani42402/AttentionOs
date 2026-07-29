package com.attentionos

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.attentionos.data.repository.StorageSummary
import com.attentionos.service.ReviewReminderWorker
import com.attentionos.ui.MainUiState
import com.attentionos.ui.MainViewModel
import com.attentionos.ui.UiEvent
import com.attentionos.ui.components.rememberNotificationAccess
import com.attentionos.ui.home.DashboardScreen
import com.attentionos.ui.insights.InsightsScreen
import com.attentionos.ui.navigation.AppDestination
import com.attentionos.ui.navigation.AttentionNavHost
import com.attentionos.ui.navigation.HelperBottomBar
import com.attentionos.ui.navigation.HelperNavigationRail
import com.attentionos.ui.navigation.navigateToTab
import com.attentionos.ui.onboarding.OnboardingScreen
import com.attentionos.ui.review.ActivityScreen
import com.attentionos.ui.settings.SettingsScreen
import com.attentionos.ui.theme.AttentionTheme
import com.attentionos.ui.theme.AppCanvas
import com.attentionos.ui.theme.ThemeMode
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

            // The UI renders stored notification titles and bodies. Without FLAG_SECURE those
            // appear in the recents thumbnail, in screenshots, and to screen recorders — which
            // would contradict the app's central promise. Default on, user-overridable.
            LaunchedEffect(uiState.settings.screenSecurity) {
                if (uiState.settings.screenSecurity) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE,
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            AttentionTheme(
                themeMode = ThemeMode.fromStorage(uiState.settings.themeMode),
                dynamicColor = uiState.settings.dynamicColor,
                motionEnabled = uiState.settings.motionEnabled,
            ) {
                val reviewRequest by openReviewRequest.collectAsStateWithLifecycle()
                val storage by viewModel.storage.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        when (event) {
                            is UiEvent.ExportReady -> {
                                shareExport(event.result.uri, event.result.count)
                                viewModel.confirmExported(event.result.exampleIds)
                            }
                            UiEvent.NothingToExport ->
                                snackbar.showSnackbar("No learned examples to export yet.")
                            UiEvent.DataDeleted -> snackbar.showSnackbar("All local attention data deleted.")
                            UiEvent.PersonalModelReset ->
                                snackbar.showSnackbar("Personalization reset. New choices start fresh.")
                        }
                    }
                }

                val darkCanvas = when (ThemeMode.fromStorage(uiState.settings.themeMode)) {
                    ThemeMode.System -> androidx.compose.foundation.isSystemInDarkTheme()
                    ThemeMode.Light -> false
                    ThemeMode.Dark -> true
                }
                AppCanvas(dark = darkCanvas) {
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
                        onThemeModeChanged = viewModel::setThemeMode,
                        onDynamicColorChanged = viewModel::setDynamicColor,
                        onMotionChanged = viewModel::setMotionEnabled,
                        onScreenSecurityChanged = viewModel::setScreenSecurity,
                        onRequestNotificationPermission = ::requestNotificationPermission,
                        onRetentionChanged = viewModel::setRetentionDays,
                        onReplayOnboarding = viewModel::replayOnboarding,
                        onExport = viewModel::exportTrainingData,
                        onResetPersonalizedModel = viewModel::resetPersonalizedModel,
                        onRunTestLab = viewModel::runTestLab,
                        onDelete = viewModel::deleteAllData,
                        storage = storage,
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
    onThemeModeChanged: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onMotionChanged: (Boolean) -> Unit,
    onScreenSecurityChanged: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRetentionChanged: (Int) -> Unit,
    onReplayOnboarding: () -> Unit,
    onExport: () -> Unit,
    onResetPersonalizedModel: () -> Unit,
    onRunTestLab: () -> Unit,
    onDelete: () -> Unit,
    storage: StorageSummary,
    onFeedback: (String, Boolean) -> Unit,
    onOpenNotificationAccess: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = AppDestination.fromRoute(backStackEntry?.destination?.route)
    LaunchedEffect(reviewRequest) {
        if (reviewRequest > 0) navController.navigateToTab(AppDestination.ACTIVITY)
    }
    val hasAccess = rememberNotificationAccess()

    // A bottom dock on a tablet or unfolded device puts the primary controls far from the eye
    // and spends height the content wants. The rail is the same four items, rotated.
    val wide = with(LocalConfiguration.current) { screenWidthDp >= WIDE_WINDOW_DP }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!wide) {
                HelperBottomBar(
                    selected = destination,
                    onSelected = navController::navigateToTab,
                )
            }
        },
    ) { scaffoldPadding ->
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                HelperNavigationRail(
                    selected = destination,
                    onSelected = navController::navigateToTab,
                )
            }
            // The weight has to sit on a wrapper: a weight modifier hands the child an exact
            // width, which would override any cap placed alongside it.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.TopCenter,
            ) {
            AttentionNavHost(
                navController = navController,
                // Capped and centred: a paragraph that spans a tablet is measurably harder to
                // read, and these layouts are one column by design.
                modifier = Modifier
                    .widthIn(max = READING_WIDTH_MAX)
                    .fillMaxSize()
                    .padding(bottom = scaffoldPadding.calculateBottomPadding()),
                home = {
                    DashboardScreen(
                        state = state,
                        hasAccess = hasAccess,
                        onFocusChanged = onFocusChanged,
                        onOpenNotificationAccess = onOpenNotificationAccess,
                        onSeeActivity = { navController.navigateToTab(AppDestination.ACTIVITY) },
                    )
                },
                review = {
                    ActivityScreen(
                        state = state,
                        onFeedback = onFeedback,
                        reviewRequest = reviewRequest,
                    )
                },
                insights = {
                    InsightsScreen(
                        state = state,
                        onReview = { navController.navigateToTab(AppDestination.ACTIVITY) },
                        onRunTestLab = onRunTestLab,
                    )
                },
                settings = {
                    SettingsScreen(
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
                        onThemeModeChanged = onThemeModeChanged,
                        onDynamicColorChanged = onDynamicColorChanged,
                        onMotionChanged = onMotionChanged,
                        onScreenSecurityChanged = onScreenSecurityChanged,
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        onRetentionChanged = onRetentionChanged,
                        onReplayOnboarding = onReplayOnboarding,
                        onExport = onExport,
                        onResetPersonalizedModel = onResetPersonalizedModel,
                        onDelete = onDelete,
                        storage = storage,
                    )
                },
            )
            }
        }
    }
}

/**
 * The Material breakpoint between a compact and a medium window.
 *
 * Chosen over a window-size-class dependency because the only decision being made here is dock
 * versus rail, and `screenWidthDp` already tracks folding and rotation.
 */
private const val WIDE_WINDOW_DP = 600

/** Roughly 90 characters at the body size: past this, line length starts costing comprehension. */
private val READING_WIDTH_MAX = 720.dp
