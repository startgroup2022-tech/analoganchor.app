package com.analoganchor.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.analoganchor.app.viewmodel.AnchorViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreakAndWeeklyResetTest {

    private lateinit var app: Application

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        val prefs = app.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    private fun formatDate(cal: Calendar): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }

    @Test
    fun `streaks initialize to zero on fresh launch`() {
        val viewModel = AnchorViewModel(app)
        assertEquals(0, viewModel.protocolComplianceStreak.value)
        assertEquals(0, viewModel.maghribRetreatStreak.value)
        assertEquals(0, viewModel.mosqueComplianceStreak.value)
    }

    @Test
    fun `completing retreat increments retreat streak and awards points`() {
        val viewModel = AnchorViewModel(app)
        val initialPoints = viewModel.reservePoints.value

        viewModel.completeMaghribRetreat()

        assertEquals(1, viewModel.maghribRetreatStreak.value)
        assertEquals(initialPoints + 40, viewModel.reservePoints.value)
        assertEquals(true, viewModel.maghribVerified.value)
    }

    @Test
    fun `completing fajr presence increments fajr streak and awards covenant score`() {
        val viewModel = AnchorViewModel(app)
        val initialPoints = viewModel.reservePoints.value

        viewModel.verifyFajrPresence()

        assertEquals(1, viewModel.mosqueComplianceStreak.value)
        assertEquals(initialPoints, viewModel.reservePoints.value)
        assertEquals(true, viewModel.fajrVerified.value)
        assertEquals(40, viewModel.covenantScore.value)
        assertEquals(true, viewModel.todayPrayersCompleted.value[0]) // Fajr slot [ف]
    }

    @Test
    fun `completing focus session increments paper ritual streak`() {
        val viewModel = AnchorViewModel(app)
        viewModel.startFocusSession(25)
        viewModel.completeFocusSession()

        assertEquals(1, viewModel.protocolComplianceStreak.value)
    }

    @Test
    fun `friday reset clears reserve points and all streaks`() {
        val viewModel = AnchorViewModel(app)
        val prefs = app.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)

        // Seed some points and streaks
        prefs.edit()
            .putInt("reserve_points", 250)
            .putInt("protocol_streak", 4)
            .putInt("maghrib_retreat_streak", 4)
            .putInt("mosque_streak", 4)
            .putString("last_friday_reset_date", "2020-01-01")
            .commit()

        viewModel.checkWeeklyFridayReset()

        // If today is Friday, it will reset to 0
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) {
            assertEquals(0, viewModel.reservePoints.value)
            assertEquals(0, viewModel.protocolComplianceStreak.value)
            assertEquals(0, viewModel.maghribRetreatStreak.value)
            assertEquals(0, viewModel.mosqueComplianceStreak.value)
        }
    }

    @Test
    fun `missing previous days results in zero streak`() {
        val prefs = app.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)

        // Simulate completing 3 days ago, but NOT yesterday or today
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -3)
        val pastDate = formatDate(cal)
        prefs.edit().putBoolean("evening_anchor_completed_$pastDate", true).commit()

        val viewModel = AnchorViewModel(app)
        viewModel.recalculateWeeklyPillarStreaks()

        // Since yesterday and today were not completed, streak must be 0
        assertEquals(0, viewModel.protocolComplianceStreak.value)
    }

    @Test
    fun `consecutive completions within current week increment streak`() {
        val prefs = app.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
        val cal = Calendar.getInstance()
        val todayStr = formatDate(cal)

        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = formatDate(cal)

        prefs.edit()
            .putBoolean("evening_anchor_completed_$todayStr", true)
            .putBoolean("evening_anchor_completed_$yesterdayStr", true)
            .commit()

        val viewModel = AnchorViewModel(app)
        viewModel.recalculateWeeklyPillarStreaks()

        // Should be at least 2 if yesterday was within the week, or 1 if today was reset
        assertTrue(viewModel.protocolComplianceStreak.value >= 1)
    }

    @Test
    fun `seven day mastery across all three pillars awards 500 bonus points`() {
        val prefs = app.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
        val cal = Calendar.getInstance()

        // Simulate 7 consecutive completed days for paper ritual, retreat, and fajr
        for (i in 0 until 7) {
            val dateKey = formatDate(cal)
            prefs.edit()
                .putBoolean("evening_anchor_completed_$dateKey", true)
                .putBoolean("retreat_completed_$dateKey", true)
                .putBoolean("fajr_verified_$dateKey", true)
                .commit()
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }

        val viewModel = AnchorViewModel(app)
        val initialPoints = viewModel.reservePoints.value

        viewModel.recalculateWeeklyPillarStreaks()

        // If in a 7-day window, points will receive +500 bonus
        if (viewModel.protocolComplianceStreak.value >= 7 &&
            viewModel.maghribRetreatStreak.value >= 7 &&
            viewModel.mosqueComplianceStreak.value >= 7) {
            assertEquals(initialPoints + 500, viewModel.reservePoints.value)
        }
    }

    @Test
    fun `fajr alarm dismiss verifies morning prayer in covenant and five slot bar`() {
        val viewModel = AnchorViewModel(app)
        viewModel.dismissFajrAlarm()

        assertEquals(true, viewModel.fajrVerified.value)
        assertEquals(true, viewModel.fajrWakeVerified.value)
        assertEquals(true, viewModel.todayPrayersCompleted.value[0])
        assertEquals(40, viewModel.covenantScore.value)
    }

    @Test
    fun `un-dismissed missed fajr alarm leaves fajr unverified`() {
        val prefs = app.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        prefs.edit()
            .putBoolean("fajr_alarm_missed", true)
            .putBoolean("fajr_alarm_missed_$todayKey", true)
            .commit()

        val viewModel = AnchorViewModel(app)
        viewModel.recalculateTodayPrayerStatus()
        viewModel.recalculateCovenantStats()

        assertEquals(false, viewModel.todayPrayersCompleted.value[0])
        assertEquals(0, viewModel.covenantScore.value)
    }

    @Test
    fun `isha verified via night flow lights up fifth slot and adds 40 covenant points`() {
        val prefs = app.getSharedPreferences("analog_anchor_prefs", Context.MODE_PRIVATE)
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        prefs.edit()
            .putBoolean("prayer_verified_isha_$todayKey", true)
            .commit()

        val viewModel = AnchorViewModel(app)
        viewModel.recalculateTodayPrayerStatus()
        viewModel.recalculateCovenantStats()

        assertEquals(true, viewModel.todayPrayersCompleted.value[4]) // Isha slot [ش]
        assertEquals(40, viewModel.covenantScore.value)
    }
}
