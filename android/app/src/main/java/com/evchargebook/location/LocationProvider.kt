package com.evchargebook.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float
)

interface LocationProvider {
    suspend fun currentLocation(): LocationFix
}

class AndroidLocationProvider(private val context: Context) : LocationProvider {
    override suspend fun currentLocation(): LocationFix = suspendCancellableCoroutine { continuation ->
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            continuation.resumeWithException(SecurityException("尚未授予定位权限"))
            return@suspendCancellableCoroutine
        }

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) {
            continuation.resumeWithException(IllegalStateException("请先开启系统定位服务"))
            return@suspendCancellableCoroutine
        }

        val cancellationSignal = CancellationSignal()
        continuation.invokeOnCancellation { cancellationSignal.cancel() }
        LocationManagerCompat.getCurrentLocation(
            manager,
            provider,
            cancellationSignal,
            ContextCompat.getMainExecutor(context)
        ) { location ->
            if (!continuation.isActive) return@getCurrentLocation
            if (location == null) continuation.resumeWithException(IllegalStateException("暂时无法获取当前位置，请稍后重试"))
            else continuation.resume(LocationFix(location.latitude, location.longitude, location.accuracy))
        }
    }
}
