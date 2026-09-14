package com.evchargebook.ui.trip

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
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
import com.evchargebook.domain.trip.TripGeoPoint
import com.evchargebook.domain.trip.TripRouteContinuity
import com.evchargebook.domain.trip.TripRouteContinuityBuilder
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
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.tile.TileOperation
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
@SuppressLint("ClickableViewAccessibility")
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
    val gapColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .66f).toArgb()
    val routeSource = remember(points) {
        points.map { point ->
            TripGeoPoint(
                latitude = point.latitude,
                longitude = point.longitude,
                capturedAtEpochMillis = point.capturedAtEpochMillis,
                speedMps = trustedTripSpeedMpsV07(point),
                capturedAtElapsedRealtimeNanos = point.capturedAtElapsedRealtimeNanos,
            )
        }
    }
    val continuity = remember(routeSource) { TripRouteContinuityBuilder.build(routeSource) }
    var mapController by remember(viewportKey) { mutableStateOf<MapLibreMap?>(null) }
    var styleLoaded by remember(viewportKey) { mutableStateOf(false) }
    var tileLoaded by remember(viewportKey) { mutableStateOf(false) }
    var tileErrorCount by remember(viewportKey) { mutableStateOf(0) }
    var fullyRendered by remember(viewportKey) { mutableStateOf(false) }

    val mapView = remember(viewportKey, context) {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply {
            onCreate(null)
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_POINTER_DOWN,
                    -> view.parent?.requestDisallowInterceptTouchEvent(true)

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL,
                    -> view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
    }

    val failureListener = remember(viewportKey, onProviderFailure) {
        MapView.OnDidFailLoadingMapListener { onProviderFailure() }
    }
    val tileListener = remember(viewportKey) {
        MapView.OnTileActionListener { operation, _, _, _, _, _, _ ->
            when (operation) {
                TileOperation.LoadFromNetwork,
                TileOperation.LoadFromCache,
                TileOperation.EndParse,
                -> tileLoaded = true

                TileOperation.Error -> tileErrorCount += 1
                else -> Unit
            }
        }
    }
    val renderListener = remember(viewportKey) {
        MapView.OnDidFinishRenderingMapListener { fully ->
            if (fully) fullyRendered = true
        }
    }

    DisposableEffect(mapView, lifecycleOwner, failureListener, tileListener, renderListener) {
        var started = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        var resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        if (started) mapView.onStart()
        if (resumed) mapView.onResume()

        mapView.addOnDidFailLoadingMapListener(failureListener)
        mapView.addOnTileActionListener(tileListener)
        mapView.addOnDidFinishRenderingMapListener(renderListener)
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
            mapView.removeOnTileActionListener(tileListener)
            mapView.removeOnDidFinishRenderingMapListener(renderListener)
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
                points = continuity.cameraFitPoints,
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
                            map.uiSettings.setLogoEnabled(false)
                            map.uiSettings.setCompassEnabled(false)
                            map.uiSettings.setRotateGesturesEnabled(false)
                            map.uiSettings.setTiltGesturesEnabled(false)
                            map.setStyle(Style.Builder().fromUri(BuildConfig.TRIP_MAP_STYLE_URL)) { style ->
                                styleLoaded = true
                                installTripRouteLayersV08(
                                    style = style,
                                    continuity = continuity,
                                    start = routeSource.first(),
                                    end = routeSource.last(),
                                    finalEndpoint = finalEndpoint,
                                    startColor = accentColor,
                                    endColor = endColor,
                                    markerStrokeColor = markerStrokeColor,
                                    gapColor = gapColor,
                                )
                                fitTripRouteV08(
                                    map = map,
                                    mapView = this,
                                    points = continuity.cameraFitPoints,
                                    paddingPx = routePaddingPx,
                                )
                            }
                        }
                    }
                },
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
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

            if (BuildConfig.DEBUG) {
                val statusText = when {
                    tileLoaded && fullyRendered -> "底图 Liberty · 瓦片已加载"
                    tileLoaded -> "底图 Liberty · 正在渲染"
                    tileErrorCount > 0 -> "底图 Liberty · 瓦片错误 $tileErrorCount"
                    styleLoaded -> "底图 Liberty · 等待瓦片"
                    else -> "底图 Liberty · 加载中"
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .94f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .28f)),
                ) {
                    Text(
                        statusText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun fitTripRouteV08(
    map: MapLibreMap,
    mapView: MapView,
    points: List<TripGeoPoint>,
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
    continuity: TripRouteContinuity,
    start: TripGeoPoint,
    end: TripGeoPoint,
    finalEndpoint: Boolean,
    startColor: Int,
    endColor: Int,
    markerStrokeColor: Int,
    gapColor: Int,
) {
    val featuresByBand = buildTripSpeedFeaturesV08(continuity)
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

    if (continuity.gaps.isNotEmpty()) {
        val gapFeatures = continuity.gaps.map { gap ->
            Feature.fromGeometry(
                LineString.fromLngLats(
                    listOf(
                        Point.fromLngLat(gap.from.longitude, gap.from.latitude),
                        Point.fromLngLat(gap.to.longitude, gap.to.latitude),
                    )
                )
            )
        }
        val gapSourceId = "trip-route-gap-source"
        val gapLayerId = "trip-route-gap-layer"
        style.addSource(GeoJsonSource(gapSourceId, FeatureCollection.fromFeatures(gapFeatures.toTypedArray())))
        style.addLayer(
            LineLayer(gapLayerId, gapSourceId).withProperties(
                lineColor(gapColor),
                lineWidth(2.2f),
                lineOpacity(0.72f),
                lineDasharray(arrayOf(2.4f, 2.4f)),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
    }

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
    point: TripGeoPoint,
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
    continuity: TripRouteContinuity,
): Map<TripMapSpeedBandV08, List<Feature>> {
    val byBand = TripMapSpeedBandV08.entries.associateWith { mutableListOf<Feature>() }

    continuity.drawableSegments.forEach { segment ->
        segment.zipWithNext().forEach { (from, to) ->
            val speedKph = (to.speedMps ?: from.speedMps)?.times(3.6)
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
