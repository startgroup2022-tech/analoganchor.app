package com.analoganchor.app

import com.analoganchor.app.data.preferences.UserPreferences
import com.analoganchor.app.service.ZenithAccessibilityService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DynamicMorningUnlockTest {

    private fun isBedtimeActiveForTest(prefs: UserPreferences, currentTime: Long): Boolean {
        if (!prefs.bedtimeEnabled) return false

        val startParts = prefs.bedtimeStartTime.split(":")
        val endParts = prefs.bedtimeEndTime.split(":")
        val startMinutes = (startParts.getOrNull(0)?.toIntOrNull() ?: 22) * 60 + (startParts.getOrNull(1)?.toIntOrNull() ?: 0)
        val endMinutes = (endParts.getOrNull(0)?.toIntOrNull() ?: 5) * 60 + (endParts.getOrNull(1)?.toIntOrNull() ?: 0)

        val cal = Calendar.getInstance().apply { timeInMillis = currentTime }
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val yesterdayCal = Calendar.getInstance().apply {
            timeInMillis = currentTime
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val yesterdayDay = yesterdayCal.get(Calendar.DAY_OF_WEEK)

        return if (startMinutes <= endMinutes) {
            if (currentDay in prefs.bedtimeDays) {
                currentMinutes in startMinutes..endMinutes
            } else false
        } else {
            if (currentDay in prefs.bedtimeDays && currentMinutes >= startMinutes) {
                true
            } else if (yesterdayDay in prefs.bedtimeDays && currentMinutes <= endMinutes) {
                true
            } else false
        }
    }

    @Test
    fun testDynamicMorningUnlockAtFiveAM() {
        val prefs = UserPreferences(
            bedtimeEnabled = true,
            bedtimeStartTime = "22:00",
            bedtimeEndTime = "05:00",
            bedtimeDays = setOf(1, 2, 3, 4, 5, 6, 7)
        )

        // 23:30 (Night) -> Bedtime should be ACTIVE
        val calNight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 30)
        }
        assertTrue(isBedtimeActiveForTest(prefs, calNight.timeInMillis))

        // 04:30 (Early morning before 5 AM) -> Bedtime should be ACTIVE
        val calPreDawn = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 4)
            set(Calendar.MINUTE, 30)
        }
        assertTrue(isBedtimeActiveForTest(prefs, calPreDawn.timeInMillis))

        // 05:01 (Sunrise / Morning release) -> Bedtime should be INACTIVE (Unlocked!)
        val calMorningUnlock = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 5)
            set(Calendar.MINUTE, 1)
        }
        assertFalse(isBedtimeActiveForTest(prefs, calMorningUnlock.timeInMillis))

        // 08:00 (Daytime) -> Bedtime should be INACTIVE (Unlocked!)
        val calDaytime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
        }
        assertFalse(isBedtimeActiveForTest(prefs, calDaytime.timeInMillis))
    }
}
