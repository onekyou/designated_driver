package com.designated.callmanager.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Define the dark color scheme using the custom colors
private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    secondary = DarkSecondary,
    tertiary = DeepYellow, // Changed from Pink80 to DeepYellow
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.Black, // Text/icons on primary color background
    onSecondary = Color.Black, // Text/icons on secondary color background
    onTertiary = Color.Black,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
)

@Composable
fun CallManagerTheme(
    darkTheme: Boolean = true, // Force dark theme
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme // Always use the dark scheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb() // Match status bar with background

            // Apply API level check for setDecorFitsSystemWindows implicitly called by getInsetsController
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11 (API 30) check
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            } else {
                // Fallback for older APIs (optional, might have limitations)
                @Suppress("DEPRECATION")
                if (!darkTheme) {
                    var flags = window.decorView.systemUiVisibility
                    flags = flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    window.decorView.systemUiVisibility = flags
                } else {
                    var flags = window.decorView.systemUiVisibility
                    flags = flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                    window.decorView.systemUiVisibility = flags
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
} 