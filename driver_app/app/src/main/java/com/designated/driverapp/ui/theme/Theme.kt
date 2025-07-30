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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

// Define Light Color Scheme using design system colors
private val LightColorScheme = lightColorScheme(
    primary = Primary,
    secondary = Secondary,
    tertiary = PrimaryLight,
    background = Color.White,
    surface = Color(0xFFF5F5F5),
    onPrimary = OnPrimary,
    onSecondary = OnSecondary,
    onTertiary = OnPrimary,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    error = Error,
    onError = OnError
)

// Define Dark Color Scheme using design system colors
private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    secondary = Secondary,
    tertiary = PrimaryLight,
    background = Background,
    surface = Surface,
    onPrimary = OnPrimary,
    onSecondary = OnSecondary,
    onTertiary = OnPrimary,
    onBackground = OnBackground,
    onSurface = OnSurface,
    error = Error,
    onError = OnError,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurface
)

@Composable
fun DriverAppTheme(
    darkTheme: Boolean = true, // 기본값을 true로 변경
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // 다이나믹 컬러는 비활성화 유지
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        //     val context = LocalContext.current
        //     if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        // } // 다이나믹 컬러 로직 주석 처리 또는 제거

        darkTheme -> DarkColorScheme // darkTheme이 true일 때 DarkColorScheme 사용
        else -> LightColorScheme     // 그 외에는 LightColorScheme 사용
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme, // 위에서 결정된 colorScheme 사용
        typography = Typography,
        content = content
    )
} 