package com.evchargebook.ui.dashboard

import android.graphics.BitmapFactory
import android.util.Base64
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.R
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.ui.theme.EVDesignTokens
import com.evchargebook.ui.theme.spacing
import com.evchargebook.ui.vehicle.OfficialVehicleImageCatalog
import java.util.Locale

private const val VEHICLE_ARTWORK_TAG = "VehicleArtwork"

@Composable
fun HeroVehicleCard(vehicle: VehicleEntity?) {
    val background = Brush.linearGradient(
        listOf(Color(0xFF07100C), Color(0xFF0B2417), Color(0xFF07100C))
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
                        Modifier
                            .size(7.dp)
                            .background(EVDesignTokens.Energy.green, CircleShape)
                    )
                    Spacer(Modifier.size(MaterialTheme.spacing.xs))
                    Text("MY EV", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(color = EVDesignTokens.Energy.green.copy(alpha = 0.12f), shape = CircleShape) {
                    Text(
                        "ACTIVE",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = EVDesignTokens.Energy.green
                    )
                }
            }

            Column {
                Text(vehicle?.brand ?: "EV Charge Book", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun VehicleStage(vehicle: VehicleEntity?) {
    val artwork = OfficialVehicleImageCatalog.resolve(vehicle)
    val context = LocalContext.current

    // BYD Seal is now a real Android drawable. This bypasses the previous
    // Base64 -> byte[] -> BitmapFactory chain that was silently falling back.
    val drawableRes = when (artwork?.assetPath) {
        "vehicle_artwork/byd_seal_2025.webp.b64" -> R.drawable.byd_seal_2025
        else -> null
    }

    val bitmap = remember(artwork?.assetPath, drawableRes) {
        if (drawableRes != null || artwork == null) {
            null
        } else {
            runCatching {
                val encoded = context.assets.open(artwork.assetPath).bufferedReader().use { it.readText() }
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    ?: error("BitmapFactory returned null")
            }.onFailure {
                Log.e(VEHICLE_ARTWORK_TAG, "Failed ${artwork.assetPath}", it)
            }.getOrNull()
        }
    }

    remember(vehicle?.catalogVehicleId, vehicle?.brand, vehicle?.model, artwork?.assetPath, drawableRes) {
        Log.d(
            VEHICLE_ARTWORK_TAG,
            "vehicle=${vehicle?.brand}/${vehicle?.model} catalog=${vehicle?.catalogVehicleId} artwork=${artwork?.assetPath} drawable=$drawableRes"
        )
        true
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(188.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(
                            EVDesignTokens.Energy.green.copy(alpha = 0.20f),
                            EVDesignTokens.Energy.green.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = Offset.Unspecified,
                        radius = 520f
                    )
                )
        )

        when {
            drawableRes != null -> Image(
                painter = painterResource(drawableRes),
                contentDescription = "${vehicle?.brand.orEmpty()} ${vehicle?.model.orEmpty()} 车型图",
                modifier = Modifier.fillMaxWidth().height(184.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center
            )
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = "${vehicle?.brand.orEmpty()} ${vehicle?.model.orEmpty()} 车型图",
                modifier = Modifier.fillMaxWidth().height(184.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center
            )
            else -> VehicleSilhouetteFallback(Modifier.fillMaxSize())
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.82f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, EVDesignTokens.Energy.green.copy(alpha = 0.72f), Color.Transparent)
                    )
                )
        )
    }
}

@Composable
private fun VehicleSilhouetteFallback(modifier: Modifier = Modifier) {
    val energy = EVDesignTokens.Energy.green
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawCircle(
            brush = Brush.radialGradient(
                listOf(energy.copy(alpha = 0.20f), Color.Transparent),
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
        drawPath(body, brush = Brush.horizontalGradient(listOf(Color(0xFF0D1713), Color(0xFF173728), Color(0xFF0B1511))))
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

        drawLine(energy.copy(alpha = 0.32f), Offset(w * 0.10f, h * 0.76f), Offset(w * 0.96f, h * 0.76f), 1.dp.toPx())
        listOf(w * 0.27f, w * 0.78f).forEach { x ->
            drawCircle(Color(0xFF050807), 16.dp.toPx(), Offset(x, h * 0.70f))
            drawCircle(energy.copy(alpha = 0.60f), 10.dp.toPx(), Offset(x, h * 0.70f), style = Stroke(1.5.dp.toPx()))
            drawCircle(Color(0xFF17231D), 5.dp.toPx(), Offset(x, h * 0.70f))
        }
    }
}

@Composable
private fun InlineMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun MetricDivider() {
    Box(Modifier.size(width = 1.dp, height = 28.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)))
}

private fun one(value: Double) = String.format(Locale.US, "%.1f", value)
