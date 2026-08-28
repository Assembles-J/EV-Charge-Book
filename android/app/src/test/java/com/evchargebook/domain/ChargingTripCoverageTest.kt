package com.evchargebook.domain

import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.entity.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargingTripCoverageTest {
    @Test
    fun `calculates coverage from completed trips fully inside charging interval`() {
        val records = listOf(
            charge(1, 1_000, 10_000.0),
            charge(2, 10_000, 10_100.0)
        )
        val trips = listOf(
            trip(1, 2_000, 4_000, 40_000.0),
            trip(2, 5_000, 8_000, 50_000.0)
        )

        val summary = ChargingTripCoverage.summarize(records, trips)

        assertEquals(1, summary.intervals.size)
        assertEquals(100.0, summary.odometerDistanceKm, 0.0001)
        assertEquals(90.0, summary.completedTripDistanceKm, 0.0001)
        assertEquals(0.9, summary.coverageRatio!!, 0.0001)
        assertEquals(2, summary.intervals.single().completedTripCount)
    }

    @Test
    fun `does not assign trip that crosses charging boundary`() {
        val records = listOf(
            charge(1, 1_000, 10_000.0),
            charge(2, 10_000, 10_100.0)
        )
        val trips = listOf(
            trip(1, 500, 2_000, 30_000.0),
            trip(2, 9_000, 11_000, 30_000.0)
        )

        val summary = ChargingTripCoverage.summarize(records, trips)

        assertEquals(0, summary.intervals.size)
        assertNull(summary.coverageRatio)
    }

    @Test
    fun `ignores interrupted trip`() {
        val records = listOf(
            charge(1, 1_000, 10_000.0),
            charge(2, 10_000, 10_100.0)
        )
        val trip = TripSessionEntity(
            id = 1,
            vehicleId = 1,
            startedAtEpochMillis = 2_000,
            endedAtEpochMillis = 4_000,
            distanceMeters = 80_000.0,
            elapsedSeconds = 2,
            status = TripStatus.INTERRUPTED
        )

        val summary = ChargingTripCoverage.summarize(records, listOf(trip))

        assertEquals(0, summary.intervals.size)
    }

    @Test
    fun `clearly empty completed trip does not inflate coverage count`() {
        val records = listOf(
            charge(1, 1_000, 10_000.0),
            charge(2, 10_000, 10_100.0)
        )
        val trips = listOf(
            trip(1, 2_000, 4_000, 40_000.0),
            trip(2, 5_000, 8_000, 0.0)
        )

        val summary = ChargingTripCoverage.summarize(records, trips)

        assertEquals(1, summary.intervals.single().completedTripCount)
        assertEquals(40.0, summary.completedTripDistanceKm, 0.0001)
    }

    private fun charge(id: Long, time: Long, odometer: Double) = ChargingRecordEntity(
        id = id,
        vehicleId = 1,
        chargeTimeEpochMillis = time,
        energyKwh = 20.0,
        cost = 10.0,
        startSoc = 20,
        endSoc = 80,
        odometerKm = odometer
    )

    private fun trip(id: Long, start: Long, end: Long, distanceMeters: Double) = TripSessionEntity(
        id = id,
        vehicleId = 1,
        startedAtEpochMillis = start,
        endedAtEpochMillis = end,
        distanceMeters = distanceMeters,
        elapsedSeconds = ((end - start) / 1_000).coerceAtLeast(1),
        status = TripStatus.COMPLETED
    )
}
