package com.evchargebook.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class AppSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val touch: Dp = 48.dp
)

val LocalAppSpacing = staticCompositionLocalOf { AppSpacing() }
val MaterialTheme.spacing: AppSpacing
    @Composable get() = LocalAppSpacing.current

private val BrandLight = lightColorScheme(
    primary = Color(0xFF006C4C), onPrimary = Color.White,
    primaryContainer = Color(0xFFA8F5CD), onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF436650), onSecondary = Color.White,
    surface = Color(0xFFFAFBF7), surfaceVariant = Color(0xFFDDE5DD),
    onSurface = Color(0xFF191C1A), onSurfaceVariant = Color(0xFF404943)
)

private val BrandDark = darkColorScheme(
    primary = Color(0xFF8FDAAF), onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005236), onPrimaryContainer = Color(0xFFA8F5CD),
    secondary = Color(0xFFAACBB3), onSecondary = Color(0xFF143723),
    surface = Color(0xFF111512), surfaceVariant = Color(0xFF202720),
    onSurface = Color(0xFFE1E4DE), onSurfaceVariant = Color(0xFFC0C9C0)
)

@Composable
fun EvChargeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        isDark -> BrandDark
        else -> BrandLight
    }
    CompositionLocalProvider(LocalAppSpacing provides AppSpacing()) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
