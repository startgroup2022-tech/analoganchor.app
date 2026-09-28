package com.analoganchor.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import com.analoganchor.app.data.local.entity.ShieldEntity
import com.analoganchor.app.data.local.entity.ScheduleEntity
import com.analoganchor.app.data.local.entity.AppUsageEntity
import java.util.concurrent.ConcurrentHashMap

class ShieldRepository {
    val isShieldsLoaded: Flow<Boolean> = flowOf(true)
    val allShields: Flow<List<ShieldEntity>> = flowOf(emptyList())

    suspend fun updateShield(shield: ShieldEntity) {}
    fun getAllUsage(): Flow<List<AppUsageEntity>> = flowOf(emptyList())

    val allSchedules: Flow<List<ScheduleEntity>> = flowOf(emptyList())

    suspend fun getScheduleById(id: Long): ScheduleEntity? {
        return null
    }

    suspend fun updateSchedule(schedule: ScheduleEntity) {}

    suspend fun getShieldByPackageName(packageName: String): ShieldEntity? {
        return null
    }

    val allowedApps = ConcurrentHashMap<String, Long>()
    val mindfulGatewayStates = ConcurrentHashMap<String, ShieldEntity>()
    val waterPauseCompletedSessions = ConcurrentHashMap.newKeySet<String>()
    val postWaterPauseGraceUntil = ConcurrentHashMap<String, Long>()

    fun isInPostWaterPauseGrace(packageName: String): Boolean {
        val until = postWaterPauseGraceUntil[packageName] ?: return false
        if (System.currentTimeMillis() >= until) {
            postWaterPauseGraceUntil.remove(packageName)
            return false
        }
        return true
    }

    fun beginPostWaterPauseGrace(packageName: String, durationMs: Long = 25_000L) {
        postWaterPauseGraceUntil[packageName] = System.currentTimeMillis() + durationMs
    }

    fun clearPostWaterPauseGrace(packageName: String) {
        postWaterPauseGraceUntil.remove(packageName)
    }
}
