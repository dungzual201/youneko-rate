package com.youneko.rate.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object YnMotion {
    const val fastMs = 150
    const val normalMs = 250
    const val slowMs = 400
    const val staggerMs = 30

    fun <T> springOrSnap(reducedMotion: Boolean): AnimationSpec<T> =
        if (reducedMotion) tween(durationMillis = 1) else spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMediumLow,
        )

    fun fadeOrSnap(reducedMotion: Boolean): AnimationSpec<Float> =
        if (reducedMotion) tween(durationMillis = 1) else tween(durationMillis = normalMs)

    fun stagger(index: Int, reducedMotion: Boolean): Int =
        if (reducedMotion || index >= 8) 0 else index * staggerMs
}
