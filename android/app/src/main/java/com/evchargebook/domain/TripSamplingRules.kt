package com.evchargebook.domain

import kotlin.math.max

data class TripSamplingDecision(
    val accept: Boolean,
    val moving: Boolean,
    val reason: String? = null
)

object TripSamplingRules {
    const val MAX_HORIZONTAL_ACCURACY_METERS = 100.0
    const val MAX_REPORTED_SPEED_MPS = 100.0
    const val MAX_IMPLIED_SPEED_MPS = 90.0
    const val MOVING_SPEED_MPS = 1.0
    const val MOVING_DISTANCE_METERS = 5.0
    const val STATIONARY_STORE_INTERVAL_SECONDS = 15L

    fun decide(
        deltaSeconds: Long,
        segmentDistanceMeters: Double,
        reportedSpeedMps: Double?,
        horizontalAccuracyMeters: Double?
    ): TripSamplingDecision {
        if (deltaSeconds < 0) return TripSamplingDecision(false, false, "时间倒序")
        if (segmentDistanceMeters < 0 || !segmentDistanceMeters.isFinite()) return TripSamplingDecision(false, false, "距离无效")
        if (horizontalAccuracyMeters != null && horizontalAccuracyMeters > MAX_HORIZONTAL_ACCURACY_METERS) {
            return TripSamplingDecision(false, false, "定位精度过低")
        }
        if (reportedSpeedMps != null && (reportedSpeedMps < 0 || reportedSpeedMps > MAX_REPORTED_SPEED_MPS)) {
            return TripSamplingDecision(false, false, "设备速度异常")
        }

        val impliedSpeed = if (deltaSeconds > 0) segmentDistanceMeters / deltaSeconds else 0.0
        if (deltaSeconds > 0 && impliedSpeed > MAX_IMPLIED_SPEED_MPS) {
            return TripSamplingDecision(false, false, "GPS 跳点")
        }

        val moving = (reportedSpeedMps ?: 0.0) >= MOVING_SPEED_MPS || segmentDistanceMeters >= MOVING_DISTANCE_METERS
        if (!moving && deltaSeconds in 1 until STATIONARY_STORE_INTERVAL_SECONDS) {
            return TripSamplingDecision(false, false, "静止降频")
        }

        return TripSamplingDecision(true, moving)
    }

    fun movingSeconds(current: Long, deltaSeconds: Long, moving: Boolean): Long =
        current + if (moving) max(deltaSeconds, 0) else 0

    fun stoppedSeconds(current: Long, deltaSeconds: Long, moving: Boolean): Long =
        current + if (!moving) max(deltaSeconds, 0) else 0
}
