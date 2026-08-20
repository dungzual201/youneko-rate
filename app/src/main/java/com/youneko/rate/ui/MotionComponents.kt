package com.youneko.rate.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

sealed interface AsyncActionState {
    data object Idle : AsyncActionState
    data object Loading : AsyncActionState
    data object Success : AsyncActionState
}

@Composable
fun YounekoActionButton(
    state: AsyncActionState,
    label: String,
    successLabel: String = label,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val pressed by remember { MutableInteractionSource() }.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !reduced) 0.96f else 1f, label = "buttonScale")
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state) {
        if (state is AsyncActionState.Success) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    Button(
        onClick = onClick,
        enabled = state is AsyncActionState.Idle,
        modifier = modifier.scale(scale).height(48.dp).semantics { contentDescription = label },
        shape = RoundedCornerShape(YounekoRadius.lg),
    ) {
        AnimatedContent(targetState = state, label = "actionState") { current ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(YounekoSpacing.xs)) {
                when (current) {
                    AsyncActionState.Idle -> Text(label)
                    AsyncActionState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        Text(label)
                    }
                    AsyncActionState.Success -> {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Text(successLabel)
                    }
                }
            }
        }
    }
}

@Composable
fun YounekoStaggeredColumn(
    itemCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    val reduced = rememberReducedMotion()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(YounekoSpacing.sm)) {
        repeat(itemCount) { index ->
            var visible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(reduced) }
            LaunchedEffect(index, reduced) {
                if (!reduced) {
                    delay(younekoStaggerDelay(index, false).toLong())
                    visible = true
                }
            }
            AnimatedVisibility(visible = visible, enter = fadeIn(animationSpec = younekoFade(reduced)) + slideInVertically(animationSpec = if (reduced) snap() else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it / 4 }, exit = fadeOut(animationSpec = younekoFade(reduced))) {
                content(index)
            }
        }
    }
}

@Composable
fun YounekoShimmer(modifier: Modifier = Modifier, lines: Int = 2) {
    val shimmer = Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(YounekoSpacing.xs)) {
        Box(Modifier.fillMaxWidth().size(104.dp).background(MaterialThemeTokens.placeholder, RoundedCornerShape(YounekoRadius.md)))
        repeat(lines) { index -> Box(Modifier.fillMaxWidth(if (index == lines - 1) 0.62f else 0.9f).height(14.dp).background(MaterialThemeTokens.placeholder, RoundedCornerShape(YounekoRadius.sm))) }
        Box(Modifier.fillMaxWidth().height(2.dp).background(shimmer))
    }
}

private object MaterialThemeTokens {
    val placeholder = Color(0xFFE3DCE8)
}
