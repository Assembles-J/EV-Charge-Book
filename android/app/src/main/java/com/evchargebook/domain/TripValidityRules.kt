package com.evchargebook.domain

import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.entity.TripStatus

enum class TripValidityStatus {
    VALID,
    REVIEW,
    INVALID,
    INCOMPLETE
}

enum class TripValidityReason {
    NOT_COMPLETED,
    MISSING_END_TIME,
    INVALID_DISTANCE,
    INVALID_DURATION,
    NO_MEANINGFUL_MOVEMENT,
    INSUFFICIENT_TRACK_POINTS,
    VERY_SHORT_TRIP
}

data class TripValidityAssessment(
    val status: TripValidityStatus,
    val reasons: Set<TripValidityReason> = emptySet()
) {
    val eligibleForAnalytics: Boolean
        get() = status == TripValidityStatus.VALID || status == TripValidityStatus.REVIEW
}

/**
 * Conservative Trip validity rules.
 *
 * Only structurally impossible/empty completed Trips are excluded from analytics automatically.
 * Very short Trips are review candidates rather than invalid, so legitimate parking-lot or
 * repositioning drives are never silently discarded.
 */
object TripValidityRules {
    const val MIN_MEANINGFUL_DISTANCE_METERS = 1.0
    const val REVIEW_DISTANCE_METERS = 100.0
    const val REVIEW_DURATION_SECONDS = 120L
    const val MIN_TRACK_POINTS = 2

    fun assess(
        trip: TripSessionEntity,
        acceptedPointCount: Int? = null
    ): TripValidityAssessment {
        if (trip.status != TripStatus.COMPLETED) {
            return TripValidityAssessment(
                status = TripValidityStatus.INCOMPLETE,
                reasons = setOf(TripValidityReason.NOT_COMPLETED)
            )
        }

        val invalidReasons = buildSet {
            if (trip.endedAtEpochMillis == null) add(TripValidityReason.MISSING_END_TIME)
            if (!trip.distanceMeters.isFinite() || trip.distanceMeters < 0.0) add(TripValidityReason.INVALID_DISTANCE)
            if (trip.elapsedSeconds <= 0L) add(TripValidityReason.INVALID_DURATION)
            if (
                trip.distanceMeters.isFinite() &&
                trip.distanceMeters >= 0.0 &&
                trip.distanceMeters < MIN_MEANINGFUL_DISTANCE_METERS
            ) {
                add(TripValidityReason.NO_MEANINGFUL_MOVEMENT)
            }
            if (acceptedPointCount != null && acceptedPointCount < MIN_TRACK_POINTS) {
                add(TripValidityReason.INSUFFICIENT_TRACK_POINTS)
            }
        }
        if (invalidReasons.isNotEmpty()) {
            return TripValidityAssessment(TripValidityStatus.INVALID, invalidReasons)
        }

        if (trip.distanceMeters < REVIEW_DISTANCE_METERS && trip.elapsedSeconds < REVIEW_DURATION_SECONDS) {
            return TripValidityAssessment(
                status = TripValidityStatus.REVIEW,
                reasons = setOf(TripValidityReason.VERY_SHORT_TRIP)
            )
        }

        return TripValidityAssessment(TripValidityStatus.VALID)
    }

    fun isEligibleForAnalytics(trip: TripSessionEntity): Boolean = assess(trip).eligibleForAnalytics
}
