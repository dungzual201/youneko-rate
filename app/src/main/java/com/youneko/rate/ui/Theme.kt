package com.youneko.rate.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.youneko.rate.R
import androidx.compose.ui.text.font.FontWeight

private val BrandLightColors = lightColorScheme(
    primary = Color(0xFF7456B8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBDDFF),
    onPrimaryContainer = Color(0xFF28134F),
    secondary = Color(0xFF9A476F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E7),
    onSecondaryContainer = Color(0xFF3F1027),
    tertiary = Color(0xFF7A5900),
    tertiaryContainer = Color(0xFFFFDEA0),
    background = Color(0xFFFFF9FC),
    surface = Color(0xFFFFF9FC),
    surfaceVariant = Color(0xFFF0E4F0),
    outline = Color(0xFF7D747E),
)

private val BrandDarkColors = darkColorScheme(
    primary = Color(0xFFD7BFFF),
    onPrimary = Color(0xFF3C1D70),
    primaryContainer = Color(0xFF563B91),
    onPrimaryContainer = Color(0xFFEBDDFF),
    secondary = Color(0xFFFFB0D0),
    onSecondary = Color(0xFF5C1E3B),
    secondaryContainer = Color(0xFF7B3154),
    onSecondaryContainer = Color(0xFFFFD9E7),
    tertiary = Color(0xFFF5C24F),
    tertiaryContainer = Color(0xFF5D4300),
    background = Color(0xFF151118),
    surface = Color(0xFF151118),
    surfaceVariant = Color(0xFF4D4350),
    outline = Color(0xFF968D98),
)

private val DisplayFont = FontFamily(Font(R.font.youneko_display, FontWeight.Normal), Font(R.font.youneko_display, FontWeight.Bold))
private val BodyFont = FontFamily(Font(R.font.youneko_body, FontWeight.Normal), Font(R.font.youneko_body, FontWeight.Medium), Font(R.font.youneko_body, FontWeight.Bold))

private val YounekoTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = DisplayFont, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontFamily = DisplayFont, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = DisplayFont, fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontFamily = DisplayFont, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = DisplayFont, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = DisplayFont, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = DisplayFont, fontWeight = FontWeight.Bold),
        bodyLarge = base.bodyLarge.copy(fontFamily = BodyFont),
        bodyMedium = base.bodyMedium.copy(fontFamily = BodyFont),
        bodySmall = base.bodySmall.copy(fontFamily = BodyFont),
        labelLarge = base.labelLarge.copy(fontFamily = BodyFont),
        labelMedium = base.labelMedium.copy(fontFamily = BodyFont),
        labelSmall = base.labelSmall.copy(fontFamily = BodyFont),
    )
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun YounekoRateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val resolvedDark = when (themeMode) {
        ThemeMode.SYSTEM -> darkTheme
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && resolvedDark -> dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        resolvedDark -> BrandDarkColors
        else -> BrandLightColors
    }

    MaterialTheme(colorScheme = colors, typography = YounekoTypography, content = content)
}
