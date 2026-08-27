package com.evchargebook.domain.trip

data class TripGeoPoint(
    val latitude: Double,
    val longitude: Double,
    val capturedAtEpochMillis: Long? = null
)

data class TripRoutePoint(
    val x: Float,
    val y: Float
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

        val finite = source.filter { it.latitude.isFinite() && it.longitude.isFinite() }
        if (finite.isEmpty()) return null

        val rawSegments = splitAtLongGaps(finite)
        val sampledSegments = downsampleSegments(rawSegments, maxPoints)
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
            y = (1.0 - (point.latitude - minLat) / latSpan).toFloat().coerceIn(0f, 1f)
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

    private fun splitAtLongGaps(source: List<TripGeoPoint>): List<List<TripGeoPoint>> {
        if (source.isEmpty()) return emptyList()
        val segments = mutableListOf<MutableList<TripGeoPoint>>()
        var current = mutableListOf(source.first())
        segments += current

        source.zipWithNext().forEach { (previous, next) ->
            val previousTime = previous.capturedAtEpochMillis
            val nextTime = next.capturedAtEpochMillis
            val isLongGap = previousTime != null && nextTime != null && nextTime - previousTime >= LONG_GAP_THRESHOLD_MS
            if (isLongGap) {
                current = mutableListOf()
                segments += current
            }
            current += next
        }
        return segments.filter { it.isNotEmpty() }
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
