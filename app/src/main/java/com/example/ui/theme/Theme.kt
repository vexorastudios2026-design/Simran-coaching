package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SimranLightColorScheme = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = IndigoSoftContainer,
    onPrimaryContainer = IndigoOnContainer,
    secondary = VioletSecondary,
    onSecondary = Color.White,
    secondaryContainer = VioletSoftContainer,
    onSecondaryContainer = VioletOnContainer,
    tertiary = SkyTertiary,
    onTertiary = Color.White,
    tertiaryContainer = SkySoftContainer,
    onTertiaryContainer = SkyOnContainer,
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceTinted,
    onSurfaceVariant = TextSecondary,
    outline = OutlineLight,
    outlineVariant = OutlineLight,
    error = PendingRose,
    onError = Color.White,
    errorContainer = PendingRoseBg,
    onErrorContainer = PendingRose,
)

private val SimranShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SimranTuitionTheme(
    content: @Composable () -> Unit,
) {
    // Strictly light theme by design for clean, approachable teacher experience
    MaterialTheme(
        colorScheme = SimranLightColorScheme,
        typography = Typography,
        shapes = SimranShapes,
        content = content,
    )
}
