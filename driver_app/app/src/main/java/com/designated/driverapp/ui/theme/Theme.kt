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
// private val DarkColorScheme = darkColorScheme( // <-- 첫 번째 정의 주석 처리 (선택 사항)
//     primary = DeepYellow, 
//     secondary = PurpleGrey80,
//     tertiary = Pink80,
//     background = DarkTextColor, 
//     surface = DarkTextColor,
//     onPrimary = Color.Black,
//     onSecondary = Color.Black,
//     onTertiary = Color.Black,
//     onBackground = Color.White, 
//     onSurface = Color.White
// )

// --- darkColorScheme 추가 --- // <-- DriverAppTheme 에서 사용하는 부분
private val darkColorScheme = darkColorScheme(
    primary = OrangePrimary, // <- 여기를 OrangePrimary 로 변경
    secondary = DarkSecondary,
    background = DarkBackground,
    surface = DarkSurface,
    onError = DarkOnError,
    onPrimary = OnOrangePrimary, // <- 여기를 OnOrangePrimary 로 변경
    onSecondary = DarkOnSecondary,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
    // Add other colors as needed, inheriting from Material defaults if not specified
    tertiary = Pink80 // Example inheriting from light for simplicity, adjust as needed
)
// --- ---

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

        darkTheme -> darkColorScheme // darkTheme이 true일 때 darkColorScheme 사용
        else -> LightColorScheme      // 그 외에는 LightColorScheme 사용
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
        colorScheme = colorScheme, // 위에서 결정된 colorScheme 사용
        typography = Typography,
        content = content
    )
} 