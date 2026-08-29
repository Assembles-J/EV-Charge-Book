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
