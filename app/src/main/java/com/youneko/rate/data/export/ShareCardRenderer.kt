package com.youneko.rate.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(38, 32, 44); typeface = Typeface.DEFAULT_BOLD }
private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(76, 65, 83) }

object ShareCardRenderer {
    fun render(album: ShareAlbum, portrait: Boolean = false, cover: Bitmap? = null): Bitmap {
        val width = if (portrait) 1080 else 1080
        val height = if (portrait) 1920 else 1080
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(255, 249, 244))
        val coverRect = if (portrait) Rect(80, 100, width - 80, width - 80) else Rect(70, 90, 520, 540)
        cover?.let { canvas.drawBitmap(it, null, coverRect, Paint(Paint.ANTI_ALIAS_FLAG)) }
            ?: run { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(232, 220, 237) }.also { canvas.drawRect(coverRect, it) } }
        titlePaint.textSize = if (portrait) 58f else 52f
        bodyPaint.textSize = if (portrait) 38f else 34f
        val textLeft = if (portrait) 80f else 580f
        val top = if (portrait) (width + 20).toFloat() else 120f
        drawWrapped(canvas, album.title, textLeft, top, width - 80f - textLeft, titlePaint)
        drawWrapped(canvas, album.artist, textLeft, top + titlePaint.textSize * 1.4f, width - 80f - textLeft, bodyPaint)
        bodyPaint.textSize = if (portrait) 76f else 70f
        canvas.drawText(album.score?.let { "%.1f/5".format(it) } ?: "—", textLeft, top + 170f, bodyPaint)
        bodyPaint.textSize = if (portrait) 34f else 30f
        drawWrapped(canvas, album.tags.joinToString(" · "), textLeft, top + 240f, width - 80f - textLeft, bodyPaint)
        drawWrapped(canvas, album.reviewExcerpt.orEmpty(), textLeft, top + 320f, width - 80f - textLeft, bodyPaint)
        return bitmap
    }

    private fun drawWrapped(canvas: Canvas, text: String, x: Float, y: Float, maxWidth: Float, paint: Paint) {
        if (text.isBlank()) return
        var cursorY = y
        text.split(Regex("\\s+")).fold("") { line, word ->
            val candidate = if (line.isBlank()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotBlank()) {
                canvas.drawText(line, x, cursorY, paint); cursorY += paint.textSize * 1.25f; word
            } else candidate
        }.takeIf(String::isNotBlank)?.let { canvas.drawText(it, x, cursorY, paint) }
    }
}

data class ShareAlbum(
    val title: String,
    val artist: String,
    val score: Double?,
    val tags: List<String> = emptyList(),
    val reviewExcerpt: String? = null,
)

object CollageRenderer {
    fun render(albums: List<CollageAlbum>, columns: Int = 3, cellSize: Int = 360): Bitmap {
        require(columns == 3 || columns == 4) { "Collage supports 3x3 or 4x4" }
        val chosen = albums.take(columns * columns)
        val bitmap = Bitmap.createBitmap(columns * cellSize, columns * cellSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(255, 249, 244))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        chosen.forEachIndexed { index, album ->
            val left = (index % columns) * cellSize
            val top = (index / columns) * cellSize
            album.cover?.let { canvas.drawBitmap(it, null, Rect(left, top, left + cellSize, top + cellSize), paint) }
                ?: run { paint.color = Color.rgb(232, 220, 237); canvas.drawRect(left.toFloat(), top.toFloat(), (left + cellSize).toFloat(), (top + cellSize).toFloat(), paint) }
            paint.color = Color.WHITE; paint.textSize = 26f; paint.setShadowLayer(4f, 1f, 1f, Color.BLACK)
            canvas.drawText(album.title.take(28), left + 12f, top + cellSize - 34f, paint)
            album.score?.let { canvas.drawText("%.1f".format(it), left + 12f, top + cellSize - 10f, paint) }
            paint.clearShadowLayer()
        }
        return bitmap
    }
}

data class CollageAlbum(val title: String, val score: Double?, val cover: Bitmap? = null)
