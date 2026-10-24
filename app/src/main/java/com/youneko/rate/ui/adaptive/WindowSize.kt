package com.youneko.rate.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.youneko.rate.ui.YnDimens

enum class YnWindowSize { COMPACT, MEDIUM, EXPANDED }

data class YnWindowInfo(
    val widthDp: Int,
    val heightDp: Int,
    val size: YnWindowSize,
    val isTwoPane: Boolean,
)

@Composable
@ReadOnlyComposable
fun rememberYnWindowInfo(): YnWindowInfo {
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp
    val size = when {
        width >= 840 -> YnWindowSize.EXPANDED
        width >= 600 -> YnWindowSize.MEDIUM
        else -> YnWindowSize.COMPACT
    }
    return YnWindowInfo(
        widthDp = width,
        heightDp = configuration.screenHeightDp,
        size = size,
        isTwoPane = width >= 840,
    )
}

/**
 * Horizontal padding that keeps long lists readable and centres them on tablets.
 */
@Composable
@ReadOnlyComposable
fun rememberYnPagePadding(
    maxContentWidth: Dp = YnDimens.contentMaxWidth,
    base: Dp = YnDimens.screenPadding,
): Dp {
    val width = LocalConfiguration.current.screenWidthDp
    val spare = (width - maxContentWidth.value).coerceAtLeast(0f) / 2f
    return spare.dp + base
}

@Composable
@ReadOnlyComposable
fun rememberYnGridColumns(
    minCellWidth: Dp = YnDimens.gridMinCell,
    maxColumns: Int = 5,
): Int {
    val width = LocalConfiguration.current.screenWidthDp - (YnDimens.screenPadding.value * 2)
    return (width / minCellWidth.value).toInt().coerceIn(2, maxColumns)
}

@Composable
fun YnPageFrame(
    modifier: Modifier = Modifier,
    maxWidth: Dp = YnDimens.contentMaxWidth,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier.fillMaxWidth().widthIn(max = maxWidth),
            content = content,
        )
    }
}
