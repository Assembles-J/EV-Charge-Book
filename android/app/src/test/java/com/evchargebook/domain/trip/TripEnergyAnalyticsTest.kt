package com.evchargebook.domain.trip

import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.entity.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripEnergyAnalyticsTest {
    @Test
    fun `uses distance weighted average instead of averaging trip averages`() {
        val trips = listOf(
            completedTrip(id = 1, distanceMeters = 100_000.0, energyKwh = 10.0),
            completedTrip(id = 2, distanceMeters = 10_000.0, energyKwh = 10.0)
        )

        val summary = TripEnergyAnalytics.summarize(trips)

        assertEquals(2, summary.eligibleTripCount)
        assertEquals(110.0, summary.distanceKm, 0.001)
        assertEquals(20.0, summary.estimatedEnergyKwh, 0.001)
        assertEquals(18.1818, summary.weightedAverageKwhPer100Km!!, 0.0001)
    }

    @Test
    fun `excludes empty invalid and energy-incomplete trips and ignores active trips`() {
        val trips = listOf(
            completedTrip(id = 1, distanceMeters = 20_000.0, energyKwh = null),
            completedTrip(id = 2, distanceMeters = 0.0, energyKwh = 2.0),
            TripSessionEntity(
                id = 3,
                vehicleId = 1,
                startedAtEpochMillis = 3000,
                distanceMeters = 30_000.0,
                elapsedSeconds = 60,
                consumedEnergyKwh = 3.0,
                status = TripStatus.RECORDING
            )
        )

        val summary = TripEnergyAnalytics.summarize(trips)

        assertEquals(1, summary.completedTripCount)
        assertEquals(0, summary.eligibleTripCount)
        assertEquals(1, summary.excludedTripCount)
        assertEquals(0.0, summary.estimatedEnergyKwh, 0.001)
        assertNull(summary.weightedAverageKwhPer100Km)
    }

    @Test
    fun `filters month summary by trip end time`() {
        val trips = listOf(
            completedTrip(id = 1, endedAt = 1500, distanceMeters = 10_000.0, energyKwh = 2.0),
            completedTrip(id = 2, endedAt = 2500, distanceMeters = 20_000.0, energyKwh = 4.0)
        )

        val summary = TripEnergyAnalytics.summarize(
            trips = trips,
            startInclusiveEpochMillis = 2000,
            endExclusiveEpochMillis = 3000
        )

        assertEquals(1, summary.completedTripCount)
        assertEquals(1, summary.eligibleTripCount)
        assertEquals(20.0, summary.distanceKm, 0.001)
        assertEquals(4.0, summary.estimatedEnergyKwh, 0.001)
    }

    private fun completedTrip(
        id: Long,
        endedAt: Long = 2000,
        distanceMeters: Double,
        energyKwh: Double?
    ) = TripSessionEntity(
        id = id,
        vehicleId = 1,
        startedAtEpochMillis = 1000,
        endedAtEpochMillis = endedAt,
        distanceMeters = distanceMeters,
        elapsedSeconds = ((endedAt - 1000) / 1_000).coerceAtLeast(1),
        consumedEnergyKwh = energyKwh,
        status = TripStatus.COMPLETED
    )
}
