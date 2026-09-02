package com.evchargebook.ui.theme

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evchargebook.update.AppUpdatePrompt

data class AppSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val touch: Dp = 48.dp
)

data class AppThemeController(
    val darkTheme: Boolean,
    val setDarkTheme: (Boolean) -> Unit
)

val LocalAppSpacing = staticCompositionLocalOf { AppSpacing() }
val LocalAppThemeController = staticCompositionLocalOf { AppThemeController(true) {} }
val MaterialTheme.spacing: AppSpacing
    @Composable get() = LocalAppSpacing.current

private val BrandLight = lightColorScheme(
    primary = Color(0xFF087A3B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F8E2),
    onPrimaryContainer = Color(0xFF082414),
    background = EVDesignTokens.Light.background,
    onBackground = EVDesignTokens.Light.primaryText,
    surface = EVDesignTokens.Light.surface,
    onSurface = EVDesignTokens.Light.primaryText,
    surfaceVariant = EVDesignTokens.Light.surfaceLow,
    onSurfaceVariant = EVDesignTokens.Light.secondaryText,
    surfaceContainerLowest = EVDesignTokens.Light.surface,
    surfaceContainerLow = EVDesignTokens.Light.background,
    surfaceContainer = EVDesignTokens.Light.surfaceLow,
    surfaceContainerHigh = EVDesignTokens.Light.surfaceHigh,
    surfaceContainerHighest = EVDesignTokens.Light.surfaceHighest,
    outline = EVDesignTokens.Light.outline,
    outlineVariant = EVDesignTokens.Light.outlineVariant,
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val BrandDark = darkColorScheme(
    primary = EVDesignTokens.Energy.green,
    onPrimary = Color(0xFF041007),
    primaryContainer = Color(0xFF123321),
    onPrimaryContainer = EVDesignTokens.Dark.primaryText,
    background = EVDesignTokens.Dark.background,
    onBackground = EVDesignTokens.Dark.primaryText,
    surface = EVDesignTokens.Dark.surface,
    onSurface = EVDesignTokens.Dark.primaryText,
    surfaceVariant = EVDesignTokens.Dark.surfaceElevated,
    onSurfaceVariant = EVDesignTokens.Dark.secondaryText,
    surfaceContainerLowest = EVDesignTokens.Dark.surfaceLowest,
    surfaceContainerLow = EVDesignTokens.Dark.surfaceLow,
    surfaceContainer = EVDesignTokens.Dark.surface,
    surfaceContainerHigh = EVDesignTokens.Dark.surfaceElevated,
    surfaceContainerHighest = EVDesignTokens.Dark.surfaceHighest,
    outline = EVDesignTokens.Dark.outline,
    outlineVariant = EVDesignTokens.Dark.outlineVariant,
    error = EVDesignTokens.Energy.danger,
    onError = Color(0xFF220001),
    inverseSurface = Color(0xFFEAF3EE),
    inverseOnSurface = Color(0xFF0A0E0C),
    inversePrimary = Color(0xFF168C4A)
)

private val AppTypography = Typography(
    displayMedium = Typography().displayMedium.copy(fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.SemiBold),
    displaySmall = Typography().displaySmall.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = Typography().headlineMedium.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = Typography().headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = Typography().titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = Typography().titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = Typography().bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = Typography().bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = Typography().bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = Typography().labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelMedium = Typography().labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = Typography().labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Medium)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(EVDesignTokens.Radius.small.dp),
    medium = RoundedCornerShape(EVDesignTokens.Radius.medium.dp),
    large = RoundedCornerShape(EVDesignTokens.Radius.large.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun EvChargeTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("ev_charge_ui", Context.MODE_PRIVATE)
    }
    var storedDarkTheme by remember {
        mutableStateOf(preferences.getBoolean("dark_theme", true))
    }
    val resolvedDarkTheme = darkTheme ?: storedDarkTheme
    val controller = AppThemeController(resolvedDarkTheme) { enabled ->
        storedDarkTheme = enabled
        preferences.edit().putBoolean("dark_theme", enabled).apply()
    }
    val colors = if (resolvedDarkTheme) BrandDark else BrandLight

    CompositionLocalProvider(
        LocalAppSpacing provides AppSpacing(),
        LocalAppThemeController provides controller,
        LocalCockpitColors provides cockpitColorsFor(colors)
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            shapes = AppShapes
        ) {
            content()
            AppUpdatePrompt()
        }
    }
}
