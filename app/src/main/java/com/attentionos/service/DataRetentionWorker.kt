package com.attentionos.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.attentionos.AttentionApplication
import kotlinx.coroutines.flow.first

class DataRetentionWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as AttentionApplication).container
        val settings = container.settingsRepository.settings.first()
        container.attentionRepository.prune(settings.retentionDays)
        return Result.success()
    }
}

