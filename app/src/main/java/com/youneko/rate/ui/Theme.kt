package com.youneko.rate.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.youneko.rate.R

val BrandPurple = Color(0xFF6C4AC7)
val BrandPink = Color(0xFFB23A6A)
val BrandCream = Color(0xFFFCF8FF)

internal val YounekoLightColorScheme = lightColorScheme(
    primary = BrandPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF24005A),
    secondary = BrandPink,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E4),
    onSecondaryContainer = Color(0xFF3E0021),
    tertiary = Color(0xFF8A6100),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDF9E),
    onTertiaryContainer = Color(0xFF2B1F00),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = BrandCream,
    onBackground = Color(0xFF1C1B20),
    surface = BrandCream,
    onSurface = Color(0xFF1C1B20),
    surfaceVariant = Color(0xFFE8E0EC),
    onSurfaceVariant = Color(0xFF49454E),
    outline = Color(0xFF7A757F),
    outlineVariant = Color(0xFFCBC4CF),
    scrim = Color.Black,
    inverseSurface = Color(0xFF313036),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = Color(0xFFD3BBFF),
    surfaceDim = Color(0xFFDCD8DF),
    surfaceBright = Color(0xFFFCF8FF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF1EBF4),
    surfaceContainerHigh = Color(0xFFEBE5EE),
    surfaceContainerHighest = Color(0xFFE5DFE9),
)

internal val YounekoDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD3BBFF),
    onPrimary = Color(0xFF3A1F70),
    primaryContainer = Color(0xFF52389C),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFFFB0C9),
    onSecondary = Color(0xFF5B1737),
    secondaryContainer = Color(0xFF7A2E4E),
    onSecondaryContainer = Color(0xFFFFD9E4),
    tertiary = Color(0xFFF2C14E),
    onTertiary = Color(0xFF3A2A00),
    tertiaryContainer = Color(0xFF574400),
    onTertiaryContainer = Color(0xFFFFDF9E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE7E0E7),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE7E0E7),
    surfaceVariant = Color(0xFF49454E),
    onSurfaceVariant = Color(0xFFCAC4CF),
    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF49454E),
    scrim = Color.Black,
    inverseSurface = Color(0xFFE7E0E7),
    inverseOnSurface = Color(0xFF313036),
    inversePrimary = BrandPurple,
    surfaceDim = Color(0xFF141218),
    surfaceBright = Color(0xFF3A3840),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1C1A21),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2931),
    surfaceContainerHighest = Color(0xFF36343C),
)

internal val BrandFontFamily = FontFamily(
    Font(R.font.bevietnampro_regular, FontWeight.Normal),
    Font(R.font.bevietnampro_medium, FontWeight.Medium),
    Font(R.font.bevietnampro_semibold, FontWeight.SemiBold),
)

internal val YounekoTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = BrandFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 52.sp, lineHeight = 58.sp, letterSpacing = (-0.5).sp),
        displayMedium = base.displayMedium.copy(fontFamily = BrandFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 42.sp, lineHeight = 48.sp, letterSpacing = (-0.4).sp),
        displaySmall = base.displaySmall.copy(fontFamily = BrandFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.3).sp),
        headlineLarge = base.headlineLarge.copy(fontFamily = BrandFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.2).sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = BrandFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp),
        headlineSmall = base.headlineSmall.copy(fontFamily = BrandFontFamily, fontWeight = FontWeight.Medium, fontSize = 21.sp, lineHeight = 27.sp),
        titleLarge = base.titleLarge.copy(fontFamily = BrandFontFamily, fontWeight = FontWeight.Medium, fontSize = 19.sp, lineHeight = 25.sp),
        titleMedium = base.titleMedium.copy(fontFamily = BrandFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.05.sp),
        titleSmall = base.titleSmall.copy(fontFamily = BrandFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.05.sp),
        bodyLarge = base.bodyLarge.copy(fontFamily = BrandFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = BrandFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        bodySmall = base.bodySmall.copy(fontFamily = BrandFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.15.sp),
        labelLarge = base.labelLarge.copy(fontFamily = BrandFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.05.sp),
        labelMedium = base.labelMedium.copy(fontFamily = BrandFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.15.sp),
        labelSmall = base.labelSmall.copy(fontFamily = BrandFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.25.sp),
    )
}

private val YounekoShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

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
        resolvedDark -> YounekoDarkColorScheme
        else -> YounekoLightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = context as? Activity ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, view)
            activity.window.statusBarColor = Color.Transparent.toArgb()
            activity.window.navigationBarColor = Color.Transparent.toArgb()
            controller.isAppearanceLightStatusBars = !resolvedDark
            controller.isAppearanceLightNavigationBars = !resolvedDark
        }
    }

    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = colors,
            typography = YounekoTypography,
            shapes = YounekoShapes,
            content = content,
        )
    }
}
