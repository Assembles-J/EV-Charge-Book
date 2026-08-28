package com.evchargebook.domain

import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.data.entity.TripSessionEntity

data class ChargingTripCoverageInterval(
    val previousRecordId: Long,
    val currentRecordId: Long,
    val odometerDistanceKm: Double,
    val completedTripDistanceKm: Double,
    val completedTripCount: Int,
    val coverageRatio: Double,
    val distanceDifferenceKm: Double
)

data class ChargingTripCoverageSummary(
    val intervals: List<ChargingTripCoverageInterval>,
    val odometerDistanceKm: Double,
    val completedTripDistanceKm: Double,
    val coverageRatio: Double?
)

object ChargingTripCoverage {
    fun summarize(
        records: List<ChargingRecordEntity>,
        trips: List<TripSessionEntity>
    ): ChargingTripCoverageSummary {
        val orderedRecords = records
            .filter { it.odometerKm != null }
            .sortedBy { it.chargeTimeEpochMillis }
        val completedTrips = trips.filter(TripValidityRules::isEligibleForAnalytics)

        val intervals = orderedRecords.zipWithNext().mapNotNull { (previous, current) ->
            val previousOdometer = previous.odometerKm ?: return@mapNotNull null
            val currentOdometer = current.odometerKm ?: return@mapNotNull null
            val odometerDistance = currentOdometer - previousOdometer
            if (!odometerDistance.isFinite() || odometerDistance <= 0.0) return@mapNotNull null

            val matchedTrips = completedTrips.filter { trip ->
                val endedAt = trip.endedAtEpochMillis ?: return@filter false
                trip.startedAtEpochMillis >= previous.chargeTimeEpochMillis &&
                    endedAt <= current.chargeTimeEpochMillis
            }
            if (matchedTrips.isEmpty()) return@mapNotNull null

            val tripDistanceKm = matchedTrips.sumOf { it.distanceMeters } / 1000.0
            ChargingTripCoverageInterval(
                previousRecordId = previous.id,
                currentRecordId = current.id,
                odometerDistanceKm = odometerDistance,
                completedTripDistanceKm = tripDistanceKm,
                completedTripCount = matchedTrips.size,
                coverageRatio = tripDistanceKm / odometerDistance,
                distanceDifferenceKm = odometerDistance - tripDistanceKm
            )
        }

        val totalOdometer = intervals.sumOf { it.odometerDistanceKm }
        val totalTrips = intervals.sumOf { it.completedTripDistanceKm }
        return ChargingTripCoverageSummary(
            intervals = intervals,
            odometerDistanceKm = totalOdometer,
            completedTripDistanceKm = totalTrips,
            coverageRatio = if (totalOdometer > 0.0) totalTrips / totalOdometer else null
        )
    }
}
