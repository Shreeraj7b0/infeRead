package com.infer.inferead.ui.theme

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

fun getAppTypography(context: Context, fontName: String): Typography {
    val fontFamily = when(fontName) {
        "Default" -> FontFamily.Default
        "SansSerif" -> FontFamily.SansSerif
        "Serif" -> FontFamily.Serif
        "Monospace" -> FontFamily.Monospace
        "Google Sans" -> FontFamily(Font("fonts/google_sans.ttf", context.assets))
        "Literata" -> FontFamily(Font("fonts/literata.ttf", context.assets))
        "Amita" -> FontFamily(Font("fonts/amita.ttf", context.assets))
        "Hind" -> FontFamily(Font("fonts/hind.ttf", context.assets))
        "Yatra One" -> FontFamily(Font("fonts/yatra_one.ttf", context.assets))
        "Chelsea Market" -> FontFamily(Font("fonts/chelsea_market.ttf", context.assets))
        "Libre Baskerville" -> FontFamily(Font("fonts/libre_baskerville.ttf", context.assets))
        "Lora" -> FontFamily(Font("fonts/lora.ttf", context.assets))
        "Nunito" -> FontFamily(Font("fonts/nunito.ttf", context.assets))
        "Playfair Display" -> FontFamily(Font("fonts/playfair_display.ttf", context.assets))
        else -> FontFamily.Default
    }
    
    return Typography(
        displayLarge = Typography.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = Typography.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = Typography.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = Typography.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = Typography.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = Typography.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = Typography.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = Typography.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = Typography.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = Typography.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = Typography.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = Typography.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = Typography.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = Typography.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = Typography.labelSmall.copy(fontFamily = fontFamily)
    )
}
