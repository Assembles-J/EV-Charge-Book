package com.evchargebook.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.ui.theme.EVDesignTokens
import com.evchargebook.ui.theme.LocalAppThemeController
import com.evchargebook.ui.vehicle.HeroArtworkManifestRepository
import com.evchargebook.ui.vehicle.OfficialVehicleImageCatalog
import kotlinx.coroutines.flow.flowOf

/** Active Trip reuses the same managed HERO artwork source as Dashboard, without Dashboard telemetry. */
@Composable
internal fun TripActiveVehicleHeroV08(
    vehicle: VehicleEntity?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appTheme = LocalAppThemeController.current
    val media = EVDesignTokens.Media.forTheme(appTheme.darkTheme)
    val catalogId = vehicle?.catalogVehicleId?.trim()?.takeIf { it.isNotEmpty() }
    val artworkKey by remember(catalogId, context.applicationContext) {
        catalogId?.let {
            AppDatabase.getInstance(context.applicationContext)
                .vehicleCatalogDao()
                .observeHeroArtworkKey(it)
        } ?: flowOf(null)
    }.collectAsState(initial = null)
    val artwork = remember(vehicle, artworkKey) {
        OfficialVehicleImageCatalog.resolve(vehicle, artworkKey)
    }
    var remoteArtwork by remember(artwork?.key, appTheme.darkTheme) {
        mutableStateOf<HeroArtworkManifestRepository.RemoteArtwork?>(null)
    }

    LaunchedEffect(artwork?.key, appTheme.darkTheme) {
        remoteArtwork = artwork?.let {
            HeroArtworkManifestRepository.resolve(
                context = context,
                artworkKey = it.key,
                preferLight = !appTheme.darkTheme,
            )
        }
    }

    val imageUrl = remoteArtwork?.url ?: artwork?.remoteFallbackUrl
    val cacheVersion = remoteArtwork?.version ?: 0

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(media.stageTop, media.stageMiddle, media.stageBottom)
                    )
                )
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsCar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = .32f),
                modifier = Modifier.align(Alignment.Center).fillMaxSize(.32f),
            )
            if (imageUrl != null && artwork != null) {
                val cacheKey = "trip-active-hero:${artwork.key}:v$cacheVersion"
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
                    alignment = Alignment.Center,
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(media.scrimStrong, media.scrimSoft, androidx.compose.ui.graphics.Color.Transparent, media.scrimBottom)
                        )
                    )
            )
            Text(
                text = vehicle?.displayName ?: "EV Charge Book",
                modifier = Modifier.align(Alignment.TopStart).padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = media.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
