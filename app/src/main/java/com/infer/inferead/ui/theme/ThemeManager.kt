package com.infer.inferead.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeBackground {
    System,
    ModernLight,
    ModernDark,
    HighContrastLight,
    HighContrastDark
}

enum class AppThemeAccent {
    Dynamic,
    OceanSky,
    VioletLavender,
    EmeraldMint,
    RosePeach,
    Custom
}

object ThemeManager {
    private val _currentBackground = MutableStateFlow(AppThemeBackground.System)
    val currentBackground: StateFlow<AppThemeBackground> = _currentBackground.asStateFlow()

    private val _currentAccent = MutableStateFlow(AppThemeAccent.Dynamic)
    val currentAccent: StateFlow<AppThemeAccent> = _currentAccent.asStateFlow()

    private val _customColor = MutableStateFlow(Color(0xFF6200EE))
    val customColor: StateFlow<Color> = _customColor.asStateFlow()

    private val _currentFontFamily = MutableStateFlow("Default")
    val currentFontFamily: StateFlow<String> = _currentFontFamily.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        val bgName = prefs.getString("app_theme_bg", AppThemeBackground.System.name) ?: AppThemeBackground.System.name
        _currentBackground.value = try { AppThemeBackground.valueOf(bgName) } catch (e: Exception) { AppThemeBackground.System }
        
        val accentName = prefs.getString("app_theme_accent", AppThemeAccent.Dynamic.name) ?: AppThemeAccent.Dynamic.name
        _currentAccent.value = try { AppThemeAccent.valueOf(accentName) } catch (e: Exception) { AppThemeAccent.Dynamic }
        
        val customColorInt = prefs.getInt("app_theme_custom_color", 0xFF6200EE.toInt())
        _customColor.value = Color(customColorInt)

        val fontFam = prefs.getString("app_theme_font", "Default") ?: "Default"
        _currentFontFamily.value = fontFam
    }

    fun setBackground(context: Context, bg: AppThemeBackground) {
        val prefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("app_theme_bg", bg.name).apply()
        _currentBackground.value = bg
    }

    fun setAccent(context: Context, accent: AppThemeAccent) {
        val prefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("app_theme_accent", accent.name).apply()
        _currentAccent.value = accent
    }

    fun setCustomColor(context: Context, color: Color) {
        val prefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        val colorInt = android.graphics.Color.argb(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        )
        prefs.edit().putInt("app_theme_custom_color", colorInt).apply()
        _customColor.value = color
    }

    fun setFontFamily(context: Context, fontFam: String) {
        val prefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("app_theme_font", fontFam).apply()
        _currentFontFamily.value = fontFam
    }
}
