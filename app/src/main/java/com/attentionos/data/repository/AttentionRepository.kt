package com.attentionos.data.repository

import android.util.Log
import com.attentionos.BuildConfig
import com.attentionos.ai.StaticEmbeddingAnalyzer
import com.attentionos.core.common.TimeConstants
import com.attentionos.data.db.AttentionDao
import com.attentionos.data.db.NotificationEventEntity
import com.attentionos.data.db.NotificationListItem
import com.attentionos.data.db.PersonalizedModelEntity
import com.attentionos.data.db.TrainingExampleEntity
import com.attentionos.data.db.UserMemoryEntity
import com.attentionos.data.settings.AppSettings
import com.attentionos.domain.AttentionContext
import com.attentionos.domain.AttentionDecision
import com.attentionos.domain.AttentionPolicy
import com.attentionos.domain.AttentionPriority
import com.attentionos.domain.NotificationCategory
import com.attentionos.domain.NotificationSignal
import com.attentionos.domain.PriorityEngine
import com.attentionos.domain.UserMemory
import com.attentionos.security.SenderHasher
import com.attentionos.security.SenderIdentity
import com.attentionos.training.Centroids
import com.attentionos.training.EmbeddingCodec
import com.attentionos.training.ModelWeightsCodec
import com.attentionos.training.PersonalizedAttentionModel
import com.attentionos.training.PersonalizedDecisionPolicy
import com.attentionos.training.PersonalizedModelProgress
import com.attentionos.training.PersonalizedModelState
import com.attentionos.training.TrainingSample
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AttentionRepository(
    private val dao: AttentionDao,
    private val priorityEngine: PriorityEngine,
    private val senderHasher: SenderHasher,
) {
    private val modelMutex = Mutex()
    private var modelLoaded = false
    private var cachedModel: PersonalizedModelState? = null
    private var cachedCentroids: Centroids? = null

    fun recentEvents(): Flow<List<NotificationListItem>> = dao.observeRecent()
    fun importantEvents(): Flow<List<NotificationEventEntity>> = dao.observeImportant()
    fun queuedEvents(): Flow<List<NotificationEventEntity>> = dao.observeQueued()
    fun receivedSince(since: Long): Flow<Int> = dao.observeReceivedSince(since)
    fun importantSince(since: Long): Flow<Int> = dao.observeImportantSince(since)
    fun queuedSince(since: Long): Flow<Int> = dao.observeQueuedSince(since)
    fun trainingCount(): Flow<Int> = dao.observeTrainingCount()

    /**
     * What is actually on disk right now, for the privacy dashboard.
     *
     * The app asks users to trust a claim about local storage; this lets them check it instead.
     */
    fun storageSummary(databaseBytes: () -> Long): Flow<StorageSummary> = combine(
        dao.observeEventCount(),
        dao.observeSenderCount(),
        dao.observeTrainingCount(),
        dao.observeStoredContentCount(),
        dao.observeOldestEventAt(),
    ) { events, senders, training, withContent, oldest ->
        StorageSummary(
            notificationCount = events,
            senderCount = senders,
            trainingExampleCount = training,
            storedContentCount = withContent,
            oldestEventAt = oldest?.takeIf { it > 0 },
            databaseBytes = databaseBytes(),
        )
    }
    /** Average inference latency over the recent window shown in the UI. */
    fun averageAnalysisMillis(): Flow<Double?> = dao.observeAverageAnalysisMillis(
        since = System.currentTimeMillis() - LATENCY_WINDOW_MILLIS,
    )
    fun personalizedModelProgress(): Flow<PersonalizedModelProgress> =
        dao.observePersonalizedModel().map { model ->
            PersonalizedModelProgress(
                positiveCount = model?.positiveCount ?: 0,
                negativeCount = model?.negativeCount ?: 0,
                evaluationCount = model?.evaluationCount ?: 0,
                personalCorrectCount = model?.personalCorrectCount ?: 0,
                baselineCorrectCount = model?.baselineCorrectCount ?: 0,
                importantEvaluationCount = model?.importantEvaluationCount ?: 0,
                importantCorrectCount = model?.importantCorrectCount ?: 0,
                notImportantEvaluationCount = model?.notImportantEvaluationCount ?: 0,
                falseImportantCount = model?.falseImportantCount ?: 0,
            )
        }

    suspend fun runTestLab(settings: AppSettings): List<AttentionTestResult> {
        val now = System.currentTimeMillis()
        val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
        val context = AttentionContext(settings.focusMode, hour)
        return testScenarios.mapIndexed { index, scenario ->
            val signal = NotificationSignal(
                packageName = scenario.packageName,
                title = scenario.title,
                text = scenario.text,
                postedAt = now + index,
                isConversation = scenario.isConversation,
                isOngoing = false,
                categoryHint = scenario.categoryHint,
            )
            val startedAt = System.nanoTime()
            val base = priorityEngine.decide(signal, context, memory = null)
            val personalization = if (settings.learningEnabled) {
                personalize(
                    base,
                    signal,
                    context,
                    memory = null,
                    allowActivation = pilotPeriodComplete(settings),
                )
            } else {
                PersonalizationOutcome(base, null, false)
            }
            AttentionTestResult(
                name = scenario.name,
                category = personalization.decision.category,
                basePriority = base.priority,
                finalPriority = personalization.decision.priority,
                baseScore = base.score,
                personalProbability = personalization.probabilityImportant,
                personalModelApplied = personalization.applied,
                safetyProtected = isSafetyProtected(base, signal),
                durationMillis = ((System.nanoTime() - startedAt) / 1_000_000L)
                    .coerceAtLeast(0L),
            )
        }
    }

    suspend fun processPosted(
        key: String,
        appLabel: String,
        signal: NotificationSignal,
        settings: AppSettings,
    ): AttentionDecision {
        val senderHash = senderHasher.hash(
            signal.conversationId ?: SenderIdentity.of(
                packageName = signal.packageName,
                personKey = null,
                shortcutId = null,
                title = signal.title,
            ),
        )
        val memoryEntity = dao.memory(senderHash)
        val memory = memoryEntity?.toDomain()
        val hour = Instant.ofEpochMilli(signal.postedAt)
            .atZone(ZoneId.systemDefault())
            .hour
        val context = AttentionContext(settings.focusMode, hour)
        val analysisStartedAt = System.nanoTime()
        val baseDecision = priorityEngine.decide(
            signal = signal,
            context = context,
            memory = memory,
        )
        val analysisDurationMillis = (
            (System.nanoTime() - analysisStartedAt) / 1_000_000L
            ).coerceAtLeast(0L)
        val personalization = if (settings.learningEnabled) {
            personalize(
                baseDecision,
                signal,
                context,
                memory,
                allowActivation = pilotPeriodComplete(settings),
            )
        } else {
            PersonalizationOutcome(baseDecision, null, false)
        }
        val decision = personalization.decision

        dao.insertEvent(
            NotificationEventEntity(
                notificationKey = key,
                packageName = signal.packageName,
                appLabel = appLabel,
                senderHash = senderHash,
                title = signal.title.takeIf { settings.storeContent },
                message = signal.text.takeIf { settings.storeContent },
                postedAt = signal.postedAt,
                priority = decision.priority.name,
                category = decision.category.name,
                score = decision.score,
                explanation = decision.explanation,
                queued = decision.shouldQueue,
                embeddingQ8 = EmbeddingCodec.encode(decision.semanticEmbedding),
                languageModelVersion = decision.languageModelVersion,
                contextHour = hour,
                focusModeAtDecision = settings.focusMode,
                senderImportanceAtDecision = memory?.importanceScore ?: 0.5f,
                senderOpenRateAtDecision = memory?.openRate ?: 0.5f,
                baseScoreAtDecision = baseDecision.score,
                semanticUrgency = baseDecision.semanticUrgency,
                personalProbability = personalization.probabilityImportant,
                personalModelApplied = personalization.applied,
                analysisDurationMillis = analysisDurationMillis,
            ),
        )
        return decision
    }

    suspend fun recordAction(
        notificationKey: String,
        action: UserAction,
        settings: AppSettings,
        actedAt: Long = System.currentTimeMillis(),
    ) {
        val event = dao.eventByKey(notificationKey) ?: return
        val isExplicit = action == UserAction.IMPORTANT || action == UserAction.NOT_IMPORTANT
        val previousAction = event.action?.let { runCatching { UserAction.valueOf(it) }.getOrNull() }
        val previousWasExplicit =
            previousAction == UserAction.IMPORTANT || previousAction == UserAction.NOT_IMPORTANT
        if (previousWasExplicit || (previousAction != null && !isExplicit)) return
        val replacingPassiveAction = previousAction != null

        dao.markAction(notificationKey, action.name, actedAt)
        if (!settings.learningEnabled) return

        val previous = dao.memory(event.senderHash)
        val oldInteractions = previous?.interactionCount ?: 0
        val newInteractions = if (replacingPassiveAction) {
            oldInteractions.coerceAtLeast(1)
        } else {
            oldInteractions + 1
        }
        val opened = action == UserAction.OPENED || action == UserAction.IMPORTANT
        val previousOpenCount = (previous?.openCount ?: 0) -
            if (previousAction == UserAction.OPENED) 1 else 0
        val previousDismissCount = (previous?.dismissCount ?: 0) -
            if (previousAction == UserAction.DISMISSED) 1 else 0
        val openCount = previousOpenCount.coerceAtLeast(0) + if (opened) 1 else 0
        val dismissCount = previousDismissCount.coerceAtLeast(0) + if (opened) 0 else 1
        val responseSeconds = ((actedAt - event.postedAt).coerceAtLeast(0L) / 1_000L)
        val oldAverage = previous?.averageResponseSeconds ?: responseSeconds
        val newAverage = if (replacingPassiveAction) {
            oldAverage
        } else if (opened) {
            ((oldAverage * oldInteractions) + responseSeconds) /
                newInteractions.coerceAtLeast(1)
        } else {
            oldAverage
        }
        val observedImportance = if (action == UserAction.IMPORTANT) {
            1f
        } else if (action == UserAction.NOT_IMPORTANT) {
            0.05f
        } else if (opened) {
            if (responseSeconds <= 60) 1f else 0.75f
        } else {
            0.15f
        }
        val oldImportance = previous?.importanceScore ?: 0.5f
        val observationWeight = if (isExplicit) 0.40f else 0.20f

        dao.upsertMemory(
            UserMemoryEntity(
                senderHash = event.senderHash,
                importanceScore = (
                    oldImportance * (1f - observationWeight) +
                        observedImportance * observationWeight
                    )
                    .coerceIn(0.05f, 0.98f),
                averageResponseSeconds = newAverage,
                openCount = openCount,
                dismissCount = dismissCount,
                interactionCount = newInteractions,
                updatedAt = actedAt,
            ),
        )

        val expected = when {
            action == UserAction.IMPORTANT -> "HIGH"
            action == UserAction.NOT_IMPORTANT -> "LOW"
            opened && responseSeconds <= 60 -> "HIGH"
            opened -> "MEDIUM"
            else -> "LOW"
        }
        val example = TrainingExampleEntity(
            sourceEventId = event.id,
            featuresJson = buildFeaturesJson(event, responseSeconds, opened),
            expectedPriority = expected,
            createdAt = actedAt,
            embeddingQ8 = event.embeddingQ8,
            languageModelVersion = event.languageModelVersion,
        )
        if (isExplicit) dao.deleteTrainingForSource(event.id)
        dao.insertTrainingExample(example)
        if (isExplicit) {
            updatePersonalizedModel(
                event = event,
                important = action == UserAction.IMPORTANT,
                updatedAt = actedAt,
            )
        }
        if (BuildConfig.DEBUG) {
            Log.d(
                "AttentionTraining",
                "Labeled example stored: embeddingBytes=${example.embeddingQ8?.size ?: 0}, " +
                    "model=${example.languageModelVersion ?: "fallback"}, label=$expected",
            )
        }
    }

    suspend fun resetPersonalizedModel() {
        modelMutex.withLock {
            dao.deletePersonalizedModel()
            cachedModel = null
            modelLoaded = true
        }
    }

    suspend fun deleteAllUserData() {
        modelMutex.withLock {
            dao.deleteAllUserData()
            cachedModel = null
            modelLoaded = true
        }
    }

    /**
     * Applies the user's retention setting to every table that accumulates personal data.
     *
     * Previously this pruned events and *exported* training rows only, so unexported examples
     * and all sender memory grew without bound no matter what retention the user chose — the
     * setting quietly did not mean what it said. Hard caps bound the worst case even when a
     * device has not been idle enough for this job to run recently.
     */
    suspend fun prune(retentionDays: Int) {
        val now = System.currentTimeMillis()
        val before = now - retentionDays * TimeConstants.DAY_MILLIS
        dao.deleteEventsBefore(before)
        dao.deleteTrainingBefore(before)
        dao.trimTrainingTo(MAX_TRAINING_ROWS)
        dao.deleteMemoryBefore(now - MEMORY_RETENTION_MILLIS)
        dao.trimMemoryTo(MAX_MEMORY_ROWS)
    }

    suspend fun exportableTraining(): List<TrainingExampleEntity> = dao.trainingForExport()

    suspend fun markExported(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.markTrainingExported(ids)
    }

    private fun buildFeaturesJson(
        event: NotificationEventEntity,
        responseSeconds: Long,
        opened: Boolean,
    ): String = buildString(256) {
        append('{')
        append("\"app\":\"").append(event.packageName.jsonEscape()).append("\",")
        append("\"sender_hash\":\"").append(event.senderHash).append("\",")
        append("\"category\":\"").append(event.category).append("\",")
        append("\"prediction\":\"").append(event.priority).append("\",")
        append("\"score\":").append(((event.score * 10_000).roundToLong() / 10_000.0))
        append(',')
        append("\"base_score\":")
            .append(((event.baseScoreAtDecision * 10_000).roundToLong() / 10_000.0))
        append(',')
        append("\"hour\":").append(event.contextHour).append(',')
        append("\"focus_mode\":").append(event.focusModeAtDecision).append(',')
        append("\"sender_importance\":")
            .append(((event.senderImportanceAtDecision * 10_000).roundToLong() / 10_000.0))
        append(',')
        append("\"sender_open_rate\":")
            .append(((event.senderOpenRateAtDecision * 10_000).roundToLong() / 10_000.0))
        append(',')
        append("\"opened\":").append(opened).append(',')
        append("\"response_seconds\":").append(responseSeconds)
        append('}')
    }

    private fun UserMemoryEntity.toDomain(): UserMemory = UserMemory(
        senderHash = senderHash,
        importanceScore = importanceScore,
        openRate = if (interactionCount == 0) 0.5f else openCount.toFloat() / interactionCount,
        averageResponseSeconds = averageResponseSeconds,
        interactionCount = interactionCount,
    )

    private suspend fun personalize(
        base: AttentionDecision,
        signal: NotificationSignal,
        context: AttentionContext,
        memory: UserMemory?,
        allowActivation: Boolean,
    ): PersonalizationOutcome {
        val state = loadPersonalizedModel()
            ?: return PersonalizationOutcome(base, null, false)
        val features = PersonalizedAttentionModel.features(
            embedding = base.semanticEmbedding,
            hourOfDay = context.hourOfDay,
            senderImportance = memory?.importanceScore ?: 0.5f,
            senderOpenRate = memory?.openRate ?: 0.5f,
            focusModeEnabled = context.focusModeEnabled,
            baseScore = base.score,
        ) ?: return PersonalizationOutcome(base, null, false)
        val logisticProbability = PersonalizedAttentionModel.predict(state, features)

        // Before the classifier has enough data, class centroids carry the signal: a
        // nearest-class-mean is stable from a handful of corrections where logistic regression
        // is still noise. Without this the personal model stays inert until 50 corrections plus
        // the evaluation gates, a bar most users never reach — so "it learns from you" never
        // became true for them.
        val centroids = loadCentroids()
        val prototypeProbability = base.semanticEmbedding
            ?.takeIf { centroids != null }
            ?.let { PersonalizedAttentionModel.prototypeScore(it, centroids!!) }

        val probability = blendProbability(
            logistic = logisticProbability,
            prototype = prototypeProbability,
            exampleCount = state.exampleCount,
        )

        // Centroids widen *when* personalization can help, never *whether* it is allowed to.
        // The pilot period and the evaluation gates still hold: nothing here may influence a
        // decision before the shadow period completes.
        val usable = allowActivation &&
            (state.isActive || (prototypeProbability != null && state.hasPrototypeEvidence))
        if (!usable) {
            return PersonalizationOutcome(base, probability, false)
        }
        val safetyProtected = isSafetyProtected(base, signal)
        val decision = PersonalizedDecisionPolicy.apply(
            base = base,
            probabilityImportant = probability,
            context = context,
            safetyProtected = safetyProtected,
        )
        return PersonalizationOutcome(
            decision = decision,
            probabilityImportant = probability,
            applied = decision != base,
        )
    }

    /**
     * Weighted blend of the two personal signals.
     *
     * Centroids dominate while examples are scarce and hand over to the classifier as it earns
     * its keep, so the transition is gradual rather than a step change in behaviour.
     */
    private fun blendProbability(
        logistic: Float,
        prototype: Float?,
        exampleCount: Int,
    ): Float {
        if (prototype == null) return logistic
        val logisticShare = (
            exampleCount.toFloat() / PersonalizedAttentionModel.MIN_EXAMPLES
            ).coerceIn(0f, 1f)
        return logistic * logisticShare + prototype * (1f - logisticShare)
    }

    private suspend fun loadCentroids(): Centroids? =
        modelMutex.withLock {
            if (!modelLoaded) {
                cachedModel = dao.personalizedModel()?.toState()
                modelLoaded = true
            }
            if (cachedCentroids == null) {
                cachedCentroids = dao.personalizedModel()?.let { entity ->
                    val important = ModelWeightsCodec.decodeVector(entity.importantCentroid)
                    val notImportant = ModelWeightsCodec.decodeVector(entity.notImportantCentroid)
                    if (important != null && notImportant != null) {
                        Centroids(important, notImportant)
                    } else {
                        null
                    }
                }
            }
            cachedCentroids
        }

    private suspend fun loadPersonalizedModel(): PersonalizedModelState? =
        modelMutex.withLock {
            if (!modelLoaded) {
                cachedModel = dao.personalizedModel()?.toState()
                modelLoaded = true
            }
            cachedModel
        }

    /**
     * Refits the personal model over every stored correction.
     *
     * Replaces a single gradient step per correction. The embeddings are already persisted, so
     * the whole set can be re-fit each time: it removes the order-dependence and systematic
     * underfitting of one-pass online learning, and at a few thousand examples it costs
     * milliseconds. Evaluation counters are still advanced incrementally, because a shadow
     * prediction is only meaningful against the model that existed when it was made.
     */
    private suspend fun updatePersonalizedModel(
        event: NotificationEventEntity,
        important: Boolean,
        updatedAt: Long,
    ) {
        val features = PersonalizedAttentionModel.features(
            embedding = EmbeddingCodec.decode(event.embeddingQ8),
            hourOfDay = event.contextHour,
            senderImportance = event.senderImportanceAtDecision,
            senderOpenRate = event.senderOpenRateAtDecision,
            focusModeEnabled = event.focusModeAtDecision,
            baseScore = event.baseScoreAtDecision,
        ) ?: return

        modelMutex.withLock {
            if (!modelLoaded) {
                cachedModel = dao.personalizedModel()?.toState()
                modelLoaded = true
            }

            // Advance the shadow-evaluation counters against the pre-update model first: this
            // records how the model that made the prediction actually performed, which is the
            // whole point of the gate.
            val withCounters = PersonalizedAttentionModel.update(
                current = cachedModel,
                features = features,
                important = important,
                predictionBeforeUpdate = event.personalProbability,
                baselineWasImportant = event.baseScoreAtDecision >= HIGH_PRIORITY_THRESHOLD,
            )

            val samples = replaySamples()
            val refitted = if (samples.size >= MIN_REFIT_SAMPLES) {
                PersonalizedAttentionModel.refit(samples, counters = withCounters)
            } else {
                withCounters
            }
            val centroids = PersonalizedAttentionModel.centroids(samples)

            dao.upsertPersonalizedModel(refitted.toEntity(updatedAt, centroids))
            cachedModel = refitted
            cachedCentroids = centroids
            if (BuildConfig.DEBUG) {
                Log.d(
                    "AttentionTraining",
                    "Personal model refit: samples=${samples.size}, " +
                        "positive=${refitted.positiveCount}, negative=${refitted.negativeCount}, " +
                        "centroids=${centroids != null}, active=${refitted.isActive}",
                )
            }
        }
    }

    /**
     * Rebuilds the training set from corrected events.
     *
     * Only events embedded by the current encoder are included; a previous encoder's vectors
     * describe a different space and would poison the fit.
     */
    private suspend fun replaySamples(): List<TrainingSample> =
        dao.correctedEvents(modelVersion = StaticEmbeddingAnalyzer.modelVersion())
            .mapNotNull { event ->
                val features = PersonalizedAttentionModel.features(
                    embedding = EmbeddingCodec.decode(event.embeddingQ8),
                    hourOfDay = event.contextHour,
                    senderImportance = event.senderImportanceAtDecision,
                    senderOpenRate = event.senderOpenRateAtDecision,
                    focusModeEnabled = event.focusModeAtDecision,
                    baseScore = event.baseScoreAtDecision,
                ) ?: return@mapNotNull null
                TrainingSample(features, event.action == UserAction.IMPORTANT.name)
            }

    private fun PersonalizedModelEntity.toState(): PersonalizedModelState? {
        if (version != PersonalizedAttentionModel.MODEL_VERSION) return null
        val decodedWeights = ModelWeightsCodec.decode(weights) ?: return null
        return PersonalizedModelState(
            weights = decodedWeights,
            bias = bias,
            positiveCount = positiveCount,
            negativeCount = negativeCount,
            evaluationCount = evaluationCount,
            personalCorrectCount = personalCorrectCount,
            baselineCorrectCount = baselineCorrectCount,
            importantEvaluationCount = importantEvaluationCount,
            importantCorrectCount = importantCorrectCount,
            notImportantEvaluationCount = notImportantEvaluationCount,
            falseImportantCount = falseImportantCount,
        )
    }

    private fun PersonalizedModelState.toEntity(
        updatedAt: Long,
        centroids: Centroids? = null,
    ): PersonalizedModelEntity =
        PersonalizedModelEntity(
            weights = ModelWeightsCodec.encode(weights),
            bias = bias,
            positiveCount = positiveCount,
            negativeCount = negativeCount,
            updatedAt = updatedAt,
            version = PersonalizedAttentionModel.MODEL_VERSION,
            evaluationCount = evaluationCount,
            personalCorrectCount = personalCorrectCount,
            baselineCorrectCount = baselineCorrectCount,
            importantEvaluationCount = importantEvaluationCount,
            importantCorrectCount = importantCorrectCount,
            notImportantEvaluationCount = notImportantEvaluationCount,
            falseImportantCount = falseImportantCount,
            importantCentroid = centroids?.let { ModelWeightsCodec.encodeVector(it.important) },
            notImportantCentroid = centroids?.let {
                ModelWeightsCodec.encodeVector(it.notImportant)
            },
        )

    private fun isSafetyProtected(
        decision: AttentionDecision,
        signal: NotificationSignal,
    ): Boolean = AttentionPolicy.isSafetyProtected(
        category = decision.category,
        semanticUrgency = decision.semanticUrgency,
        categoryHint = signal.categoryHint,
    )

    private fun pilotPeriodComplete(settings: AppSettings): Boolean =
        settings.pilotStartedAt > 0L &&
            System.currentTimeMillis() - settings.pilotStartedAt >=
            TimeConstants.PILOT_DURATION_MILLIS

    private fun String.jsonEscape(): String = buildString(length + 8) {
        this@jsonEscape.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
    }

    private companion object {
        const val HIGH_PRIORITY_THRESHOLD = 0.68f

        /**
         * Below this the set is too small for a refit to beat a single step, and the cost of
         * loading it is pure overhead.
         */
        const val MIN_REFIT_SAMPLES = 4
        const val LATENCY_WINDOW_MILLIS = 7L * TimeConstants.DAY_MILLIS

        /** Sender memory outlives events so learned importance survives a short retention. */
        const val MEMORY_RETENTION_MILLIS = 180L * TimeConstants.DAY_MILLIS
        const val MAX_TRAINING_ROWS = 5_000
        const val MAX_MEMORY_ROWS = 2_000

        val testScenarios = listOf(
            TestScenario(
                name = "Security",
                packageName = "com.example.identity",
                title = "New login detected",
                text = "Verification code 482911. If this wasn't you, secure your account now.",
                categoryHint = "status",
            ),
            TestScenario(
                name = "Urgent work",
                packageName = "com.slack",
                title = "Production incident",
                text = "Production server is down. Action required immediately.",
                categoryHint = "msg",
                isConversation = true,
            ),
            TestScenario(
                name = "Conversation",
                packageName = "com.whatsapp",
                title = "Sam",
                text = "Are we still meeting for lunch at 1?",
                categoryHint = "msg",
                isConversation = true,
            ),
            TestScenario(
                name = "Delivery",
                packageName = "com.example.courier",
                title = "Package update",
                text = "Your delivery is arriving this afternoon.",
                categoryHint = "status",
            ),
            TestScenario(
                name = "Promotion",
                packageName = "com.example.shop",
                title = "Weekend sale",
                text = "Save up to 40 percent with this limited offer.",
                categoryHint = "promo",
            ),
        )
    }
}

private data class PersonalizationOutcome(
    val decision: AttentionDecision,
    val probabilityImportant: Float?,
    val applied: Boolean,
)

private data class TestScenario(
    val name: String,
    val packageName: String,
    val title: String,
    val text: String,
    val categoryHint: String,
    val isConversation: Boolean = false,
)

data class AttentionTestResult(
    val name: String,
    val category: NotificationCategory,
    val basePriority: AttentionPriority,
    val finalPriority: AttentionPriority,
    val baseScore: Float,
    val personalProbability: Float?,
    val personalModelApplied: Boolean,
    val safetyProtected: Boolean,
    val durationMillis: Long,
)

enum class UserAction {
    OPENED,
    DISMISSED,
    IMPORTANT,
    NOT_IMPORTANT,
}

/** A plain-language snapshot of everything the app is storing locally. */
data class StorageSummary(
    val notificationCount: Int = 0,
    val senderCount: Int = 0,
    val trainingExampleCount: Int = 0,
    /** Rows that hold actual notification text; zero unless the user opted in. */
    val storedContentCount: Int = 0,
    val oldestEventAt: Long? = null,
    val databaseBytes: Long = 0,
)
