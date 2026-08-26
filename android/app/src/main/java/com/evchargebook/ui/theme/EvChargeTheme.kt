package com.evchargebook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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

data class AppSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val touch: Dp = 48.dp
)

val LocalAppSpacing = staticCompositionLocalOf { AppSpacing() }
val MaterialTheme.spacing: AppSpacing
    @Composable get() = LocalAppSpacing.current

private val Ink = Color(0xFF171A1F)
private val MutedInk = Color(0xFF686E78)
private val Canvas = Color(0xFFF7F8FA)
private val Surface = Color(0xFFFFFFFF)
private val Line = Color(0xFFE4E7EC)
private val Energy = Color(0xFF176B52)
private val EnergySoft = Color(0xFFE5F3ED)

private val BrandLight = lightColorScheme(
    primary = Energy,
    onPrimary = Color.White,
    primaryContainer = EnergySoft,
    onPrimaryContainer = Color(0xFF0C3D2E),
    secondary = Color(0xFF4F5E58),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDF1EF),
    onSecondaryContainer = Color(0xFF29332F),
    background = Canvas,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0F2F5),
    onSurfaceVariant = MutedInk,
    outline = Line,
    outlineVariant = Color(0xFFEEF0F3),
    error = Color(0xFFB42318)
)

private val BrandDark = darkColorScheme(
    primary = Color(0xFF83D5B6),
    onPrimary = Color(0xFF073C2C),
    primaryContainer = Color(0xFF153F33),
    onPrimaryContainer = Color(0xFFC2F1DF),
    secondary = Color(0xFFBAC8C2),
    onSecondary = Color(0xFF25332E),
    secondaryContainer = Color(0xFF2A3430),
    onSecondaryContainer = Color(0xFFDDE7E2),
    background = Color(0xFF101214),
    onBackground = Color(0xFFF1F3F5),
    surface = Color(0xFF171A1D),
    onSurface = Color(0xFFF1F3F5),
    surfaceVariant = Color(0xFF22262A),
    onSurfaceVariant = Color(0xFFB9C0C8),
    outline = Color(0xFF353B42),
    outlineVariant = Color(0xFF252A2F),
    error = Color(0xFFFFB4AB)
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
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun EvChargeTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) BrandDark else BrandLight
    CompositionLocalProvider(LocalAppSpacing provides AppSpacing()) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
