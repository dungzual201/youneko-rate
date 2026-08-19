package com.youneko.rate.data.lyrics

sealed interface Lyrics {
    data class Plain(val text: String) : Lyrics
    data class Timed(val lines: List<LyricLine>) : Lyrics
}

data class LyricLine(
    val startMs: Long,
    val endMs: Long? = null,
    val text: String,
    val agent: String? = null,
    val isBackground: Boolean = false,
    val translation: String? = null,
)

enum class LyricsFormat { TTML, LRC, PLAIN }
