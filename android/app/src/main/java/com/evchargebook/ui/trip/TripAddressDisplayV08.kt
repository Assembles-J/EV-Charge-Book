package com.evchargebook.ui.trip

/**
 * Presentation-only address compaction for Trip surfaces.
 *
 * Reverse-geocoded strings remain the source of truth. We only remove a shared leading city
 * context when both endpoints are inside the same city. Cross-city trips keep their city/province
 * prefix so the direction remains unambiguous.
 */
internal data class TripEndpointDisplayV08(
    val start: String,
    val end: String,
)

internal fun compactTripEndpointDisplayV08(start: String, end: String): TripEndpointDisplayV08 {
    val startCityPrefix = cityPrefixV08(start)
    val endCityPrefix = cityPrefixV08(end)
    if (startCityPrefix == null || startCityPrefix != endCityPrefix) {
        return TripEndpointDisplayV08(start = start, end = end)
    }

    val compactStart = start.removePrefix(startCityPrefix).ifBlank { start }
    val compactEnd = end.removePrefix(endCityPrefix).ifBlank { end }
    return TripEndpointDisplayV08(start = compactStart, end = compactEnd)
}

private fun cityPrefixV08(address: String): String? {
    if (address.isBlank() || address.contains(',')) return null
    val cityIndex = address.indexOf('市')
    if (cityIndex !in 1..11) return null

    val leading = address.substring(0, cityIndex + 1)
    val provinceIndex = leading.indexOf('省')
    return when {
        provinceIndex >= 0 -> leading
        leading.length >= 3 -> leading
        else -> null
    }
}
