package com.analoganchor.app.data

import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlin.math.*

class PrayerTimesCalculator(
    private val latitude: Double,
    private val longitude: Double,
    private val timezoneOffset: Double
) {

    data class PrayerTimes(
        val fajr: Calendar,
        val sunrise: Calendar,
        val dhuhr: Calendar,
        val asr: Calendar,
        val maghrib: Calendar,
        val isha: Calendar
    )

    private fun julianDate(year: Int, month: Int, day: Int): Double {
        var Y = year
        var M = month
        if (M <= 2) {
            Y -= 1
            M += 12
        }
        val A = Y / 100
        val B = A / 4
        val C = 2 - A + B
        val E = (365.25 * (Y + 4716)).toInt()
        val F = (30.6001 * (M + 1)).toInt()
        return C + day + E + F - 1524.5
    }

    fun calculateTimes(date: Date): PrayerTimes {
        val cal = Calendar.getInstance().apply { time = date }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)

        val JD = julianDate(year, month, day)
        val d = JD - 2451545.0

        // Keplerian Elements of the Sun
        val g = 357.529 + 0.98560028 * d
        val q = 280.459 + 0.98564736 * d
        val L = q + 1.915 * sin(Math.toRadians(g)) + 0.02 * sin(Math.toRadians(2.0 * g))
        val e = 23.439 - 0.00000036 * d

        val dec = Math.toDegrees(asin(sin(Math.toRadians(e)) * sin(Math.toRadians(L))))

        var RA = Math.toDegrees(atan2(cos(Math.toRadians(e)) * sin(Math.toRadians(L)), cos(Math.toRadians(L))))
        RA = (RA + 360.0) % 360.0

        // Adjust RA quadrant
        val L_quad = floor(L / 90.0) * 90.0
        val RA_quad = floor(RA / 90.0) * 90.0
        val RA_adj = RA + (L_quad - RA_quad)

        // Equation of time (in hours)
        val eqt = ((q + 360.0) % 360.0 - RA_adj) / 15.0

        // Midday / Dhuhr
        val midday = 12.0 - (longitude / 15.0) - eqt + timezoneOffset

        // Helper to compute hour angle
        fun hourAngle(altitude: Double): Double {
            val num = sin(Math.toRadians(altitude)) - sin(Math.toRadians(latitude)) * sin(Math.toRadians(dec))
            val den = cos(Math.toRadians(latitude)) * cos(Math.toRadians(dec))
            val cosH = num / den
            if (cosH > 1.0 || cosH < -1.0) return Double.NaN
            return Math.toDegrees(acos(cosH)) / 15.0
        }

        // Sunrise & Sunset (Maghrib) altitude is -0.833 degrees
        val hSunrise = hourAngle(-0.833)
        val sunriseHour = if (hSunrise.isNaN()) midday - 6.0 else midday - hSunrise
        val maghribHour = if (hSunrise.isNaN()) midday + 6.0 else midday + hSunrise

        // Fajr altitude is -18.0 degrees
        val hFajr = hourAngle(-18.0)
        val fajrHour = if (hFajr.isNaN()) midday - 7.2 else midday - hFajr

        // Isha altitude is -17.0 degrees
        val hIsha = hourAngle(-17.0)
        val ishaHour = if (hIsha.isNaN()) midday + 7.2 else midday + hIsha

        // Asr calculation (Shafi/Maliki/Hanbali: shadow length = object + midday shadow)
        val altAsr = Math.toDegrees(atan(1.0 / (1.0 + tan(Math.toRadians(abs(latitude - dec))))))
        val hAsr = hourAngle(altAsr)
        val asrHour = if (hAsr.isNaN()) midday + 3.0 else midday + hAsr

        fun convertToCalendar(hourVal: Double): Calendar {
            var finalHour = hourVal
            // Normalize
            while (finalHour < 0.0) finalHour += 24.0
            while (finalHour >= 24.0) finalHour -= 24.0

            val hour = finalHour.toInt()
            val minuteVal = (finalHour - hour) * 60.0
            val minute = minuteVal.toInt()
            val second = ((minuteVal - minute) * 60.0).toInt()

            return (cal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, second)
                set(Calendar.MILLISECOND, 0)
            }
        }

        return PrayerTimes(
            fajr = convertToCalendar(fajrHour),
            sunrise = convertToCalendar(sunriseHour),
            dhuhr = convertToCalendar(midday),
            asr = convertToCalendar(asrHour),
            maghrib = convertToCalendar(maghribHour),
            isha = convertToCalendar(ishaHour)
        )
    }
}
