package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrithiPrimaryLavender,
    secondary = PrithiSecondaryFuchsia,
    tertiary = PrithiPinkAccent,
    background = SpaceBackground,
    surface = SpaceCardSurface,
    onPrimary = SpaceBackground,
    onSecondary = SpaceBackground,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = SpaceCardOverlay,
    onSurfaceVariant = Color(0xFFE0C3FC)
)

private val LightColorScheme = lightColorScheme(
    primary = LavenderDeep,
    secondary = LavenderMedium,
    tertiary = PrithiPinkAccent,
    background = LavenderLight,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force cosmic dark by default for immersive AI experience
    dynamicColor: Boolean = false, // Use our handcrafted palette for stronger visual brand
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
