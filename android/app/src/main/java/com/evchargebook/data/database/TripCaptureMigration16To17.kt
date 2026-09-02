package com.evchargebook.data.database

/**
 * v17 adds the Android monotonic capture clock to Trip points.
 *
 * Existing rows intentionally remain NULL: elapsedRealtimeNanos is boot-relative and cannot be
 * reconstructed honestly from wall-clock timestamps after the fact. Legacy rows therefore keep
 * using the explicit epoch fallback path in TripCaptureTimeRules.
 */
object TripCaptureMigration16To17 {
    val statements: List<String> = listOf(
        "ALTER TABLE trip_points ADD COLUMN capturedAtElapsedRealtimeNanos INTEGER DEFAULT NULL",
    )
}
