package com.designated.driverapp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.* // Import Material 3 components
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Define Light Color Scheme using Material 3 builder
private val LightColorScheme = lightColorScheme(
    primary = DeepYellow, // Use custom yellow as primary
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color.White, // Light background
    surface = Color.White,
    onPrimary = Color.Black, // Text/icons on primary color
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = DarkTextColor, // Dark text on light background
    onSurface = DarkTextColor
    /* Other default colors to override */
)

// Define Dark Color Scheme using Material 3 builder (can be customized further)
private val DarkColorScheme = darkColorScheme(
    primary = DeepYellow, // Use custom yellow as primary
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = DarkTextColor, // Dark background
    surface = DarkTextColor,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color.White, // Light text on dark background
    onSurface = Color.White
)

@Composable
fun DriverAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disable dynamic color for now for consistency
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Ensure Typography is imported from the correct package (should be automatic if Type.kt is updated)
        content = content
    )
} 