package com.evchargebook.ui.trip

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.evchargebook.BuildConfig
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.domain.TripCaptureTimeRules
import com.evchargebook.domain.TripContinuityRules
import com.evchargebook.ui.theme.EVDesignTokens
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Real geographic context for a completed Trip.
 *
 * The basemap is display-only. Persisted WGS84 TripPoint coordinates stay authoritative and are
 * never snapped, converted in storage, or bridged across a LONG_GAP. If the provider/style fails,
 * the caller falls back to the truthful no-basemap renderer.
 */
@Composable
internal fun TripMapContextV08(
    points: List<TripPointEntity>,
    finalEndpoint: Boolean,
    height: Dp,
    onProviderFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) return

    val viewportKey = points.firstOrNull()?.tripId
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val routePaddingPx = with(density) { 42.dp.roundToPx() }
    val accentColor = EVDesignTokens.Energy.green.toArgb()
    val endColor = MaterialTheme.colorScheme.error.toArgb()
    val markerStrokeColor = MaterialTheme.colorScheme.surface.toArgb()
    var mapController by remember(viewportKey) { mutableStateOf<MapLibreMap?>(null) }

    val mapView = remember(viewportKey, context) {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply { onCreate(null) }
    }

    val failureListener = remember(viewportKey, onProviderFailure) {
        MapView.OnDidFailLoadingMapListener { onProviderFailure() }
    }

    DisposableEffect(mapView, lifecycleOwner, failureListener) {
        var started = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        var resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        if (started) mapView.onStart()
        if (resumed) mapView.onResume()

        mapView.addOnDidFailLoadingMapListener(failureListener)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started) {
                    mapView.onStart()
                    started = true
                }
                Lifecycle.Event.ON_RESUME -> if (!resumed) {
                    mapView.onResume()
                    resumed = true
                }
                Lifecycle.Event.ON_PAUSE -> if (resumed) {
                    mapView.onPause()
                    resumed = false
                }
                Lifecycle.Event.ON_STOP -> if (started) {
                    mapView.onStop()
                    started = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.removeOnDidFailLoadingMapListener(failureListener)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }

    fun fitRoute() {
        mapController?.let { map ->
            fitTripRouteV08(
                map = map,
                mapView = mapView,
                points = points,
                paddingPx = routePaddingPx,
            )
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .17f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(20.dp)),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(height),
                factory = {
                    mapView.apply {
                        getMapAsync { map ->
                            mapController = map
                            map.setMinZoomPreference(1.0)
                            map.setMaxZoomPreference(22.5)
                            map.uiSettings.setAttributionEnabled(true)
                            map.uiSettings.setCompassEnabled(false)
                            map.uiSettings.setRotateGesturesEnabled(false)
                            map.uiSettings.setTiltGesturesEnabled(false)
                            map.setStyle(Style.Builder().fromUri(BuildConfig.TRIP_MAP_STYLE_URL)) { style ->
                                installTripRouteLayersV08(
                                    style = style,
                                    points = points,
                                    finalEndpoint = finalEndpoint,
                                    startColor = accentColor,
                                    endColor = endColor,
                                    markerStrokeColor = markerStrokeColor,
                                )
                                fitTripRouteV08(
                                    map = map,
                                    mapView = this,
                                    points = points,
                                    paddingPx = routePaddingPx,
                                )
                            }
                        }
                    }
                },
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MapGestureChipV08("拖动")
                    MapGestureChipV08("双指缩放")
                    MapGestureChipV08("双击放大")
                }
                Surface(
                    onClick = ::fitRoute,
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .90f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .24f)),
                ) {
                    Text(
                        "回到全程",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun MapGestureChipV08(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .86f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .20f)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private fun fitTripRouteV08(
    map: MapLibreMap,
    mapView: MapView,
    points: List<TripPointEntity>,
    paddingPx: Int,
) {
    val latLngs = points.map { LatLng(it.latitude, it.longitude) }
    if (latLngs.size < 2) return
    val bounds = runCatching { LatLngBounds.Builder().includes(latLngs).build() }.getOrNull() ?: return
    mapView.post {
        runCatching {
            map.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds, paddingPx),
                320,
            )
        }
    }
}

private fun installTripRouteLayersV08(
    style: Style,
    points: List<TripPointEntity>,
    finalEndpoint: Boolean,
    startColor: Int,
    endColor: Int,
    markerStrokeColor: Int,
) {
    val featuresByBand = buildTripSpeedFeaturesV08(points)
    TripMapSpeedBandV08.entries.forEach { band ->
        val features = featuresByBand[band].orEmpty()
        if (features.isEmpty()) return@forEach
        val sourceId = "trip-route-${band.sourceSuffix}-source"
        val layerId = "trip-route-${band.sourceSuffix}-layer"
        style.addSource(GeoJsonSource(sourceId, FeatureCollection.fromFeatures(features.toTypedArray())))
        style.addLayer(
            LineLayer(layerId, sourceId).withProperties(
                lineColor(band.argb),
                lineWidth(4.6f),
                lineOpacity(0.98f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
    }

    val start = points.first()
    val end = points.last()
    addTripPointLayerV08(
        style = style,
        sourceId = "trip-start-source",
        layerId = "trip-start-layer",
        point = start,
        color = startColor,
        strokeColor = markerStrokeColor,
        radius = 6.2f,
    )
    addTripPointLayerV08(
        style = style,
        sourceId = "trip-end-source",
        layerId = "trip-end-layer",
        point = end,
        color = if (finalEndpoint) endColor else startColor,
        strokeColor = markerStrokeColor,
        radius = 6.2f,
    )
}

private fun addTripPointLayerV08(
    style: Style,
    sourceId: String,
    layerId: String,
    point: TripPointEntity,
    color: Int,
    strokeColor: Int,
    radius: Float,
) {
    style.addSource(
        GeoJsonSource(
            sourceId,
            Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)),
        )
    )
    style.addLayer(
        CircleLayer(layerId, sourceId).withProperties(
            circleColor(color),
            circleRadius(radius),
            circleStrokeColor(strokeColor),
            circleStrokeWidth(2.2f),
        )
    )
}

private fun buildTripSpeedFeaturesV08(
    points: List<TripPointEntity>,
): Map<TripMapSpeedBandV08, List<Feature>> {
    val byBand = TripMapSpeedBandV08.entries.associateWith { mutableListOf<Feature>() }
    val longGapMillis = TripContinuityRules.LONG_GAP_SECONDS * 1_000L

    points.zipWithNext().forEach { (from, to) ->
        val timing = TripCaptureTimeRules.between(
            previousEpochMillis = from.capturedAtEpochMillis,
            previousElapsedRealtimeNanos = from.capturedAtElapsedRealtimeNanos,
            currentEpochMillis = to.capturedAtEpochMillis,
            currentElapsedRealtimeNanos = to.capturedAtElapsedRealtimeNanos,
        )
        if (!timing.accepted || timing.breaksContinuity(longGapMillis)) return@forEach

        val speedKph = (trustedTripSpeedMpsV07(to) ?: trustedTripSpeedMpsV07(from))?.times(3.6)
        val band = tripMapSpeedBandV08(speedKph)
        byBand.getValue(band).add(
            Feature.fromGeometry(
                LineString.fromLngLats(
                    listOf(
                        Point.fromLngLat(from.longitude, from.latitude),
                        Point.fromLngLat(to.longitude, to.latitude),
                    )
                )
            )
        )
    }

    return byBand
}

private enum class TripMapSpeedBandV08(
    val sourceSuffix: String,
    val argb: Int,
) {
    UNKNOWN("unknown", 0xFF78818D.toInt()),
    LOW("low", 0xFFFF4D5A.toInt()),
    LOW_MID("low-mid", 0xFFFF982E.toInt()),
    MID("mid", 0xFFFFD928.toInt()),
    CRUISE("cruise", 0xFF2FE36F.toInt()),
    FAST("fast", 0xFF2BD9E8.toInt()),
    HIGH("high", 0xFF4C7DFF.toInt()),
    VERY_HIGH("very-high", 0xFFB64CFF.toInt()),
}

private fun tripMapSpeedBandV08(speedKph: Double?): TripMapSpeedBandV08 = when {
    speedKph == null || !speedKph.isFinite() -> TripMapSpeedBandV08.UNKNOWN
    speedKph < 5.0 -> TripMapSpeedBandV08.LOW
    speedKph < 15.0 -> TripMapSpeedBandV08.LOW_MID
    speedKph < 30.0 -> TripMapSpeedBandV08.MID
    speedKph < 50.0 -> TripMapSpeedBandV08.CRUISE
    speedKph < 70.0 -> TripMapSpeedBandV08.FAST
    speedKph < 90.0 -> TripMapSpeedBandV08.HIGH
    else -> TripMapSpeedBandV08.VERY_HIGH
}
