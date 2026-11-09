package com.parag.apkslim.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parag.apkslim.core.Category

object Brand {
    val Indigo = Color(0xFF2E5BFF)
    val Violet = Color(0xFF7B2FF7)
    val Mint = Color(0xFF12B886)
    val Amber = Color(0xFFF59F00)
    val Coral = Color(0xFFFF6B6B)
    val hero = Brush.linearGradient(listOf(Indigo, Violet))
    val success = Brush.linearGradient(listOf(Color(0xFF0CA678), Color(0xFF2E5BFF)))
}

/** Fixed, colour-blind-friendly-ish palette for size categories (same in light & dark). */
fun Category.color(): Color = when (this) {
    Category.NATIVE -> Color(0xFF4C6EF5)
    Category.DEX -> Color(0xFF12B886)
    Category.IMAGES -> Color(0xFFF59F00)
    Category.RESOURCES -> Color(0xFFBE4BDB)
    Category.RESOURCE_TABLE -> Color(0xFF15AABF)
    Category.ASSETS -> Color(0xFFFF6B6B)
    Category.SIGNATURE -> Color(0xFF868E96)
    Category.OTHER -> Color(0xFFADB5BD)
}

private val Light = lightColorScheme(
    primary = Color(0xFF2E5BFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE3FF),
    onPrimaryContainer = Color(0xFF001257),
    secondary = Color(0xFF6A3FD8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEADDFF),
    onSecondaryContainer = Color(0xFF22005D),
    tertiary = Color(0xFF0CA678),
    tertiaryContainer = Color(0xFFC3FAE8),
    onTertiaryContainer = Color(0xFF00382A),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF14161F),
    surface = Color(0xFFF6F7FB),
    onSurface = Color(0xFF14161F),
    surfaceVariant = Color(0xFFE3E6F0),
    onSurfaceVariant = Color(0xFF454A5C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F2F8),
    surfaceContainerHigh = Color(0xFFE9ECF5),
    surfaceContainerHighest = Color(0xFFE3E6F0),
    outline = Color(0xFF767B8E),
    outlineVariant = Color(0xFFD3D7E3),
    error = Color(0xFFD63B3B),
    errorContainer = Color(0xFFFFE3E3),
    onErrorContainer = Color(0xFF5C0A0A),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB7C4FF),
    onPrimary = Color(0xFF002388),
    primaryContainer = Color(0xFF2443C8),
    onPrimaryContainer = Color(0xFFDDE3FF),
    secondary = Color(0xFFD0BCFF),
    onSecondary = Color(0xFF3B1A8C),
    secondaryContainer = Color(0xFF5231B8),
    onSecondaryContainer = Color(0xFFEADDFF),
    tertiary = Color(0xFF63E6BE),
    tertiaryContainer = Color(0xFF00513D),
    onTertiaryContainer = Color(0xFFC3FAE8),
    background = Color(0xFF0E1018),
    onBackground = Color(0xFFE4E6F0),
    surface = Color(0xFF0E1018),
    onSurface = Color(0xFFE4E6F0),
    surfaceVariant = Color(0xFF2A2E3D),
    onSurfaceVariant = Color(0xFFC3C7D6),
    surfaceContainerLowest = Color(0xFF0A0C12),
    surfaceContainerLow = Color(0xFF161925),
    surfaceContainer = Color(0xFF1B1E2B),
    surfaceContainerHigh = Color(0xFF232736),
    surfaceContainerHighest = Color(0xFF2C3041),
    outline = Color(0xFF8C91A3),
    outlineVariant = Color(0xFF3A3F50),
    error = Color(0xFFFF8787),
    errorContainer = Color(0xFF5C1414),
    onErrorContainer = Color(0xFFFFE3E3),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

@Composable
fun ApkSlimTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
