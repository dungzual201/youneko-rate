package com.youneko.rate.ui.analyze

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.youneko.rate.data.audio.CachedSpectrogram
import com.youneko.rate.data.audio.SPECTROGRAM_DB_CEILING
import com.youneko.rate.data.audio.SPECTROGRAM_DB_FLOOR
import com.youneko.rate.data.audio.SpectrogramLut
import com.youneko.rate.data.audio.SpectrogramMath
import com.youneko.rate.data.audio.formatSpectrogramTime
import kotlin.math.max
import kotlin.math.roundToInt
import java.io.File

object SpectrogramBitmapRenderer {
    fun render(cached: CachedSpectrogram, logarithmic: Boolean, dbFloor: Float): Bitmap {
        val width = cached.metadata.spectrogram.columns.coerceAtLeast(1).coerceAtMost(2_000)
        val height = cached.metadata.spectrogram.rows.coerceAtLeast(1).coerceAtMost(1_024)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        val rows = cached.metadata.spectrogram.rows
        val nyquist = cached.metadata.spectrogram.sampleRate / 2.0
        for (column in 0 until width) {
            for (row in 0 until height) {
                val normalizedFromTop = row.toDouble() / (height - 1).coerceAtLeast(1)
                val frequency = if (!logarithmic) {
                    nyquist * (1.0 - normalizedFromTop)
                } else {
                    val minimum = 20.0
                    val logFrequency = minimum * Math.pow(nyquist / minimum, normalizedFromTop)
                    nyquist - logFrequency
                }
                val sourceRow = ((frequency / nyquist) * (rows - 1)).roundToInt().coerceIn(0, rows - 1)
                val matrixIndex = column * rows + sourceRow
                val db = SpectrogramMath.dequantizeDb(cached.matrix.getOrElse(matrixIndex) { 0 })
                pixels[row * width + column] = SpectrogramLut.color(db, dbFloor)
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    fun writePng(cached: CachedSpectrogram, logarithmic: Boolean, dbFloor: Float, target: File): File {
        target.parentFile?.mkdirs()
        val bitmap = render(cached, logarithmic, dbFloor)
        target.outputStream().buffered().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Không thể xuất PNG" }
        }
        bitmap.recycle()
        return target
    }
}

@Composable
fun SpectrogramView(
    cached: CachedSpectrogram,
    logarithmic: Boolean,
    dbFloor: Float,
    showAxes: Boolean,
    resetToken: Int = 0,
    modifier: Modifier = Modifier,
    onTooltip: (String?) -> Unit = {},
) {
    val outline = MaterialTheme.colorScheme.outline
    val plotBitmap = remember(cached.metadata, cached.matrix.contentHashCode(), logarithmic, dbFloor) {
        SpectrogramBitmapRenderer.render(cached, logarithmic, dbFloor).asImageBitmap()
    }
    var zoom by remember(cached.metadata) { mutableFloatStateOf(1f) }
    var panX by remember(cached.metadata) { mutableFloatStateOf(0f) }
    LaunchedEffect(resetToken) {
        zoom = 1f
        panX = 0f
    }
    Box(modifier.fillMaxWidth().height(360.dp)) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(plotBitmap, showAxes) {
                    detectTransformGestures { _, pan, zoomChange, _ ->
                        zoom = (zoom * zoomChange).coerceIn(1f, 8f)
                        val plotWidth = (size.width - if (showAxes) 92f else 0f).coerceAtLeast(1f)
                        val maxPan = plotWidth * (zoom - 1f)
                        panX = (panX + pan.x).coerceIn(-maxPan, 0f)
                    }
                }
                .pointerInput(plotBitmap, zoom, panX, showAxes, logarithmic) {
                    detectTapGestures { position ->
                        val left = if (showAxes) 52f else 0f
                        val top = 12f
                        val right = size.width - if (showAxes) 42f else 0f
                        val bottom = size.height - if (showAxes) 34f else 0f
                        val plotWidth = (right - left).coerceAtLeast(1f)
                        val plotHeight = (bottom - top).coerceAtLeast(1f)
                        val sourceX = ((position.x - left - panX) / (plotWidth * zoom)).coerceIn(0f, 1f)
                        val sourceY = ((position.y - top) / plotHeight).coerceIn(0f, 1f)
                        val column = (sourceX * (cached.metadata.spectrogram.columns - 1)).roundToInt()
                        val row = ((1f - sourceY) * (cached.metadata.spectrogram.rows - 1)).roundToInt()
                        val time = SpectrogramMath.timeForColumn(column, cached.metadata.spectrogram.columns, cached.metadata.spectrogram.durationMs)
                        val frequency = SpectrogramMath.frequencyForRow(row, cached.metadata.spectrogram.rows, cached.metadata.spectrogram.sampleRate, logarithmic)
                        val index = column * cached.metadata.spectrogram.rows + row
                        val db = SpectrogramMath.dequantizeDb(cached.matrix.getOrElse(index) { 0 })
                        onTooltip("t = ${formatSpectrogramTime(time)}, f = %.1f kHz, %.0f dB".format(java.util.Locale.US, frequency / 1_000.0, db))
                    }
                },
        ) {
            val left = if (showAxes) 52f else 0f
            val top = 12f
            val right = size.width - if (showAxes) 42f else 0f
            val bottom = size.height - if (showAxes) 34f else 0f
            val plotWidth = (right - left).coerceAtLeast(1f)
            val plotHeight = (bottom - top).coerceAtLeast(1f)
            clipRect(left, top, right, bottom) {
                withTransform({
                    translate(left + panX, top)
                }) {
                    drawImage(
                        plotBitmap,
                        dstSize = IntSize((plotWidth * zoom).roundToInt().coerceAtLeast(1), plotHeight.roundToInt().coerceAtLeast(1)),
                        filterQuality = FilterQuality.None,
                    )
                }
            }
            if (showAxes) {
                drawLine(outline, Offset(left, top), Offset(left, bottom), strokeWidth = 2f)
                drawLine(outline, Offset(left, bottom), Offset(right, bottom), strokeWidth = 2f)
                drawAxes(this, left, top, right, bottom, cached, logarithmic, outline)
                drawLegend(this, right + 8f, top, bottom, dbFloor, outline)
            }
        }
    }
}

private fun drawAxes(
    scope: androidx.compose.ui.graphics.drawscope.DrawScope,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    cached: CachedSpectrogram,
    logarithmic: Boolean,
    outline: androidx.compose.ui.graphics.Color,
) = with(scope) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = outline.toArgb(); textSize = 20f }
    val sampleRate = cached.metadata.spectrogram.sampleRate
    val nyquist = sampleRate / 2.0
    val frequencyTicks = listOf(0.0, 5_000.0, 10_000.0, 15_000.0, 20_000.0, nyquist).distinct().filter { it <= nyquist + 1 }
    frequencyTicks.forEach { frequency ->
        val normalized = if (!logarithmic) frequency / nyquist else {
            if (frequency <= 0.0) 0.0 else kotlin.math.ln(frequency / 20.0) / kotlin.math.ln(nyquist / 20.0)
        }
        val y = bottom - (normalized * (bottom - top)).toFloat()
        drawLine(outline.copy(alpha = 0.35f), Offset(left, y), Offset(right, y), strokeWidth = 1f)
        drawIntoCanvas { canvas -> canvas.nativeCanvas.drawText(if (frequency >= 1000) "${(frequency / 1000).roundToInt()} kHz" else "0", 2f, y + 6f, paint) }
    }
    val duration = cached.metadata.spectrogram.durationMs
    val timeTicks = 0..4
    timeTicks.forEach { tick ->
        val x = left + (right - left) * tick / 4f
        drawLine(outline.copy(alpha = 0.35f), Offset(x, top), Offset(x, bottom), strokeWidth = 1f)
        drawIntoCanvas { canvas -> canvas.nativeCanvas.drawText(formatSpectrogramTime(duration * tick / 4), x - 18f, bottom + 24f, paint) }
    }
}

private fun drawLegend(
    scope: androidx.compose.ui.graphics.drawscope.DrawScope,
    x: Float,
    top: Float,
    bottom: Float,
    dbFloor: Float,
    outline: androidx.compose.ui.graphics.Color,
) = with(scope) {
    val width = 18f
    val steps = 64
    repeat(steps) { index ->
        val normalized = index.toFloat() / (steps - 1)
        val y = bottom - normalized * (bottom - top)
        val nextY = bottom - (index + 1).toFloat() / (steps - 1) * (bottom - top)
        drawRect(SpectrogramLut.color(dbFloor + normalized * -dbFloor, dbFloor).let { androidx.compose.ui.graphics.Color(it) }, androidx.compose.ui.geometry.Offset(x, nextY), androidx.compose.ui.geometry.Size(width, y - nextY + 1f))
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = outline.toArgb(); textSize = 18f }
    listOf(dbFloor, -60f, -30f, 0f).forEach { db ->
        val y = bottom - ((db - dbFloor) / (0f - dbFloor)).coerceIn(0f, 1f) * (bottom - top)
        drawIntoCanvas { canvas -> canvas.nativeCanvas.drawText("${db.roundToInt()} dB", x + width + 4f, y + 5f, paint) }
    }
}
