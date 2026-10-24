package com.youneko.rate.ui.rate

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.youneko.rate.R
import com.youneko.rate.ui.YnDimens
import com.youneko.rate.ui.YnMotion
import com.youneko.rate.ui.rememberReducedMotion
import java.util.Locale
import kotlin.math.round

private val RatingGold = Color(0xFFE7A93C)

@Composable
fun StarRatingBar(
    value: Double?,
    onValueChange: (Double) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    step: Double = 0.5,
    enabled: Boolean = true,
    starSize: Dp = YnDimens.ratingStarSize,
    showValue: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val reducedMotion = rememberReducedMotion()
    val scale by animateFloatAsState(
        if (value != null) 1.02f else 1f,
        animationSpec = YnMotion.springOrSnap(reducedMotion),
        label = "starScale",
    )
    val current = value ?: 0.0
    val description = stringResource(R.string.rating_accessibility, current)
    Row(
        modifier = modifier.heightIn(min = YnDimens.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .scale(scale)
                .semantics { contentDescription = description }
                .pointerInput(enabled, step, starSize) {
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
                .pointerInput(enabled, step, starSize) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            val next = quantize((offset.x / size.width.toFloat()) * 5.0, step)
                            if (next != value) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onValueChange(next)
                            }
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
            contentAlignment = Alignment.CenterStart,
        ) {
            Row {
                repeat(5) { index ->
                    val fill = (current - index).coerceIn(0.0, 1.0).toFloat()
                    Box(Modifier.size(starSize)) {
                        Icon(
                            imageVector = Icons.Outlined.StarBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(starSize),
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
                                    tint = RatingGold,
                                    modifier = Modifier.size(starSize),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (showValue) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = value?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: stringResource(R.string.not_rated),
                style = MaterialTheme.typography.labelLarge,
                color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(end = 2.dp),
            )
        }
    }
}

private fun quantize(raw: Double, step: Double): Double {
    val bounded = raw.coerceIn(0.5, 5.0)
    return (round(bounded / step) * step).coerceIn(0.5, 5.0)
}
