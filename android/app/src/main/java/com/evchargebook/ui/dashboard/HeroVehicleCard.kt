package com.evchargebook.ui.dashboard

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.ui.theme.EVDesignTokens
import com.evchargebook.ui.theme.LocalCockpitColors
import com.evchargebook.ui.theme.spacing
import com.evchargebook.ui.vehicle.OfficialVehicleImageCatalog
import java.util.Locale

private const val VEHICLE_ARTWORK_TAG = "VehicleArtwork"

@Composable
fun HeroVehicleCard(
    vehicle: VehicleEntity?,
    currentSoc: Int? = null,
    currentMileageKm: Double? = null,
    latestTrip: TripSessionEntity? = null
) {
    val cockpit = LocalCockpitColors.current
    val background = Brush.linearGradient(
        listOf(
            Color(0xFF06100C),
            Color(0xFF0A2116),
            Color(0xFF07110D)
        )
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent
    ) {
        Column(modifier = Modifier.background(background)) {
            Column(
                modifier = Modifier.padding(
                    start = MaterialTheme.spacing.lg,
                    end = MaterialTheme.spacing.lg,
                    top = MaterialTheme.spacing.lg,
                    bottom = MaterialTheme.spacing.sm
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(EVDesignTokens.Energy.green, CircleShape))
                    Spacer(Modifier.size(MaterialTheme.spacing.xs))
                    Text("MY EV", style = MaterialTheme.typography.labelLarge, color = cockpit.secondaryText)
                }
                Spacer(Modifier.height(MaterialTheme.spacing.lg))
                Text(
                    vehicle?.brand ?: "EV Charge Book",
                    style = MaterialTheme.typography.bodyLarge,
                    color = cockpit.secondaryText
                )
                Text(
                    vehicle?.model ?: "添加你的第一辆车",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cockpit.primaryText
                )
            }

            Box(modifier = Modifier.fillMaxWidth().height(304.dp)) {
                VehicleStage(vehicle, Modifier.fillMaxSize())
                if (vehicle != null) {
                    HeroDynamicStateOverlay(
                        currentSoc = currentSoc,
                        currentMileageKm = currentMileageKm,
                        latestTrip = latestTrip,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VehicleStage(vehicle: VehicleEntity?, modifier: Modifier = Modifier) {
    val artwork = OfficialVehicleImageCatalog.resolve(vehicle)
    remember(vehicle?.catalogVehicleId, vehicle?.brand, vehicle?.model, artwork?.drawableRes) {
        Log.d(
            VEHICLE_ARTWORK_TAG,
            "vehicle=${vehicle?.brand}/${vehicle?.model} catalog=${vehicle?.catalogVehicleId} drawable=${artwork?.drawableRes}"
        )
        true
    }

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(
                    Color(0xFF07110D),
                    Color(0xFF0A1712),
                    Color(0xFF06100C)
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        if (artwork != null) {
            Image(
                painter = painterResource(artwork.drawableRes),
                contentDescription = "${vehicle?.brand.orEmpty()} ${vehicle?.model.orEmpty()} 车型图",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center
            )
        } else {
            VehicleSilhouetteFallback(Modifier.fillMaxSize())
        }

        // Finished generated artwork owns its aurora/reflection effects. Compose only adds a
        // restrained edge blend so titles and the translucent state panel remain legible.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color(0x2206100C),
                        Color.Transparent,
                        Color(0x5506100C)
                    )
                )
            )
        )
    }
}

@Composable
private fun HeroDynamicStateOverlay(
    currentSoc: Int?,
    currentMileageKm: Double?,
    latestTrip: TripSessionEntity?,
    modifier: Modifier = Modifier
) {
    val safeSoc = currentSoc?.coerceIn(0, 100)
    val targetProgress = safeSoc?.div(100f) ?: 0f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 650),
        label = "dashboard_hero_soc"
    )
    val panelShape = MaterialTheme.shapes.large
    val fontScale = LocalConfiguration.current.fontScale

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.14f), panelShape),
        shape = panelShape,
        color = Color(0xD9091511)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 340.dp || fontScale >= 1.3f
            if (compact) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HeroSocMetric(safeSoc, animatedProgress, Modifier.weight(1f))
                        MetricDivider()
                        HeroMetric(
                            label = "当前里程",
                            value = currentMileageKm?.let(::formatMileage) ?: "--",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(LocalCockpitColors.current.secondaryText.copy(alpha = .20f))
                    )
                    HeroRecentTripMetric(trip = latestTrip, modifier = Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    HeroSocMetric(safeSoc, animatedProgress, Modifier.weight(0.95f))
                    MetricDivider()
                    HeroMetric(
                        label = "当前里程",
                        value = currentMileageKm?.let(::formatMileage) ?: "--",
                        modifier = Modifier.weight(1f)
                    )
                    MetricDivider()
                    HeroRecentTripMetric(trip = latestTrip, modifier = Modifier.weight(1.05f))
                }
            }
        }
    }
}

@Composable
private fun HeroSocMetric(safeSoc: Int?, animatedProgress: Float, modifier: Modifier = Modifier) {
    val cockpit = LocalCockpitColors.current
    Column(modifier = modifier) {
        Text(
            safeSoc?.let { "$it%" } ?: "--",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (safeSoc != null) EVDesignTokens.Energy.green else cockpit.primaryText
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(cockpit.secondaryText.copy(alpha = 0.24f))
        ) {
            if (safeSoc != null) {
                Box(
                    Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxSize()
                        .background(EVDesignTokens.Energy.green)
                )
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val cockpit = LocalCockpitColors.current
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = cockpit.secondaryText)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            color = cockpit.primaryText
        )
    }
}

@Composable
private fun HeroRecentTripMetric(trip: TripSessionEntity?, modifier: Modifier = Modifier) {
    val cockpit = LocalCockpitColors.current
    val distance = trip?.distanceMeters
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?.let(::formatTripDistance)
        ?: "--"
    val consumption = trip?.averageConsumptionKwhPer100Km
        ?.takeIf { it.isFinite() && it >= 0.0 }
        ?.let { String.format(Locale.US, "%.1f kWh/100km", it) }

    Column(modifier = modifier) {
        Text("最近行程", style = MaterialTheme.typography.labelMedium, color = cockpit.secondaryText)
        Spacer(Modifier.height(4.dp))
        Text(
            distance,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            color = cockpit.primaryText
        )
        if (consumption != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                consumption,
                style = MaterialTheme.typography.labelMedium,
                color = EVDesignTokens.Energy.green
            )
        }
    }
}

@Composable
private fun MetricDivider() {
    Box(
        Modifier
            .size(width = 1.dp, height = 54.dp)
            .background(LocalCockpitColors.current.secondaryText.copy(alpha = .24f))
    )
}

@Composable
private fun VehicleSilhouetteFallback(modifier: Modifier = Modifier) {
    val energy = EVDesignTokens.Energy.green
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawCircle(
            brush = Brush.radialGradient(
                listOf(energy.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(w * 0.58f, h * 0.60f),
                radius = w * 0.46f
            ),
            radius = w * 0.46f,
            center = Offset(w * 0.58f, h * 0.60f)
        )
        val body = Path().apply {
            moveTo(w * .10f, h * .68f)
            cubicTo(w * .16f, h * .61f, w * .23f, h * .57f, w * .30f, h * .55f)
            cubicTo(w * .36f, h * .39f, w * .43f, h * .31f, w * .54f, h * .30f)
            lineTo(w * .66f, h * .30f)
            cubicTo(w * .73f, h * .33f, w * .79f, h * .43f, w * .84f, h * .53f)
            cubicTo(w * .91f, h * .55f, w * .95f, h * .60f, w * .96f, h * .68f)
            lineTo(w * .91f, h * .72f)
            lineTo(w * .14f, h * .72f)
            close()
        }
        drawPath(
            body,
            brush = Brush.horizontalGradient(
                listOf(Color(0xFF0D1713), Color(0xFF173728), Color(0xFF0B1511))
            )
        )
        drawPath(body, color = energy.copy(alpha = .72f), style = Stroke(width = 1.5.dp.toPx()))
        listOf(w * .27f, w * .78f).forEach { x ->
            drawCircle(Color(0xFF050807), 16.dp.toPx(), Offset(x, h * .70f))
            drawCircle(
                energy.copy(alpha = .55f),
                10.dp.toPx(),
                Offset(x, h * .70f),
                style = Stroke(1.5.dp.toPx())
            )
        }
    }
}

private fun formatMileage(value: Double): String =
    if (value % 1.0 == 0.0) {
        String.format(Locale.US, "%,.0f km", value)
    } else {
        String.format(Locale.US, "%,.1f km", value)
    }

private fun formatTripDistance(meters: Double): String =
    if (meters >= 1000.0) {
        String.format(Locale.US, "%.1f km", meters / 1000.0)
    } else {
        String.format(Locale.US, "%.0f m", meters)
    }
