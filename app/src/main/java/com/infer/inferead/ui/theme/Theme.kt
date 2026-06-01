package com.infer.inferead.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AppleDarkPrimary,
    background = AppleDarkBackground,
    surface = AppleDarkSurface,
    onBackground = AppleDarkText,
    onSurface = AppleDarkText
)

private val LightColorScheme = lightColorScheme(
    primary = AppleLightPrimary,
    background = AppleLightBackground,
    surface = AppleLightSurface,
    onBackground = AppleLightText,
    onSurface = AppleLightText
)

@Composable
fun InfeReadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val currentBg by ThemeManager.currentBackground.collectAsState()
    val currentAccent by ThemeManager.currentAccent.collectAsState()
    val customColor by ThemeManager.customColor.collectAsState()

    val context = LocalContext.current
    
    // 1. Determine base accent colors
    val (primaryLight, primaryDark) = when (currentAccent) {
        AppThemeAccent.OceanSky -> Pair(Color(0xFF0288D1), Color(0xFF4FC3F7))
        AppThemeAccent.VioletLavender -> Pair(Color(0xFF7B1FA2), Color(0xFFCE93D8))
        AppThemeAccent.EmeraldMint -> Pair(Color(0xFF388E3C), Color(0xFFA5D6A7))
        AppThemeAccent.RosePeach -> Pair(Color(0xFFD32F2F), Color(0xFFFFAB91))
        AppThemeAccent.Custom -> Pair(customColor, customColor)
        AppThemeAccent.Dynamic -> Pair(Color.Unspecified, Color.Unspecified)
    }

    // 2. Determine if it's currently rendering dark mode based on background selection
    val isEffectivelyDark = when (currentBg) {
        AppThemeBackground.ModernDark, AppThemeBackground.HighContrastDark -> true
        AppThemeBackground.ModernLight, AppThemeBackground.HighContrastLight -> false
        AppThemeBackground.System -> darkTheme
    }

    // 3. Build the ColorScheme
    var colorScheme = if (isEffectivelyDark) DarkColorScheme else LightColorScheme
    
    if (currentAccent == AppThemeAccent.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        colorScheme = if (isEffectivelyDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (currentAccent != AppThemeAccent.Dynamic) {
        // Copy the scheme but replace the primary colors
        colorScheme = colorScheme.copy(
            primary = if (isEffectivelyDark) primaryDark else primaryLight
        )
    }
    
    // 4. Apply specific background overrides
    colorScheme = when (currentBg) {
        AppThemeBackground.HighContrastDark -> colorScheme.copy(background = Color(0xFF000000), surface = Color(0xFF121212))
        AppThemeBackground.HighContrastLight -> colorScheme.copy(background = Color(0xFFFFFFFF), surface = Color(0xFFF5F5F5))
        AppThemeBackground.ModernDark -> colorScheme.copy(background = Color(0xFF1E1E1E), surface = Color(0xFF2C2C2E))
        AppThemeBackground.ModernLight -> colorScheme.copy(background = Color(0xFFF9F9F9), surface = Color(0xFFFFFFFF))
        AppThemeBackground.System -> colorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isEffectivelyDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
