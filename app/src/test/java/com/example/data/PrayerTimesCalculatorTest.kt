package com.analoganchor.app.data

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PrayerTimesCalculatorTest {

    @Test
    fun testMeccaCalculations() {
        // Mecca coordinates: Latitude 21.4225, Longitude 39.8262, UTC+3
        val calculator = PrayerTimesCalculator(21.4225, 39.8262, 3.0)
        
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val testDate = sdf.parse("2026-05-29") ?: Date()
        
        val times = calculator.calculateTimes(testDate)
        
        assertNotNull(times)
        
        val fHour = times.fajr.get(Calendar.HOUR_OF_DAY)
        val sHour = times.sunrise.get(Calendar.HOUR_OF_DAY)
        val dHour = times.dhuhr.get(Calendar.HOUR_OF_DAY)
        val aHour = times.asr.get(Calendar.HOUR_OF_DAY)
        val mHour = times.maghrib.get(Calendar.HOUR_OF_DAY)
        val iHour = times.isha.get(Calendar.HOUR_OF_DAY)
        
        // Assert reasonable, mathematically correct boundaries for Mecca in late May
        // Fajr is in early morning (~04:10 AM)
        assertEquals(4, fHour)
        // Sunrise is around ~05:35 AM
        assertEquals(5, sHour)
        // Dhuhr is always around solar noon (~12:20 PM)
        assertEquals(12, dHour)
        // Asr is mid-afternoon (~03:30 - 03:45 PM)
        assertEquals(15, aHour)
        // Maghrib is sunset (~06:50 PM)
        assertEquals(18, mHour)
        // Isha is late evening (~08:15 - 08:30 PM)
        assertEquals(20, iHour)
    }

    @Test
    fun testBusaiteenCalculations() {
        // Busaiteen (Bahrain) coordinates: Latitude 26.2697, Longitude 50.6094, UTC+3
        val calculator = PrayerTimesCalculator(26.2697, 50.6094, 3.0)
        
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val testDate = sdf.parse("2026-05-29") ?: Date()
        
        val times = calculator.calculateTimes(testDate)
        
        assertNotNull(times)
        
        val fHour = times.fajr.get(Calendar.HOUR_OF_DAY)
        val sHour = times.sunrise.get(Calendar.HOUR_OF_DAY)
        val dHour = times.dhuhr.get(Calendar.HOUR_OF_DAY)
        
        // Assert Busaiteen astronomical timings:
        // Fajr: ~03:15 AM
        assertEquals(3, fHour)
        // Sunrise: ~04:45 AM
        assertEquals(4, sHour)
        // Dhuhr: ~11:45 AM
        assertEquals(11, dHour)
    }

    @Test
    fun testLondonCalculations() {
        // London coordinates: Latitude 51.5074, Longitude -0.1278, UTC+1 (BST)
        val calculator = PrayerTimesCalculator(51.5074, -0.1278, 1.0)
        
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val testDate = sdf.parse("2026-05-29") ?: Date()
        
        val times = calculator.calculateTimes(testDate)
        
        assertNotNull(times)
        
        val dHour = times.dhuhr.get(Calendar.HOUR_OF_DAY)
        val mHour = times.maghrib.get(Calendar.HOUR_OF_DAY)
        
        // London Dhuhr (solar noon normalized with BST): ~12:58 (12 PM hour part)
        assertEquals(12, dHour)
        // London sunset/Maghrib in late May: ~21:00 (9 PM)
        assertEquals(21, mHour)
    }
}
