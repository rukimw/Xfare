package com.lankafare.app

import kotlin.math.abs

data class FareReading(
    val totalFareLkr: Double,
    val totalDistanceKm: Double
)

object FareParser {

    // Matches Rs. 1,450 | Rs 850.50 | LKR 1,200 | LKR950
    private val FARE_REGEX = Regex(
        pattern = "(?:Rs\\.?|LKR)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
        option = RegexOption.IGNORE_CASE
    )

    // Prevents matching per-km rate labels like "Rs. 115 / km"
    private val PER_KM_SUFFIX_REGEX = Regex(
        pattern = "^\\s*(?:/|per)\\s*km\\b",
        option = RegexOption.IGNORE_CASE
    )

    // Matches 2.3 km | 10 km | 8.4km
    private val DISTANCE_REGEX = Regex(
        pattern = "(\\d+(?:\\.\\d+)?)\\s*km\\b",
        option = RegexOption.IGNORE_CASE
    )

    private val TOTAL_KEYWORD_REGEX = Regex(
        pattern = "\\b(?:total|trip\\s*distance)\\b",
        option = RegexOption.IGNORE_CASE
    )

    private const val MIN_VALID_FARE = 50.0
    private const val MAX_VALID_FARE = 500_000.0
    private const val MIN_VALID_KM = 0.1
    const val MAX_VALID_KM = 500.0

    fun parseScreenText(lines: List<String>): FareReading? {
        if (lines.isEmpty()) return null

        val fares = ArrayList<Double>(4)
        val distances = ArrayList<Double>(4)
        var explicitTotalKm: Double? = null

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            for (match in FARE_REGEX.findAll(line)) {
                val trailing = line.substring(match.range.last + 1)
                if (PER_KM_SUFFIX_REGEX.containsMatchIn(trailing)) {
                    continue
                }
                val numeric = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
                if (numeric in MIN_VALID_FARE..MAX_VALID_FARE) {
                    fares.add(numeric)
                }
            }

            val hasTotalHint = TOTAL_KEYWORD_REGEX.containsMatchIn(line)
            for (match in DISTANCE_REGEX.findAll(line)) {
                val km = match.groupValues[1].toDoubleOrNull() ?: continue
                if (km in MIN_VALID_KM..MAX_VALID_KM) {
                    distances.add(km)
                    if (hasTotalHint) {
                        explicitTotalKm = km
                    }
                }
            }
        }

        val primaryFare = fares.maxOrNull() ?: return null
        val totalDistance = resolveTotalDistance(distances, explicitTotalKm) ?: return null
        if (totalDistance <= 0.0) return null

        return FareReading(
            totalFareLkr = primaryFare,
            totalDistanceKm = totalDistance
        )
    }

    private fun resolveTotalDistance(
        distances: List<Double>,
        explicitTotalKm: Double?
    ): Double? {
        if (explicitTotalKm != null) return explicitTotalKm
        return when (distances.size) {
            0 -> null
            1 -> distances[0]
            2 -> distances[0] + distances[1]
            else -> {
                val maxVal = distances.maxOrNull() ?: return null
                // If screen already shows pickup + trip + total, prefer total instead of double counting
                for (i in distances.indices) {
                    for (j in i + 1 until distances.size) {
                        if (abs((distances[i] + distances[j]) - maxVal) <= 0.15) {
                            return maxVal
                        }
                    }
                }
                distances[0] + distances[1]
            }
        }
    }
}
