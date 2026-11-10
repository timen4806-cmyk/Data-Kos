package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val WargaKosDarkColorScheme = darkColorScheme(
    primary = DarkTealPrimary,
    onPrimary = DarkOnTealPrimary,
    primaryContainer = DarkTealContainer,
    onPrimaryContainer = DarkOnTealContainer,
    secondary = DarkAmberSecondary,
    onSecondary = DarkOnAmberSecondary,
    secondaryContainer = DarkAmberContainer,
    onSecondaryContainer = DarkOnAmberContainer,
    tertiary = DarkEmeraldTertiary,
    onTertiary = DarkOnEmeraldTertiary,
    tertiaryContainer = DarkEmeraldContainer,
    onTertiaryContainer = DarkOnEmeraldContainer,
    background = ObsidianBackground,
    onBackground = OnObsidianBackground,
    surface = ObsidianSurface,
    onSurface = OnObsidianBackground,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = OnObsidianSurfaceVariant,
    outline = ObsidianOutline,
)

private val WargaKosLightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = OnTealPrimary,
    primaryContainer = TealContainer,
    onPrimaryContainer = OnTealContainer,
    secondary = AmberSecondary,
    onSecondary = OnAmberSecondary,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = OnAmberContainer,
    tertiary = EmeraldTertiary,
    onTertiary = OnEmeraldTertiary,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = OnEmeraldContainer,
    background = SandBackground,
    onBackground = OnSandBackground,
    surface = WarmSurface,
    onSurface = OnSandBackground,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = OnWarmSurfaceVariant,
    outline = WarmOutline,
)

val WargaKosShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun WargaKosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) WargaKosDarkColorScheme else WargaKosLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = WargaKosTypography,
        shapes = WargaKosShapes,
        content = content,
    )
}
