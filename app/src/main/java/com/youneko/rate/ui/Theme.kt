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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.youneko.rate.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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

private val AppFont = FontFamily(
    Font(R.font.bevietnampro_regular, FontWeight.Normal),
    Font(R.font.bevietnampro_medium, FontWeight.Medium),
    Font(R.font.bevietnampro_semibold, FontWeight.SemiBold),
)

private val YounekoTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = AppFont, fontWeight = FontWeight.SemiBold),
        displayMedium = base.displayMedium.copy(fontFamily = AppFont, fontWeight = FontWeight.SemiBold),
        displaySmall = base.displaySmall.copy(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-0.2).sp),
        headlineLarge = base.headlineLarge.copy(fontFamily = AppFont, fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.2).sp),
        headlineSmall = base.headlineSmall.copy(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
        titleLarge = base.titleLarge.copy(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
        titleMedium = base.titleMedium.copy(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
        bodyLarge = base.bodyLarge.copy(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.15.sp),
        bodySmall = base.bodySmall.copy(fontFamily = AppFont, fontWeight = FontWeight.Normal),
        labelLarge = base.labelLarge.copy(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
        labelMedium = base.labelMedium.copy(fontFamily = AppFont, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    )
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun YounekoRateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    reducedMotion: Boolean = false,
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

    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(colorScheme = colors, typography = YounekoTypography, content = content)
    }
}
