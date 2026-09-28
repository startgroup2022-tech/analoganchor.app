package com.analoganchor.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.analoganchor.app.engine.DailyRuleEngineRunner
import com.analoganchor.app.data.repository.ShieldRepository
import com.analoganchor.app.data.preferences.UserPreferencesRepository
import com.analoganchor.app.worker.FajrDailyRescheduleWorker
import com.mapbox.common.MapboxOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class AnalogAnchorApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    val shieldRepository: ShieldRepository by lazy { ShieldRepository() }
    val preferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(this) }

    override fun onCreate() {
        super.onCreate()
        try {
            if (BuildConfig.MAPBOX_ACCESS_TOKEN.isNotEmpty()) {
                MapboxOptions.accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN
            }
        } catch (e: Throwable) {
            android.util.Log.e("AnalogAnchorApp", "Failed to init Mapbox token: ${e.message}")
        }
        applicationScope.launch {
            DailyRuleEngineRunner.runIfDue(applicationContext)
            setupDailySelfHealingWorker()
        }
    }

    private fun setupDailySelfHealingWorker() {
        try {
            val rescheduleRequest = PeriodicWorkRequestBuilder<FajrDailyRescheduleWorker>(
                12, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                FajrDailyRescheduleWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                rescheduleRequest
            )
        } catch (e: Exception) {
            android.util.Log.e("AnalogAnchorApp", "Failed to enqueue FajrDailyRescheduleWorker: ${e.message}")
        }
    }
}

