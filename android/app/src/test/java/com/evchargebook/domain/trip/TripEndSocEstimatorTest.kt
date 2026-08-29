package com.evchargebook.domain.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripEndSocEstimatorTest {
    @Test
    fun estimatesEndSocFromDefaultConsumption() {
        val result = TripEndSocEstimator.estimate(
            startSoc = 80,
            distanceMeters = 50_000.0,
            batteryCapacityKwh = 67.7
        )

        assertEquals(69, result)
    }

    @Test
    fun usesProvidedTrustedAverageWhenAvailable() {
        val result = TripEndSocEstimator.estimate(
            startSoc = 80,
            distanceMeters = 50_000.0,
            batteryCapacityKwh = 67.7,
            averageConsumptionKwhPer100Km = 10.0
        )

        assertEquals(73, result)
    }

    @Test
    fun averagesNewestPlausibleHistoryAndIgnoresOutliers() {
        val average = TripEndSocEstimator.historicalAverageConsumptionKwhPer100Km(
            listOf(16.0, null, 80.0, 14.0, 18.0, 13.0, 17.0, 12.0)
        )

        assertEquals(15.6, average!!, 0.0001)
    }

    @Test
    fun returnsNullWhenHistoricalConsumptionHasNoPlausibleSamples() {
        assertNull(
            TripEndSocEstimator.historicalAverageConsumptionKwhPer100Km(
                listOf(null, Double.NaN, 0.0, 45.0)
            )
        )
    }

    @Test
    fun keepsStartSocWhenDistanceOrCapacityCannotEstimateDrop() {
        assertEquals(
            62,
            TripEndSocEstimator.estimate(
                startSoc = 62,
                distanceMeters = 0.0,
                batteryCapacityKwh = 67.7
            )
        )
        assertEquals(
            62,
            TripEndSocEstimator.estimate(
                startSoc = 62,
                distanceMeters = 25_000.0,
                batteryCapacityKwh = null
            )
        )
    }

    @Test
    fun requiresValidStartSoc() {
        assertNull(
            TripEndSocEstimator.estimate(
                startSoc = null,
                distanceMeters = 10_000.0,
                batteryCapacityKwh = 67.7
            )
        )
    }
}
