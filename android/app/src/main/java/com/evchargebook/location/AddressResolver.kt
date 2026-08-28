package com.evchargebook.location

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

interface AddressResolver {
    suspend fun reverse(latitude: Double, longitude: Double): String?
}

class AndroidGeocoderAddressResolver(context: Context) : AddressResolver {
    private val geocoder = Geocoder(context.applicationContext, Locale.SIMPLIFIED_CHINESE)

    override suspend fun reverse(latitude: Double, longitude: Double): String? {
        sharedCache.get(latitude, longitude)?.let { return it }
        return withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            val resolved = runCatching {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)
                    ?.firstOrNull()
                    ?.let { address ->
                        buildList {
                            address.adminArea?.takeIf { it.isNotBlank() }?.let(::add)
                            address.locality?.takeIf { it.isNotBlank() && it != address.adminArea }?.let(::add)
                            address.subLocality?.takeIf { it.isNotBlank() }?.let(::add)
                            address.thoroughfare?.takeIf { it.isNotBlank() }?.let(::add)
                            address.subThoroughfare?.takeIf { it.isNotBlank() }?.let(::add)
                            if (isEmpty()) address.getAddressLine(0)?.takeIf { it.isNotBlank() }?.let(::add)
                        }.joinToString("").takeIf { it.isNotBlank() }
                    }
            }.getOrNull()
            sharedCache.put(latitude, longitude, resolved)
            resolved
        }
    }

    companion object {
        private val sharedCache = GeocodeMemoryCache()
    }
}
