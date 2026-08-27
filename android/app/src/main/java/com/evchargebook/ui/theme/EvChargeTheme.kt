package com.evchargebook.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
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

val LocalAppSpacing = staticCompositionLocalOf { AppSpacing() }
val MaterialTheme.spacing: AppSpacing
    @Composable get() = LocalAppSpacing.current

private val LightBackground = Color(0xFFF5F7F6)
private val LightSurface = Color.White

private val BrandLight = lightColorScheme(
    primary = EVDesignTokens.Energy.green,
    onPrimary = Color(0xFF07140D),
    background = LightBackground,
    onBackground = Color(0xFF101512),
    surface = LightSurface,
    onSurface = Color(0xFF101512),
    surfaceVariant = Color(0xFFE9EFEC),
    onSurfaceVariant = Color(0xFF66736D),
    outline = Color(0xFFD8E1DC)
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
    outline = EVDesignTokens.Dark.outline,
    outlineVariant = Color(0xFF1B2521),
    inverseSurface = Color(0xFFEAF3EE),
    inverseOnSurface = Color(0xFF0A0E0C),
    inversePrimary = Color(0xFF168C4A)
)

private val AppTypography = Typography(
    headlineMedium = Typography().headlineMedium.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = Typography().headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = Typography().titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = Typography().titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = Typography().bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = Typography().bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = Typography().labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(EVDesignTokens.Radius.small.dp),
    medium = RoundedCornerShape(EVDesignTokens.Radius.medium.dp),
    large = RoundedCornerShape(EVDesignTokens.Radius.large.dp)
)

/**
 * EV Charge Book v0.5 theme.
 *
 * Dark First is the product default. Light mode remains available to callers by
 * explicitly passing darkTheme = false; a persisted in-app theme switch can be
 * wired later without changing screen code.
 */
@Composable
fun EvChargeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) BrandDark else BrandLight
    CompositionLocalProvider(
        LocalAppSpacing provides AppSpacing()
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
