package com.attentionos.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.attentionos.core.common.TimeConstants
import com.attentionos.core.di.AppContainer
import com.attentionos.data.db.NotificationListItem
import com.attentionos.data.repository.AttentionTestResult
import com.attentionos.data.repository.UserAction
import com.attentionos.data.settings.AppSettings
import com.attentionos.training.ExportResult
import com.attentionos.training.PersonalizedModelProgress
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val settings: AppSettings = AppSettings(),
    val events: List<NotificationListItem> = emptyList(),
    val receivedToday: Int = 0,
    val importantToday: Int = 0,
    val queuedToday: Int = 0,
    val trainingExamples: Int = 0,
    val personalizedModel: PersonalizedModelProgress = PersonalizedModelProgress(),
    val averageAnalysisMillis: Double? = null,
    val testLab: TestLabUiState = TestLabUiState(),
    val isLoading: Boolean = true,
) {
    /**
     * Rough attention saved, assuming a held-back notification costs about
     * [SECONDS_PER_INTERRUPTION] of refocusing.
     *
     * Rounds rather than truncating: integer division reported "0 min" until three
     * notifications had been queued, so the first two looked like they saved nothing.
     */
    val estimatedMinutesSaved: Int
        get() = ((queuedToday * SECONDS_PER_INTERRUPTION) / 60f).roundToInt()
    val unreviewedEvents: List<NotificationListItem>
        get() = events.filter {
            it.action != UserAction.IMPORTANT.name &&
                it.action != UserAction.NOT_IMPORTANT.name &&
                it.hasEmbedding
        }
    val pilotDaysElapsed: Int
        get() = if (settings.pilotStartedAt <= 0L) {
            0
        } else {
            (
                (System.currentTimeMillis() - settings.pilotStartedAt)
                    .coerceAtLeast(0L) / TimeConstants.DAY_MILLIS
                ).toInt() + 1
        }
    val pilotComplete: Boolean
        get() = settings.pilotStartedAt > 0L &&
            System.currentTimeMillis() - settings.pilotStartedAt >=
            TimeConstants.PILOT_DURATION_MILLIS
    val personalModelActive: Boolean get() = personalizedModel.isActive && pilotComplete

    private companion object {
        const val SECONDS_PER_INTERRUPTION = 24
    }
}

data class TestLabUiState(
    val isRunning: Boolean = false,
    val results: List<AttentionTestResult> = emptyList(),
    val error: String? = null,
)

sealed interface UiEvent {
    data class ExportReady(val result: ExportResult.Ready) : UiEvent
    data object NothingToExport : UiEvent
    data object DataDeleted : UiEvent
    data object PersonalModelReset : UiEvent
}

private data class DashboardStats(
    val received: Int,
    val important: Int,
    val queued: Int,
    val training: Int,
    val personalizedModel: PersonalizedModelProgress,
    val averageAnalysisMillis: Double?,
)

private data class LearningStats(
    val training: Int,
    val personalizedModel: PersonalizedModelProgress,
    val averageAnalysisMillis: Double?,
)

class MainViewModel(private val container: AppContainer) : ViewModel() {
    private val testLabState = MutableStateFlow(TestLabUiState())

    /**
     * Start of the current day, re-emitted when the day changes.
     *
     * This used to be captured once when the ViewModel was constructed, so an app left resident
     * past midnight kept counting "today" from the previous day until the process died. Sleeps
     * until the next midnight rather than polling.
     */
    private val dayStart: Flow<Long> = flow {
        while (true) {
            val startOfToday = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            emit(startOfToday)
            val untilMidnight = startOfToday + TimeConstants.DAY_MILLIS -
                System.currentTimeMillis()
            delay(untilMidnight.coerceAtLeast(MINIMUM_TICK_MILLIS))
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dashboardStats: Flow<DashboardStats> = dayStart.flatMapLatest { since ->
        combine(
            container.attentionRepository.receivedSince(since),
            container.attentionRepository.importantSince(since),
            container.attentionRepository.queuedSince(since),
            combine(
                container.attentionRepository.trainingCount(),
                container.attentionRepository.personalizedModelProgress(),
                container.attentionRepository.averageAnalysisMillis(),
            ) { training, personalizedModel, averageAnalysisMillis ->
                LearningStats(training, personalizedModel, averageAnalysisMillis)
            },
        ) { received, important, queued, learning ->
            DashboardStats(
                received,
                important,
                queued,
                learning.training,
                learning.personalizedModel,
                learning.averageAnalysisMillis,
            )
        }
    }

    val uiState: StateFlow<MainUiState> = combine(
        container.settingsRepository.settings,
        container.attentionRepository.recentEvents(),
        dashboardStats,
        testLabState,
    ) { settings, events, counts, testLab ->
        MainUiState(
            settings = settings,
            events = events,
            receivedToday = counts.received,
            importantToday = counts.important,
            queuedToday = counts.queued,
            trainingExamples = counts.training,
            personalizedModel = counts.personalizedModel,
            averageAnalysisMillis = counts.averageAnalysisMillis,
            testLab = testLab,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    val events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)

    fun setFocusMode(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setFocusMode(enabled) }
    }

    fun setStoreContent(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setStoreContent(enabled) }
    }

    fun setLearningEnabled(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setLearningEnabled(enabled) }
    }

    fun setCriticalSound(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setCriticalSound(enabled) }
    }

    fun setCriticalVibration(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setCriticalVibration(enabled) }
    }

    fun setHighSound(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setHighSound(enabled) }
    }

    fun setHighVibration(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setHighVibration(enabled) }
    }

    fun setMediumSound(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setMediumSound(enabled) }
    }

    fun setMediumVibration(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setMediumVibration(enabled) }
    }

    fun setReviewReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setReviewReminderEnabled(enabled)
        }
    }

    fun setReviewReminderTimes(times: Set<Int>) {
        viewModelScope.launch { container.settingsRepository.setReviewReminderTimes(times) }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setDarkTheme(enabled) }
    }

    fun setMotionEnabled(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setMotionEnabled(enabled) }
    }

    fun setScreenSecurity(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setScreenSecurity(enabled) }
    }

    /** Called once the share sheet has actually been handed the file. */
    fun confirmExported(ids: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) { container.exportManager.confirmExported(ids) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { container.settingsRepository.completeOnboarding() }
    }

    fun replayOnboarding() {
        viewModelScope.launch { container.settingsRepository.replayOnboarding() }
    }

    fun setRetentionDays(days: Int) {
        viewModelScope.launch { container.settingsRepository.setRetentionDays(days) }
    }

    fun exportTrainingData() {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = container.exportManager.exportJsonLines()) {
                ExportResult.Empty -> events.emit(UiEvent.NothingToExport)
                is ExportResult.Ready -> events.emit(UiEvent.ExportReady(result))
            }
        }
    }

    fun deleteAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            container.panicWipe()
            events.emit(UiEvent.DataDeleted)
        }
    }

    fun resetPersonalizedModel() {
        viewModelScope.launch(Dispatchers.IO) {
            container.attentionRepository.resetPersonalizedModel()
            events.emit(UiEvent.PersonalModelReset)
        }
    }

    fun runTestLab() {
        if (testLabState.value.isRunning) return
        viewModelScope.launch(Dispatchers.Default) {
            testLabState.value = testLabState.value.copy(isRunning = true, error = null)
            runCatching {
                container.attentionRepository.runTestLab(container.currentSettings.value)
            }.onSuccess { results ->
                testLabState.value = TestLabUiState(results = results)
            }.onFailure {
                testLabState.value = TestLabUiState(
                    error = "The local test could not complete. Try again.",
                )
            }
        }
    }

    fun submitFeedback(notificationKey: String, important: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            container.attentionRepository.recordAction(
                notificationKey = notificationKey,
                action = if (important) UserAction.IMPORTANT else UserAction.NOT_IMPORTANT,
                settings = container.currentSettings.value,
            )
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(container) as T
        }
    }

    private companion object {
        /** Guards against a zero or negative sleep if the clock moves during the wait. */
        const val MINIMUM_TICK_MILLIS = 1_000L
    }
}
