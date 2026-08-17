package com.youneko.rate.ui.rate

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.youneko.rate.R
import kotlin.math.ceil
import kotlin.math.round

@Composable
fun StarRatingBar(
    value: Double?,
    onValueChange: (Double) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    step: Double = 0.5,
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (value != null) 1.08f else 1f, label = "starScale")
    val current = value ?: 0.0
    val description = stringResource(R.string.rating_accessibility, current)
    Row(
        modifier = modifier
            .scale(scale)
            .semantics { contentDescription = description }
            .pointerInput(enabled, step) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onLongPress = { onClear() },
                    onTap = { offset ->
                        val next = quantize((offset.x / size.width.toFloat()) * 5.0, step)
                        if (next != value) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onValueChange(next)
                        }
                    },
                )
            }
            .pointerInput(enabled, step) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val next = quantize((offset.x / size.width.toFloat()) * 5.0, step)
                        if (next != value) onValueChange(next)
                    },
                    onDrag = { change, _ ->
                        val next = quantize((change.position.x / size.width.toFloat()) * 5.0, step)
                        if (next != value) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onValueChange(next)
                        }
                    },
                    onDragEnd = {},
                    onDragCancel = {},
                )
            },
    ) {
        val density = LocalDensity.current
        repeat(10) { index ->
            val threshold = (index + 1) * 0.5
            val filled = current >= threshold
            val half = !filled && current >= threshold - 0.5
            Icon(
                imageVector = if (filled || half) Icons.Default.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                tint = if (filled || half) Color(0xFFFFB84D) else Color(0xFFB9B3C2),
                modifier = Modifier.size(with(density) { 20.dp }),
            )
        }
    }
}

private fun quantize(raw: Double, step: Double): Double {
    val bounded = raw.coerceIn(0.5, 5.0)
    return (round(bounded / step) * step).coerceIn(0.5, 5.0)
}
