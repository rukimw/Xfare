package com.lankafare.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FareParserTest {

    @Test
    fun parsesPickMePickupAndTripDistance() {
        val reading = FareParser.parseScreenText(
            listOf("New Ride Request", "2.1 km", "8.4 km", "Rs. 1,450", "Accept")
        )
        assertNotNull(reading)
        assertEquals(1450.0, reading!!.totalFareLkr, 0.01)
        assertEquals(10.5, reading.totalDistanceKm, 0.01)
    }

    @Test
    fun parsesUberLkrFormatAndIgnoresPerKmRate() {
        val reading = FareParser.parseScreenText(
            listOf("UberX", "LKR 980.50", "Rs. 110 / km", "Total 7.5 km")
        )
        assertNotNull(reading)
        assertEquals(980.50, reading!!.totalFareLkr, 0.01)
        assertEquals(7.5, reading.totalDistanceKm, 0.01)
    }

    @Test
    fun returnsNullWhenMissingFareOrDistance() {
        assertNull(FareParser.parseScreenText(listOf("Online", "Searching for trips")))
        assertNull(FareParser.parseScreenText(listOf("Rs. 500")))
    }
}
