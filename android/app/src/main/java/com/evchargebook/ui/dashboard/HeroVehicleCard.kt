package com.evchargebook.ui.dashboard

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.ui.theme.EVDesignTokens
import com.evchargebook.ui.theme.LocalCockpitColors
import com.evchargebook.ui.theme.spacing
import com.evchargebook.ui.vehicle.OfficialVehicleImageCatalog
import java.util.Locale

private const val VEHICLE_ARTWORK_TAG = "VehicleArtwork"

@Composable
fun HeroVehicleCard(vehicle: VehicleEntity?) {
    val cockpit = LocalCockpitColors.current
    val background = Brush.linearGradient(listOf(Color(0xFF07100C), Color(0xFF0B2417), Color(0xFF07100C)))
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = Color.Transparent) {
        Column(modifier = Modifier.background(background).padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(EVDesignTokens.Energy.green, CircleShape))
                    Spacer(Modifier.size(MaterialTheme.spacing.xs))
                    Text("MY EV", style = MaterialTheme.typography.labelLarge, color = cockpit.secondaryText)
                }
                Surface(color = EVDesignTokens.Energy.green.copy(alpha = 0.12f), shape = CircleShape) {
                    Text("ACTIVE", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = EVDesignTokens.Energy.green)
                }
            }
            Column {
                Text(vehicle?.brand ?: "EV Charge Book", style = MaterialTheme.typography.bodyMedium, color = cockpit.secondaryText)
                Text(vehicle?.model ?: "添加你的第一辆车", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = cockpit.primaryText)
            }
            VehicleStage(vehicle)
            if (vehicle != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    InlineMetric("电池容量", "${one(vehicle.batteryCapacityKwh)} kWh")
                    MetricDivider()
                    InlineMetric("标称续航", "${vehicle.rangeKm} km")
                    MetricDivider()
                    InlineMetric("状态", "可记录")
                }
            } else {
                Text("完成车辆资料后，这里会作为你的车辆主视觉。", style = MaterialTheme.typography.bodyMedium, color = cockpit.secondaryText)
            }
        }
    }
}

@Composable
private fun VehicleStage(vehicle: VehicleEntity?) {
    val artwork = OfficialVehicleImageCatalog.resolve(vehicle)
    remember(vehicle?.catalogVehicleId, vehicle?.brand, vehicle?.model, artwork?.drawableRes) {
        Log.d(VEHICLE_ARTWORK_TAG, "vehicle=${vehicle?.brand}/${vehicle?.model} catalog=${vehicle?.catalogVehicleId} drawable=${artwork?.drawableRes}")
        true
    }
    Box(modifier = Modifier.fillMaxWidth().height(188.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(EVDesignTokens.Energy.green.copy(alpha = 0.20f), EVDesignTokens.Energy.green.copy(alpha = 0.06f), Color.Transparent), center = Offset.Unspecified, radius = 520f)))
        if (artwork != null) {
            Image(painter = painterResource(artwork.drawableRes), contentDescription = "${vehicle?.brand.orEmpty()} ${vehicle?.model.orEmpty()} 车型图", modifier = Modifier.fillMaxWidth().height(184.dp), contentScale = ContentScale.Fit, alignment = Alignment.Center)
        } else {
            VehicleSilhouetteFallback(Modifier.fillMaxSize())
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.82f).height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, EVDesignTokens.Energy.green.copy(alpha = 0.72f), Color.Transparent))))
    }
}

@Composable
private fun VehicleSilhouetteFallback(modifier: Modifier = Modifier) {
    val energy = EVDesignTokens.Energy.green
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawCircle(brush = Brush.radialGradient(listOf(energy.copy(alpha = 0.20f), Color.Transparent), center = Offset(w * 0.58f, h * 0.60f), radius = w * 0.46f), radius = w * 0.46f, center = Offset(w * 0.58f, h * 0.60f))
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
        drawPath(body, brush = Brush.horizontalGradient(listOf(Color(0xFF0D1713), Color(0xFF173728), Color(0xFF0B1511))))
        drawPath(body, color = energy.copy(alpha = .78f), style = Stroke(width = 1.5.dp.toPx()))
        listOf(w * .27f, w * .78f).forEach { x ->
            drawCircle(Color(0xFF050807), 16.dp.toPx(), Offset(x, h * .70f))
            drawCircle(energy.copy(alpha = .60f), 10.dp.toPx(), Offset(x, h * .70f), style = Stroke(1.5.dp.toPx()))
        }
    }
}

@Composable
private fun InlineMetric(label: String, value: String) {
    val cockpit = LocalCockpitColors.current
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = cockpit.secondaryText)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cockpit.primaryText)
    }
}

@Composable
private fun MetricDivider() {
    Box(Modifier.size(width = 1.dp, height = 28.dp).background(LocalCockpitColors.current.secondaryText.copy(alpha = .28f)))
}

private fun one(value: Double) = String.format(Locale.US, "%.1f", value)
