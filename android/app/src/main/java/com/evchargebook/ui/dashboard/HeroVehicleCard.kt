package com.evchargebook.ui.dashboard

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.ui.theme.EVDesignTokens
import com.evchargebook.ui.theme.spacing
import com.evchargebook.ui.vehicle.OfficialVehicleImageCatalog
import java.util.Locale

/**
 * Vehicle-first hero for the v0.5 dashboard.
 *
 * Uses stored vehicle facts and, when a strict model match exists, official
 * manufacturer media artwork. Unknown models keep the local EV illustration so
 * the UI never shows a misleading vehicle.
 */
@Composable
fun HeroVehicleCard(vehicle: VehicleEntity?) {
    val background = Brush.linearGradient(
        colors = listOf(
            Color(0xFF07100C),
            Color(0xFF0B2417),
            Color(0xFF07100C)
        )
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .background(background)
                .padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(EVDesignTokens.Energy.green, CircleShape)
                    )
                    Spacer(Modifier.size(MaterialTheme.spacing.xs))
                    Text(
                        "MY EV",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = EVDesignTokens.Energy.green.copy(alpha = 0.12f),
                    shape = CircleShape
                ) {
                    Text(
                        "ACTIVE",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = EVDesignTokens.Energy.green
                    )
                }
            }

            Column {
                Text(
                    vehicle?.brand ?: "EV Charge Book",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    vehicle?.model ?: "添加你的第一辆车",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            VehicleStage(vehicle)

            if (vehicle != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InlineMetric("电池容量", "${one(vehicle.batteryCapacityKwh)} kWh")
                    MetricDivider()
                    InlineMetric("标称续航", "${vehicle.rangeKm} km")
                    MetricDivider()
                    InlineMetric("状态", "可记录")
                }
            } else {
                Text(
                    "完成车辆资料后，这里会作为你的车辆主视觉。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Vehicle artwork lives inside the hero instead of inside a second visible card.
 * Manufacturer photos are deliberately softened into the dark-green stage with
 * edge fades and a tint so a studio background never appears as a pasted rectangle.
 */
@Composable
private fun VehicleStage(vehicle: VehicleEntity?) {
    val officialImage = OfficialVehicleImageCatalog.resolve(vehicle)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(184.dp),
        contentAlignment = Alignment.Center
    ) {
        // Ambient energy glow remains visible behind both remote and fallback art.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            EVDesignTokens.Energy.green.copy(alpha = 0.18f),
                            EVDesignTokens.Energy.green.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = Offset.Unspecified,
                        radius = 520f
                    )
                )
        )

        if (officialImage == null) {
            VehicleSilhouetteFallback(Modifier.fillMaxSize())
            return@Box
        }

        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(officialImage.imageUrl)
                .crossfade(300)
                .build(),
            contentDescription = "${vehicle?.brand.orEmpty()} ${vehicle?.model.orEmpty()} 官方车型图",
            modifier = Modifier
                .fillMaxWidth()
                .height(172.dp),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center
        )

        // Turn the source photograph into part of the hero instead of a rectangular photo card.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x4A07100C))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color(0xE807100C),
                            0.17f to Color(0x5207100C),
                            0.58f to Color(0x1207100C),
                            0.82f to Color(0x7307100C),
                            1.00f to Color(0xF207100C)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.00f to Color(0xE607100C),
                            0.14f to Color(0x4807100C),
                            0.50f to Color.Transparent,
                            0.86f to Color(0x4807100C),
                            1.00f to Color(0xE607100C)
                        )
                    )
                )
        )

        // A subtle ground light visually anchors the car to the EV cockpit.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.78f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            EVDesignTokens.Energy.green.copy(alpha = 0.58f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
private fun VehicleSilhouetteFallback(modifier: Modifier = Modifier) {
    val energy = EVDesignTokens.Energy.green
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(energy.copy(alpha = 0.20f), Color.Transparent),
                center = Offset(w * 0.58f, h * 0.60f),
                radius = w * 0.46f
            ),
            radius = w * 0.46f,
            center = Offset(w * 0.58f, h * 0.60f)
        )

        val body = Path().apply {
            moveTo(w * 0.10f, h * 0.68f)
            cubicTo(w * 0.16f, h * 0.61f, w * 0.23f, h * 0.57f, w * 0.30f, h * 0.55f)
            cubicTo(w * 0.36f, h * 0.39f, w * 0.43f, h * 0.31f, w * 0.54f, h * 0.30f)
            lineTo(w * 0.66f, h * 0.30f)
            cubicTo(w * 0.73f, h * 0.33f, w * 0.79f, h * 0.43f, w * 0.84f, h * 0.53f)
            cubicTo(w * 0.91f, h * 0.55f, w * 0.95f, h * 0.60f, w * 0.96f, h * 0.68f)
            lineTo(w * 0.91f, h * 0.72f)
            lineTo(w * 0.14f, h * 0.72f)
            close()
        }

        drawPath(
            path = body,
            brush = Brush.horizontalGradient(
                listOf(Color(0xFF0D1713), Color(0xFF173728), Color(0xFF0B1511))
            )
        )
        drawPath(body, color = energy.copy(alpha = 0.78f), style = Stroke(width = 1.5.dp.toPx()))

        val glass = Path().apply {
            moveTo(w * 0.34f, h * 0.54f)
            lineTo(w * 0.44f, h * 0.35f)
            lineTo(w * 0.62f, h * 0.35f)
            lineTo(w * 0.74f, h * 0.54f)
            close()
        }
        drawPath(glass, color = Color(0xFF07110D))
        drawPath(glass, color = energy.copy(alpha = 0.30f), style = Stroke(width = 1.dp.toPx()))

        drawLine(
            color = energy.copy(alpha = 0.32f),
            start = Offset(w * 0.10f, h * 0.76f),
            end = Offset(w * 0.96f, h * 0.76f),
            strokeWidth = 1.dp.toPx()
        )

        listOf(w * 0.27f, w * 0.78f).forEach { x ->
            drawCircle(Color(0xFF050807), radius = 16.dp.toPx(), center = Offset(x, h * 0.70f))
            drawCircle(energy.copy(alpha = 0.60f), radius = 10.dp.toPx(), center = Offset(x, h * 0.70f), style = Stroke(1.5.dp.toPx()))
            drawCircle(Color(0xFF17231D), radius = 5.dp.toPx(), center = Offset(x, h * 0.70f))
        }
    }
}

@Composable
private fun InlineMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MetricDivider() {
    Box(
        modifier = Modifier
            .size(width = 1.dp, height = 28.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    )
}

private fun one(value: Double) = String.format(Locale.US, "%.1f", value)
