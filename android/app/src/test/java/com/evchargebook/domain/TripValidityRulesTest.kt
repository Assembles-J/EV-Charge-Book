package com.evchargebook.domain

import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.entity.TripStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class TripValidityRulesTest {
    @Test
    fun `normal completed trip is valid for analytics`() {
        val assessment = TripValidityRules.assess(trip(distanceMeters = 2_500.0, elapsedSeconds = 600))

        assertEquals(TripValidityStatus.VALID, assessment.status)
        assertTrue(assessment.eligibleForAnalytics)
    }

    @Test
    fun `zero movement completed trip is invalid and excluded from analytics`() {
        val assessment = TripValidityRules.assess(trip(distanceMeters = 0.0, elapsedSeconds = 180))

        assertEquals(TripValidityStatus.INVALID, assessment.status)
        assertTrue(TripValidityReason.NO_MEANINGFUL_MOVEMENT in assessment.reasons)
        assertFalse(assessment.eligibleForAnalytics)
    }

    @Test
    fun `zero duration completed trip is invalid`() {
        val assessment = TripValidityRules.assess(trip(distanceMeters = 300.0, elapsedSeconds = 0))

        assertEquals(TripValidityStatus.INVALID, assessment.status)
        assertTrue(TripValidityReason.INVALID_DURATION in assessment.reasons)
    }

    @Test
    fun `completed trip without enough track points is invalid when detail evidence is available`() {
        val assessment = TripValidityRules.assess(
            trip = trip(distanceMeters = 200.0, elapsedSeconds = 120),
            acceptedPointCount = 1
        )

        assertEquals(TripValidityStatus.INVALID, assessment.status)
        assertTrue(TripValidityReason.INSUFFICIENT_TRACK_POINTS in assessment.reasons)
    }

    @Test
    fun `very short trip is review only and stays in analytics until user decides`() {
        val assessment = TripValidityRules.assess(trip(distanceMeters = 60.0, elapsedSeconds = 70))

        assertEquals(TripValidityStatus.REVIEW, assessment.status)
        assertTrue(assessment.eligibleForAnalytics)
    }

    @Test
    fun `short distance with meaningful duration remains valid`() {
        val assessment = TripValidityRules.assess(trip(distanceMeters = 60.0, elapsedSeconds = 180))

        assertEquals(TripValidityStatus.VALID, assessment.status)
        assertTrue(assessment.eligibleForAnalytics)
    }

    @Test
    fun `interrupted trip is incomplete rather than automatically invalid`() {
        val assessment = TripValidityRules.assess(
            trip(distanceMeters = 0.0, elapsedSeconds = 30, status = TripStatus.INTERRUPTED, endedAt = null)
        )

        assertEquals(TripValidityStatus.INCOMPLETE, assessment.status)
        assertFalse(assessment.eligibleForAnalytics)
    }

    private fun trip(
        distanceMeters: Double,
        elapsedSeconds: Long,
        status: String = TripStatus.COMPLETED,
        endedAt: Long? = 20_000L
    ) = TripSessionEntity(
        id = 1,
        vehicleId = 1,
        startedAtEpochMillis = 10_000L,
        endedAtEpochMillis = endedAt,
        distanceMeters = distanceMeters,
        elapsedSeconds = elapsedSeconds,
        status = status
    )
}
