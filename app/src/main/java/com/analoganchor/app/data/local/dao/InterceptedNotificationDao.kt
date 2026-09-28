package com.analoganchor.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import com.analoganchor.app.data.local.entity.InterceptedNotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InterceptedNotificationDao {
    @Insert
    suspend fun insert(notification: InterceptedNotificationEntity)

    @Query("SELECT * FROM intercepted_notifications WHERE scheduleId = :scheduleId")
    fun getNotificationsForSchedule(scheduleId: Long): Flow<List<InterceptedNotificationEntity>>

    @Query("SELECT * FROM intercepted_notifications")
    suspend fun getAllNotifications(): List<InterceptedNotificationEntity>

    @Query("DELETE FROM intercepted_notifications WHERE scheduleId = :scheduleId")
    suspend fun deleteByScheduleId(scheduleId: Long)

    @Query("DELETE FROM intercepted_notifications")
    suspend fun deleteAll()
}
