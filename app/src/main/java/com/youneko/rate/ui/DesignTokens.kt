package com.youneko.rate.ui

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object YounekoSpacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
}

object YounekoRadius {
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val pill: Dp = 28.dp
}

object YounekoElevation {
    val card: Dp = 2.dp
    val prominent: Dp = 6.dp
}

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val view = LocalView.current
    return remember(context, view) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) <= 0f
        }.getOrDefault(false)
    }
}

fun <T> younekoSpring(reducedMotion: Boolean): AnimationSpec<T> =
    if (reducedMotion) snap() else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

fun younekoFade(reducedMotion: Boolean): FiniteAnimationSpec<Float> =
    if (reducedMotion) snap() else tween(durationMillis = 200)

fun younekoStaggerDelay(index: Int, reducedMotion: Boolean): Int =
    if (reducedMotion || index >= 8) 0 else index * 30
