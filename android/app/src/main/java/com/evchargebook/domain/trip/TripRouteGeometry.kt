package com.evchargebook.domain.trip

data class TripGeoPoint(
    val latitude: Double,
    val longitude: Double
)

data class TripRoutePoint(
    val x: Float,
    val y: Float
)

data class TripRouteGeometry(
    val points: List<TripRoutePoint>,
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double
) {
    val isDrawable: Boolean get() = points.size >= 2
}

object TripRouteGeometryBuilder {
    private const val DEFAULT_MAX_POINTS = 400

    fun build(
        source: List<TripGeoPoint>,
        maxPoints: Int = DEFAULT_MAX_POINTS
    ): TripRouteGeometry? {
        if (source.isEmpty() || maxPoints < 2) return null

        val finite = source.filter { it.latitude.isFinite() && it.longitude.isFinite() }
        if (finite.isEmpty()) return null

        val sampled = downsample(finite, maxPoints)
        val minLat = sampled.minOf { it.latitude }
        val maxLat = sampled.maxOf { it.latitude }
        val minLon = sampled.minOf { it.longitude }
        val maxLon = sampled.maxOf { it.longitude }
        val latSpan = (maxLat - minLat).takeIf { it > 0.0 } ?: 1.0
        val lonSpan = (maxLon - minLon).takeIf { it > 0.0 } ?: 1.0

        val normalized = sampled.map { point ->
            TripRoutePoint(
                x = ((point.longitude - minLon) / lonSpan).toFloat().coerceIn(0f, 1f),
                y = (1.0 - (point.latitude - minLat) / latSpan).toFloat().coerceIn(0f, 1f)
            )
        }

        return TripRouteGeometry(
            points = normalized,
            minLatitude = minLat,
            maxLatitude = maxLat,
            minLongitude = minLon,
            maxLongitude = maxLon
        )
    }

    private fun downsample(source: List<TripGeoPoint>, maxPoints: Int): List<TripGeoPoint> {
        if (source.size <= maxPoints) return source

        val lastIndex = source.lastIndex
        return List(maxPoints) { outputIndex ->
            val sourceIndex = ((outputIndex.toDouble() / (maxPoints - 1)) * lastIndex)
                .toInt()
                .coerceIn(0, lastIndex)
            source[sourceIndex]
        }
    }
}
