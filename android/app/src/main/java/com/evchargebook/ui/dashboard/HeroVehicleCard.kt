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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
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
    artworkKey: String? = null,
    edgeToEdgeTop: Boolean = false
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
    val topSystemInset = if (edgeToEdgeTop) {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    } else {
        0.dp
    }

    val selectableVehicles = if (vehicles.isNotEmpty()) vehicles else listOfNotNull(vehicle)
    val canSwitchVehicle = vehicleSwitchEnabled && selectableVehicles.size > 1
    var vehicleMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Color(0xFF06100C)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // The artwork stage stays matched to the 1600x1100 production assets.
                        .aspectRatio(1.46f)
                ) {
                    VehicleStage(vehicle, effectiveArtworkKey, Modifier.fillMaxSize())
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0x98020806),
                                        Color(0x20020806),
                                        Color.Transparent,
                                        Color(0x10020806),
                                        Color(0x4806100C)
                                    )
                                )
                            )
                    )

                    Text(
                        text = vehicle?.let { "${it.brand}  ${it.model}" } ?: "EV Charge Book",
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = 16.dp,
                                top = topSystemInset + 14.dp,
                                end = 72.dp
                            ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        color = cockpit.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (vehicle != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = topSystemInset + 10.dp, end = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x6607110F))
                                    .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                                    .clickable(enabled = canSwitchVehicle) {
                                        vehicleMenuExpanded = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsCar,
                                    contentDescription = if (vehicleSwitchEnabled) "切换车辆" else "行程进行中不可切换车辆",
                                    tint = Color.White.copy(alpha = if (vehicleSwitchEnabled) 0.94f else 0.45f),
                                    modifier = Modifier.size(22.dp)
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

                // Reserve real layout space below the 1600x1100 artwork stage. The panel is then
                // bottom-aligned across this total height, overlapping only 28dp of the artwork.
                if (vehicle != null) {
                    Spacer(modifier = Modifier.height(52.dp))
                }
            }

            if (vehicle != null) {
                HeroDynamicStatePanel(
                    currentSoc = currentSoc,
                    currentMileageKm = currentMileageKm,
                    latestTrip = latestTrip,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp)
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
    val recentDistance = latestTrip
        ?.distanceMeters
        ?.takeIf { it.isFinite() && it > 0.0 }
    val panelShape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .shadow(
                elevation = 6.dp,
                shape = panelShape,
                ambientColor = Color.Black.copy(alpha = 0.20f),
                spotColor = Color.Black.copy(alpha = 0.26f)
            )
            .clip(panelShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.045f),
                        Color(0x9013211C),
                        Color(0xB0091411)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.15f),
                        Color.White.copy(alpha = 0.05f),
                        EVDesignTokens.Energy.green.copy(alpha = 0.07f)
                    )
                ),
                shape = panelShape
            )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0x181D6D49),
                            Color.Transparent,
                            Color(0x0C174634)
                        )
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroSocMetric(
                safeSoc = safeSoc,
                animatedProgress = animatedProgress,
                modifier = Modifier.weight(0.82f)
            )
            MetricDivider()
            HeroMetric(
                icon = Icons.Default.Speed,
                label = "当前里程",
                value = currentMileageKm?.let(::formatMileage) ?: "--",
                unit = if (currentMileageKm != null) "km" else null,
                modifier = Modifier
                    .weight(1.16f)
                    .padding(horizontal = 10.dp)
            )
            MetricDivider()
            HeroMetric(
                icon = Icons.Default.Route,
                label = "最近行程",
                value = recentDistance?.let(::formatTripDistance) ?: "--",
                unit = recentDistance?.let { if (it >= 1000.0) "km" else "m" },
                modifier = Modifier
                    .weight(0.96f)
                    .padding(start = 10.dp)
            )
        }
    }
}

@Composable
private fun HeroSocMetric(safeSoc: Int?, animatedProgress: Float, modifier: Modifier = Modifier) {
    val cockpit = LocalCockpitColors.current
    Column(modifier = modifier.padding(end = 10.dp)) {
        if (safeSoc != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    safeSoc.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = EVDesignTokens.Energy.green
                )
                Text(
                    "%",
                    modifier = Modifier.padding(bottom = 1.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = cockpit.primaryText
                )
            }
        } else {
            Text(
                "--",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = cockpit.primaryText
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(cockpit.secondaryText.copy(alpha = 0.22f))
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
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = cockpit.secondaryText,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = cockpit.primaryText,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
            if (unit != null) {
                Spacer(Modifier.size(3.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = cockpit.primaryText.copy(alpha = 0.86f),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun MetricDivider() {
    Box(
        Modifier
            .size(width = 1.dp, height = 44.dp)
            .background(LocalCockpitColors.current.secondaryText.copy(alpha = .20f))
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
