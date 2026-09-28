package com.analoganchor.app.util

import java.util.Locale
import java.util.regex.Pattern

data class ParsedLocation(
    val lat: Double,
    val lng: Double,
    val title: String,
    val address: String
)

object LocationCodeParser {
    // Default Bahrain reference coordinate for recovering short Plus Codes (e.g. 6H7J+2XR)
    const val BAHRAIN_DEFAULT_LAT = 26.2285
    const val BAHRAIN_DEFAULT_LNG = 50.5860

    private val PLUS_CODE_PATTERN = Pattern.compile(
        "(?i)\\b([23456789CFGHJMPQRVWX]{4,8}\\+[23456789CFGHJMPQRVWX]{2,4})\\b"
    )

    private val COORD_PATTERN = Pattern.compile(
        "^\\s*(-?\\d{1,2}(?:\\.\\d+)?)[,\\s]+(-?\\d{1,3}(?:\\.\\d+)?)\\s*$"
    )

    fun parse(
        query: String,
        referenceLat: Double = BAHRAIN_DEFAULT_LAT,
        referenceLng: Double = BAHRAIN_DEFAULT_LNG
    ): ParsedLocation? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null

        // 1. Direct Lat/Lng numerical coordinate match
        val coordMatcher = COORD_PATTERN.matcher(trimmed)
        if (coordMatcher.matches()) {
            try {
                val lat = coordMatcher.group(1)?.toDoubleOrNull() ?: return null
                val lng = coordMatcher.group(2)?.toDoubleOrNull() ?: return null
                if (lat in -90.0..90.0 && lng in -180.0..180.0) {
                    return ParsedLocation(
                        lat = lat,
                        lng = lng,
                        title = String.format(Locale.US, "GPS: %.5f, %.5f", lat, lng),
                        address = trimmed
                    )
                }
            } catch (_: Exception) {}
        }

        // 2. Google Plus Code match (e.g. '6H7J+2XR Manama, Bahrain' or '6H7J+2XR')
        val plusMatcher = PLUS_CODE_PATTERN.matcher(trimmed)
        if (plusMatcher.find()) {
            val code = plusMatcher.group(1)?.uppercase(Locale.US) ?: return null
            try {
                val olc = OpenLocationCode(code)
                val fullOlc = if (olc.isShort) {
                    val refLat = if (referenceLat != 0.0 && referenceLat != -999.0) referenceLat else BAHRAIN_DEFAULT_LAT
                    val refLng = if (referenceLng != 0.0 && referenceLng != -999.0) referenceLng else BAHRAIN_DEFAULT_LNG
                    olc.recover(refLat, refLng)
                } else {
                    olc
                }
                val area = fullOlc.decode()
                return ParsedLocation(
                    lat = area.centerLatitude,
                    lng = area.centerLongitude,
                    title = "Google Plus Code: $code",
                    address = trimmed
                )
            } catch (_: Exception) {}
        }

        return null
    }
}
