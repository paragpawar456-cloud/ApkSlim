package com.parag.apkslim.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parag.apkslim.core.Category

/**
 * ApkSlim brand: one warm accent ("Squeeze orange") on clean white surfaces, ink text,
 * a delivery-green for success. No gradients on content surfaces.
 */
object Brand {
    val Orange = Color(0xFFFF5B2E)
    val OrangeDark = Color(0xFFE2441A)
    val OrangeSoft = Color(0xFFFFEEE8)
    val Ink = Color(0xFF1B1E28)
    val Green = Color(0xFF12A150)
    val GreenSoft = Color(0xFFE6F6EC)
    val Amber = Color(0xFFF5A623)
    val Mint = Green
    val Coral = Color(0xFFE5484D)
    val Violet = Color(0xFF6E56CF)
    val Blue = Color(0xFF2F6FEB)

    // Soft banner backgrounds (offer cards)
    val Peach = Color(0xFFFFF1EA)
    val Sage = Color(0xFFEAF6EF)
    val Lilac = Color(0xFFF1EEFB)
    val Sky = Color(0xFFEAF2FE)
}

/** Extra semantic colours that Material's scheme doesn't have. */
@Immutable
data class ExtraColors(
    val success: Color,
    val successSoft: Color,
    val accentSoft: Color,
    val muted: Color,
    val divider: Color,
    val card: Color,
    val bannerPeach: Color,
    val bannerSage: Color,
    val bannerLilac: Color,
    val bannerSky: Color,
)

private val LightExtra = ExtraColors(
    success = Brand.Green, successSoft = Brand.GreenSoft, accentSoft = Brand.OrangeSoft,
    muted = Color(0xFF6B7080), divider = Color(0xFFECEDF1), card = Color.White,
    bannerPeach = Brand.Peach, bannerSage = Brand.Sage, bannerLilac = Brand.Lilac, bannerSky = Brand.Sky,
)
private val DarkExtra = ExtraColors(
    success = Color(0xFF3DD68C), successSoft = Color(0xFF16291F), accentSoft = Color(0xFF3A2219),
    muted = Color(0xFF9A9FAE), divider = Color(0xFF2A2D35), card = Color(0xFF1A1C22),
    bannerPeach = Color(0xFF2E211C), bannerSage = Color(0xFF1B2A22), bannerLilac = Color(0xFF241F33), bannerSky = Color(0xFF1B2433),
)

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

object AppTheme {
    val extra: ExtraColors @Composable get() = LocalExtraColors.current
}

/** Category colours: calm, distinguishable, same hue family in both themes. */
fun Category.color(): Color = when (this) {
    Category.NATIVE -> Color(0xFF2F6FEB)
    Category.DEX -> Color(0xFFFF5B2E)
    Category.IMAGES -> Color(0xFFF5A623)
    Category.RESOURCES -> Color(0xFF6E56CF)
    Category.RESOURCE_TABLE -> Color(0xFF12A150)
    Category.ASSETS -> Color(0xFFE5484D)
    Category.SIGNATURE -> Color(0xFF8B8FA0)
    Category.OTHER -> Color(0xFFC3C6D1)
}

private val Light = lightColorScheme(
    primary = Brand.Orange,
    onPrimary = Color.White,
    primaryContainer = Brand.OrangeSoft,
    onPrimaryContainer = Color(0xFF5A1600),
    secondary = Brand.Ink,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F2F5),
    onSecondaryContainer = Brand.Ink,
    tertiary = Brand.Green,
    tertiaryContainer = Brand.GreenSoft,
    onTertiaryContainer = Color(0xFF00391A),
    background = Color.White,
    onBackground = Brand.Ink,
    surface = Color.White,
    onSurface = Brand.Ink,
    surfaceVariant = Color(0xFFF4F5F7),
    onSurfaceVariant = Color(0xFF6B7080),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF6F7F9),
    surfaceContainerHigh = Color(0xFFF1F2F5),
    surfaceContainerHighest = Color(0xFFEBECF0),
    outline = Color(0xFFB9BCC7),
    outlineVariant = Color(0xFFECEDF1),
    error = Color(0xFFD93025),
    errorContainer = Color(0xFFFDECEA),
    onErrorContainer = Color(0xFF5F0F0A),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF7A52),
    onPrimary = Color(0xFF2B0A00),
    primaryContainer = Color(0xFF3A2219),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFE6E7EC),
    onSecondary = Brand.Ink,
    secondaryContainer = Color(0xFF24262D),
    onSecondaryContainer = Color(0xFFE6E7EC),
    tertiary = Color(0xFF3DD68C),
    tertiaryContainer = Color(0xFF16291F),
    onTertiaryContainer = Color(0xFFB8F0CF),
    background = Color(0xFF111214),
    onBackground = Color(0xFFECEDF1),
    surface = Color(0xFF111214),
    onSurface = Color(0xFFECEDF1),
    surfaceVariant = Color(0xFF1E2026),
    onSurfaceVariant = Color(0xFF9A9FAE),
    surfaceContainerLowest = Color(0xFF0C0D0F),
    surfaceContainerLow = Color(0xFF1A1C22),
    surfaceContainer = Color(0xFF1A1C22),
    surfaceContainerHigh = Color(0xFF22242B),
    surfaceContainerHighest = Color(0xFF2A2D35),
    outline = Color(0xFF5A5E6B),
    outlineVariant = Color(0xFF2A2D35),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF3B1412),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val Sans = FontFamily.SansSerif

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
        headlineLarge = t.headlineLarge.copy(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
        headlineMedium = t.headlineMedium.copy(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.copy(fontFamily = Sans, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleLarge = t.titleLarge.copy(fontFamily = Sans, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleMedium = t.titleMedium.copy(fontFamily = Sans, fontWeight = FontWeight.Bold),
        titleSmall = t.titleSmall.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(fontFamily = Sans),
        bodyMedium = t.bodyMedium.copy(fontFamily = Sans),
        bodySmall = t.bodySmall.copy(fontFamily = Sans, lineHeight = 17.sp),
        labelLarge = t.labelLarge.copy(fontFamily = Sans, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
        labelMedium = t.labelMedium.copy(fontFamily = Sans, fontWeight = FontWeight.Medium),
        labelSmall = t.labelSmall.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
    )
}

val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun ApkSlimTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalExtraColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
