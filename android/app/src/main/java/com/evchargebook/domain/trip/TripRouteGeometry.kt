package com.evchargebook.domain.trip

import com.evchargebook.domain.TripCaptureTimeRules

data class TripGeoPoint(
    val latitude: Double,
    val longitude: Double,
    val capturedAtEpochMillis: Long? = null,
    val speedMps: Double? = null,
    val capturedAtElapsedRealtimeNanos: Long? = null,
)

data class TripRouteGap(
    val from: TripGeoPoint,
    val to: TripGeoPoint,
)

data class TripGeoBounds(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
)

data class TripRouteContinuity(
    val segments: List<List<TripGeoPoint>>,
    val gaps: List<TripRouteGap>,
) {
    val drawableSegments: List<List<TripGeoPoint>>
        get() = segments.filter { it.size >= 2 }

    /**
     * Default framing favors substantial continuous route context. A tiny fragment after a long
     * GPS gap stays truthful data, but it must not crush the useful route into a tiny viewport.
     * Point count is a deliberately small heuristic here because accepted Trip points use a common
     * sampling cadence; every omitted fragment remains available through fullRouteFitPoints.
     */
    val defaultFitPoints: List<TripGeoPoint>
        get() {
            val drawable = drawableSegments
            if (drawable.isEmpty()) return segments.flatten()

            val largestSegmentSize = drawable.maxOf { it.size }
            val substantialSegments = drawable.filter { segment ->
                segment.size >= 3 && segment.size * 2 >= largestSegmentSize
            }
            return substantialSegments.ifEmpty { drawable }.flatten()
        }

    /** Every persisted finite point, used only when the user explicitly asks to see the full Trip. */
    val fullRouteFitPoints: List<TripGeoPoint>
        get() = segments.flatten()

    val defaultBounds: TripGeoBounds?
        get() = boundsOf(defaultFitPoints)

    val fullRouteBounds: TripGeoBounds?
        get() = boundsOf(fullRouteFitPoints)

    /** Compatibility alias for older callers; new code should choose default or full explicitly. */
    val cameraFitPoints: List<TripGeoPoint>
        get() = defaultFitPoints

    private fun boundsOf(points: List<TripGeoPoint>): TripGeoBounds? {
        if (points.isEmpty()) return null
        return TripGeoBounds(
            minLatitude = points.minOf { it.latitude },
            maxLatitude = points.maxOf { it.latitude },
            minLongitude = points.minOf { it.longitude },
            maxLongitude = points.maxOf { it.longitude },
        )
    }
}

object TripRouteContinuityBuilder {
    fun build(source: List<TripGeoPoint>): TripRouteContinuity {
        val finite = source.filter { it.latitude.isFinite() && it.longitude.isFinite() }
        if (finite.isEmpty()) return TripRouteContinuity(emptyList(), emptyList())

        val segments = mutableListOf<MutableList<TripGeoPoint>>()
        val gaps = mutableListOf<TripRouteGap>()
        var current = mutableListOf(finite.first())
        segments += current

        finite.zipWithNext().forEach { (previous, next) ->
            val previousTime = previous.capturedAtEpochMillis
            val nextTime = next.capturedAtEpochMillis
            val breaksContinuity = if (previousTime != null && nextTime != null) {
                val timing = TripCaptureTimeRules.between(
                    previousEpochMillis = previousTime,
                    previousElapsedRealtimeNanos = previous.capturedAtElapsedRealtimeNanos,
                    currentEpochMillis = nextTime,
                    currentElapsedRealtimeNanos = next.capturedAtElapsedRealtimeNanos,
                )
                !timing.accepted || timing.breaksContinuity(TripRouteGeometryBuilder.LONG_GAP_THRESHOLD_MS)
            } else {
                false
            }

            if (breaksContinuity) {
                gaps += TripRouteGap(from = previous, to = next)
                current = mutableListOf()
                segments += current
            }
            current += next
        }

        return TripRouteContinuity(
            segments = segments.filter { it.isNotEmpty() },
            gaps = gaps,
        )
    }
}

data class TripRoutePoint(
    val x: Float,
    val y: Float,
    val speedMps: Double? = null
)

data class TripRouteGeometry(
    val points: List<TripRoutePoint>,
    val segments: List<List<TripRoutePoint>>,
    val gapCount: Int,
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double
) {
    val isDrawable: Boolean get() = segments.any { it.size >= 2 }
}

object TripRouteGeometryBuilder {
    private const val DEFAULT_MAX_POINTS = 400
    const val LONG_GAP_THRESHOLD_MS = 120_000L

    fun build(
        source: List<TripGeoPoint>,
        maxPoints: Int = DEFAULT_MAX_POINTS
    ): TripRouteGeometry? {
        if (source.isEmpty() || maxPoints < 2) return null

        val continuity = TripRouteContinuityBuilder.build(source)
        if (continuity.segments.isEmpty()) return null

        val sampledSegments = downsampleSegments(continuity.segments, maxPoints)
        val sampled = sampledSegments.flatten()
        if (sampled.isEmpty()) return null

        val minLat = sampled.minOf { it.latitude }
        val maxLat = sampled.maxOf { it.latitude }
        val minLon = sampled.minOf { it.longitude }
        val maxLon = sampled.maxOf { it.longitude }
        val latSpan = (maxLat - minLat).takeIf { it > 0.0 } ?: 1.0
        val lonSpan = (maxLon - minLon).takeIf { it > 0.0 } ?: 1.0

        fun normalize(point: TripGeoPoint) = TripRoutePoint(
            x = ((point.longitude - minLon) / lonSpan).toFloat().coerceIn(0f, 1f),
            y = (1.0 - (point.latitude - minLat) / latSpan).toFloat().coerceIn(0f, 1f),
            speedMps = point.speedMps
        )

        val normalizedSegments = sampledSegments.map { segment -> segment.map(::normalize) }

        return TripRouteGeometry(
            points = normalizedSegments.flatten(),
            segments = normalizedSegments,
            gapCount = (normalizedSegments.size - 1).coerceAtLeast(0),
            minLatitude = minLat,
            maxLatitude = maxLat,
            minLongitude = minLon,
            maxLongitude = maxLon
        )
    }

    private fun downsampleSegments(source: List<List<TripGeoPoint>>, maxPoints: Int): List<List<TripGeoPoint>> {
        val totalPoints = source.sumOf { it.size }
        if (totalPoints <= maxPoints) return source
        if (source.size >= maxPoints) return source.take(maxPoints).map { listOf(it.first()) }

        val baseBudget = maxPoints / source.size
        val remainder = maxPoints % source.size
        return source.mapIndexed { index, segment ->
            val budget = baseBudget + if (index < remainder) 1 else 0
            downsample(segment, budget.coerceAtLeast(1))
        }
    }

    private fun downsample(source: List<TripGeoPoint>, maxPoints: Int): List<TripGeoPoint> {
        if (source.size <= maxPoints) return source
        if (maxPoints <= 1) return listOf(source.first())

        val lastIndex = source.lastIndex
        return List(maxPoints) { outputIndex ->
            val sourceIndex = ((outputIndex.toDouble() / (maxPoints - 1)) * lastIndex)
                .toInt()
                .coerceIn(0, lastIndex)
            source[sourceIndex]
        }
    }
}
