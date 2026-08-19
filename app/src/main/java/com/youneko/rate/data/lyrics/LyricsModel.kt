package com.youneko.rate.data.lyrics

sealed interface Lyrics {
    data class Plain(val text: String) : Lyrics
    data class Timed(val lines: List<LyricLine>) : Lyrics
}

data class WordTiming(
    val startMs: Long,
    val endMs: Long?,
    val text: String,
)

data class LyricLine(
    val startMs: Long,
    val endMs: Long? = null,
    val text: String,
    val words: List<WordTiming> = emptyList(),
    val agent: String? = null,
    val isBackground: Boolean = false,
    val translation: String? = null,
)

enum class LyricsFormat { TTML, LRC, PLAIN }

fun Lyrics.toPlainText(): String = when (this) {
    is Lyrics.Plain -> text
    is Lyrics.Timed -> lines.joinToString("\n") { line ->
        buildString {
            append(line.text)
            line.translation?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
        }
    }
}
