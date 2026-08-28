package com.evchargebook.ui.dashboard

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.ui.theme.EVDesignTokens
import com.evchargebook.ui.theme.LocalCockpitColors
import com.evchargebook.ui.vehicle.HeroArtworkManifestRepository
import com.evchargebook.ui.vehicle.OfficialVehicleImageCatalog
import kotlinx.coroutines.flow.flowOf
import java.util.Locale

private const val VEHICLE_ARTWORK_TAG = "VehicleArtwork"

@Composable
fun HeroVehicleCard(
    vehicle: VehicleEntity?,
    currentSoc: Int? = null,
    currentMileageKm: Double? = null,
    latestTrip: TripSessionEntity? = null,
    vehicles: List<VehicleEntity> = emptyList(),
    vehicleSwitchEnabled: Boolean = true,
    onSelectVehicle: (Long) -> Unit = {},
    artworkKey: String? = null
) {
    val cockpit = LocalCockpitColors.current
    val context = LocalContext.current
    val catalogId = vehicle?.catalogVehicleId?.trim()?.takeIf { it.isNotEmpty() }
    val localArtworkKey by remember(catalogId, context.applicationContext) {
        catalogId?.let {
            AppDatabase.getInstance(context.applicationContext)
                .vehicleCatalogDao()
                .observeHeroArtworkKey(it)
        } ?: flowOf(null)
    }.collectAsState(initial = null)
    val effectiveArtworkKey = artworkKey?.trim()?.takeIf { it.isNotEmpty() } ?: localArtworkKey

    val selectableVehicles = if (vehicles.isNotEmpty()) vehicles else listOfNotNull(vehicle)
    val canSwitchVehicle = vehicleSwitchEnabled && selectableVehicles.size > 1
    var vehicleMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Color(0xFF06100C)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.46f)
            ) {
                VehicleStage(vehicle, effectiveArtworkKey, Modifier.fillMaxSize())
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x7A020806),
                                    Color(0x16020806),
                                    Color.Transparent,
                                    Color(0x12020806)
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 14.dp, end = 72.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(EVDesignTokens.Energy.green, CircleShape))
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "MY EV",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = cockpit.primaryText
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        vehicle?.let { "${it.brand}  ${it.model}" } ?: "EV Charge Book",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        color = cockpit.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (vehicle != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 12.dp, end = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0x8F07110F))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                                .clickable(enabled = canSwitchVehicle) {
                                    vehicleMenuExpanded = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = if (vehicleSwitchEnabled) "切换车辆" else "行程进行中不可切换车辆",
                                tint = Color.White.copy(alpha = if (vehicleSwitchEnabled) 1f else 0.45f),
                                modifier = Modifier.size(23.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = vehicleMenuExpanded && canSwitchVehicle,
                            onDismissRequest = { vehicleMenuExpanded = false }
                        ) {
                            selectableVehicles.forEach { candidate ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                candidate.model,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (candidate.id == vehicle.id) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                            Text(
                                                candidate.brand,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        vehicleMenuExpanded = false
                                        onSelectVehicle(candidate.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (vehicle != null) {
                HeroDynamicStatePanel(
                    currentSoc = currentSoc,
                    currentMileageKm = currentMileageKm,
                    latestTrip = latestTrip,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun VehicleStage(
    vehicle: VehicleEntity?,
    artworkKey: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val artwork = OfficialVehicleImageCatalog.resolve(vehicle, artworkKey)
    var remoteArtwork by remember(artwork?.key) {
        mutableStateOf<HeroArtworkManifestRepository.RemoteArtwork?>(null)
    }

    LaunchedEffect(artwork?.key) {
        remoteArtwork = artwork?.let { HeroArtworkManifestRepository.resolve(context, it.key) }
    }

    val imageUrl = remoteArtwork?.url ?: artwork?.remoteFallbackUrl
    val cacheVersion = remoteArtwork?.version ?: 0

    remember(vehicle?.catalogVehicleId, vehicle?.brand, vehicle?.model, artworkKey, artwork?.key, imageUrl) {
        Log.d(
            VEHICLE_ARTWORK_TAG,
            "vehicle=${vehicle?.brand}/${vehicle?.model} catalog=${vehicle?.catalogVehicleId} artwork=${artwork?.key} remote=${imageUrl != null}"
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
        Icon(
            imageVector = Icons.Default.DirectionsCar,
            contentDescription = null,
            tint = EVDesignTokens.Energy.green.copy(alpha = 0.42f),
            modifier = Modifier.size(72.dp)
        )

        if (artwork != null && imageUrl != null) {
            val cacheKey = "vehicle-hero:${artwork.key}:v$cacheVersion"
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = "${vehicle?.brand.orEmpty()} ${vehicle?.model.orEmpty()} 车型图",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center
            )
        }
    }
}

@Composable
private fun HeroDynamicStatePanel(
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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.12f), panelShape),
        shape = panelShape,
        color = Color(0xFF091511)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroSocMetric(
                safeSoc = safeSoc,
                animatedProgress = animatedProgress,
                modifier = Modifier.weight(0.92f)
            )
            MetricDivider()
            HeroMetric(
                icon = Icons.Default.Speed,
                label = "当前里程",
                value = currentMileageKm?.let(::formatMileage) ?: "--",
                unit = if (currentMileageKm != null) "km" else null,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )
            MetricDivider()
            HeroRecentTripMetric(
                trip = latestTrip,
                modifier = Modifier
                    .weight(1.08f)
                    .padding(start = 12.dp)
            )
        }
    }
}

@Composable
private fun HeroSocMetric(safeSoc: Int?, animatedProgress: Float, modifier: Modifier = Modifier) {
    val cockpit = LocalCockpitColors.current
    Column(modifier = modifier.padding(end = 12.dp)) {
        if (safeSoc != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    safeSoc.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = EVDesignTokens.Energy.green
                )
                Text(
                    "%",
                    modifier = Modifier.padding(bottom = 2.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = cockpit.primaryText
                )
            }
        } else {
            Text(
                "--",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                color = cockpit.primaryText
            )
        }
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
private fun HeroMetric(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String? = null,
    modifier: Modifier = Modifier
) {
    val cockpit = LocalCockpitColors.current
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = cockpit.secondaryText,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.size(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = cockpit.secondaryText)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = cockpit.primaryText
            )
            if (unit != null) {
                Spacer(Modifier.size(3.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = cockpit.primaryText.copy(alpha = 0.86f)
                )
            }
        }
    }
}

@Composable
private fun HeroRecentTripMetric(trip: TripSessionEntity?, modifier: Modifier = Modifier) {
    val cockpit = LocalCockpitColors.current
    val distance = trip
        ?.distanceMeters
        ?.takeIf { it.isFinite() && it > 0.0 }
    val consumption = trip
        ?.averageConsumptionKwhPer100Km
        ?.takeIf { it.isFinite() && it >= 0.0 }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Route,
                contentDescription = null,
                tint = cockpit.secondaryText,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.size(4.dp))
            Text("最近行程", style = MaterialTheme.typography.labelMedium, color = cockpit.secondaryText)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                distance?.let(::formatTripDistance) ?: "--",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = cockpit.primaryText
            )
            if (distance != null) {
                Spacer(Modifier.size(3.dp))
                Text(
                    if (distance >= 1000.0) "km" else "m",
                    style = MaterialTheme.typography.labelMedium,
                    color = cockpit.primaryText.copy(alpha = 0.86f)
                )
            }
        }
        if (consumption != null) {
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    String.format(Locale.US, "%.1f", consumption),
                    style = MaterialTheme.typography.labelMedium,
                    color = EVDesignTokens.Energy.green
                )
                Spacer(Modifier.size(3.dp))
                Text(
                    "kWh/100km",
                    style = MaterialTheme.typography.labelSmall,
                    color = cockpit.secondaryText
                )
            }
        }
    }
}

@Composable
private fun MetricDivider() {
    Box(
        Modifier
            .size(width = 1.dp, height = 56.dp)
            .background(LocalCockpitColors.current.secondaryText.copy(alpha = .24f))
    )
}

private fun formatMileage(value: Double): String =
    if (value % 1.0 == 0.0) {
        String.format(Locale.US, "%,.0f", value)
    } else {
        String.format(Locale.US, "%,.1f", value)
    }

private fun formatTripDistance(meters: Double): String =
    if (meters >= 1000.0) {
        String.format(Locale.US, "%.1f", meters / 1000.0)
    } else {
        String.format(Locale.US, "%.0f", meters)
    }
