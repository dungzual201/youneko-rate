package com.youneko.rate.ui.rate

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.youneko.rate.R
import java.util.Locale
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
    val scale by animateFloatAsState(if (value != null) 1.04f else 1f, label = "starScale")
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
        repeat(5) { index ->
            val fill = (current - index).coerceIn(0.0, 1.0).toFloat()
            Box(Modifier.size(26.dp)) {
                Icon(
                    imageVector = Icons.Outlined.StarBorder,
                    contentDescription = null,
                    tint = Color(0xFFB9B3C2),
                    modifier = Modifier.size(26.dp),
                )
                if (fill > 0f) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fill)
                            .clip(RectangleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFB84D),
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }
        Text(
            text = value?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—",
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

private fun quantize(raw: Double, step: Double): Double {
    val bounded = raw.coerceIn(0.5, 5.0)
    return (round(bounded / step) * step).coerceIn(0.5, 5.0)
}
