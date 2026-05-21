package com.downtify.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF1DB954),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF0D5326),
    onPrimaryContainer = Color(0xFFA5F0BF),
    secondary = Color(0xFF1AA34A),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF0F6E32),
    onSecondaryContainer = Color(0xFFA5F0BF),
    tertiary = Color(0xFFBB86FC),
    background = Color(0xFF121212),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFB3B3B3),
    outline = Color(0xFF404040),
    error = Color(0xFFCF6679),
    onError = Color(0xFF000000)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1DB954),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFFA5F0BF),
    onPrimaryContainer = Color(0xFF00210B),
    secondary = Color(0xFF1AA34A),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFFA5F0BF),
    onSecondaryContainer = Color(0xFF00210B),
    tertiary = Color(0xFF6200EE),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFF5F5F5),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFF494949),
    outline = Color(0xFFCCCCCC),
    error = Color(0xFFB00020),
    onError = Color(0xFFFFFFFF)
)

@Composable
fun DowntifyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
