package com.evchargebook.ui.vehicle

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.entity.VehicleCatalogEntity
import com.evchargebook.ui.theme.LocalAppThemeController
import kotlinx.coroutines.flow.flowOf

@Composable
fun ManagedBrandLogo(
    catalogVehicleId: String?,
    modifier: Modifier = Modifier,
    darkSurface: Boolean? = null,
) {
    val context = LocalContext.current
    val catalogId = catalogVehicleId?.trim()?.takeIf { it.isNotEmpty() }
    val catalog by remember(catalogId, context.applicationContext) {
        catalogId?.let {
            AppDatabase.getInstance(context.applicationContext)
                .vehicleCatalogDao()
                .observeByCatalogId(it)
        } ?: flowOf(null)
    }.collectAsState(initial = null)

    ManagedBrandLogo(
        catalog = catalog,
        modifier = modifier,
        darkSurface = darkSurface,
    )
}

@Composable
fun ManagedBrandLogo(
    catalog: VehicleCatalogEntity?,
    modifier: Modifier = Modifier,
    darkSurface: Boolean? = null,
) {
    val context = LocalContext.current
    val appDark = LocalAppThemeController.current.darkTheme
    val useDarkSurface = darkSurface ?: appDark
    val primaryUrl = if (useDarkSurface) catalog?.brandLogoDarkUrl else catalog?.brandLogoLightUrl
    val primaryVersion = if (useDarkSurface) catalog?.brandLogoDarkVersion ?: 0 else catalog?.brandLogoLightVersion ?: 0
    val fallbackUrl = if (useDarkSurface) catalog?.brandLogoLightUrl else catalog?.brandLogoDarkUrl
    val fallbackVersion = if (useDarkSurface) catalog?.brandLogoLightVersion ?: 0 else catalog?.brandLogoDarkVersion ?: 0
    val url = primaryUrl ?: fallbackUrl
    val version = if (primaryUrl != null) primaryVersion else fallbackVersion
    val variant = when {
        primaryUrl != null && useDarkSurface -> "dark"
        primaryUrl != null -> "light"
        fallbackUrl != null && useDarkSurface -> "light-fallback"
        fallbackUrl != null -> "dark-fallback"
        else -> "none"
    }
    val brandCacheId = catalog?.brandId?.takeIf { it.isNotBlank() }
        ?: catalog?.brand?.takeIf { it.isNotBlank() }
        ?: "unknown"

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Default.DirectionsCar,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxSize(),
        )

        if (url != null) {
            val cacheKey = "brand-logo:$brandCacheId:$variant:v$version"
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = catalog?.brand?.let { "$it 品牌 Logo" },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
