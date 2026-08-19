package com.youneko.rate.data.lyrics

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.Locale
import kotlin.math.roundToLong

object LyricsParser {
    fun sniff(raw: String): LyricsFormat {
        val head = raw.trimStart().lowercase(Locale.ROOT)
        return when {
            head.startsWith("<tt") || head.contains("http://www.w3.org/ns/ttml") -> LyricsFormat.TTML
            raw.lineSequence().any { it.trimStart().matches(Regex("\\[\\d{1,2}:\\d{2}.*")) } -> LyricsFormat.LRC
            else -> LyricsFormat.PLAIN
        }
    }

    fun parse(raw: String, fileName: String? = null): Lyrics = when (sniff(raw)) {
        LyricsFormat.TTML -> parseTtmlSafely(raw, fileName)
        LyricsFormat.LRC -> parseLrc(raw)
        LyricsFormat.PLAIN -> Lyrics.Plain(raw.trim())
    }

    fun parseTtml(raw: String): Lyrics.Timed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        parser.setInput(StringReader(raw))
        val lines = mutableListOf<LyricLine>()
        var event = parser.eventType
        var current: MutableTtmlLine? = null
        val spanRoles = ArrayDeque<SpanContext>()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase(Locale.ROOT)) {
                        "p" -> current = MutableTtmlLine(
                            startMs = parseTime(attribute(parser, "begin")),
                            endMs = attribute(parser, "end")?.let(::parseTime),
                            agent = attribute(parser, "agent"),
                        ).also { it.background = attribute(parser, "role") == "x-bg" }
                        "span" -> if (current != null) {
                            val role = attribute(parser, "role")
                            spanRoles.addLast(SpanContext(role))
                            if (role == "x-bg") current.background = true
                        }
                        "br" -> current?.appendMain("\n")
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.ENTITY_REF -> {
                    current?.let { line ->
                        val text = parser.text.orEmpty()
                        val role = spanRoles.lastOrNull()?.role
                        if (role == "x-translation") line.appendTranslation(text) else line.appendMain(text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name.lowercase(Locale.ROOT)) {
                        "span" -> if (spanRoles.isNotEmpty()) spanRoles.removeLast()
                        "p" -> current?.let { line ->
                            lines += line.toImmutable()
                            current = null
                            spanRoles.clear()
                        }
                    }
                }
            }
            event = parser.next()
        }
        if (current != null) throw org.xmlpull.v1.XmlPullParserException("Unclosed TTML paragraph")
        return Lyrics.Timed(lines.filter { it.text.isNotBlank() }.sortedBy { it.startMs }.withEndTimes())
    }

    fun parseLrc(raw: String): Lyrics.Timed {
        val pattern = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\](.*)")
        val lines = raw.lineSequence().flatMap { line ->
            val matches = pattern.findAll(line)
            val text = matches.firstOrNull()?.groupValues?.getOrNull(4)?.trim().orEmpty()
            matches.map { match ->
                val minute = match.groupValues[1].toLongOrNull() ?: 0L
                val second = match.groupValues[2].toLongOrNull() ?: 0L
                val fraction = match.groupValues[3].toLongOrNull() ?: 0L
                val ms = if (match.groupValues[3].length <= 2) fraction * 10L else fraction
                LyricLine((minute * 60 + second) * 1000 + ms, text = text)
            }
        }.filter { it.text.isNotBlank() }.sortedBy { it.startMs }.toList()
        return Lyrics.Timed(lines.withEndTimes())
    }

    private fun parseTtmlSafely(raw: String, fileName: String?): Lyrics = runCatching { parseTtml(raw) }.getOrElse { error ->
        android.util.Log.w("LyricsParser", "TTML parse failed for ${fileName ?: "unknown file"}", error)
        Lyrics.Plain(stripMarkup(raw))
    }

    private fun stripMarkup(raw: String): String = raw
        .replace(Regex("<[^>]*>"), "")
        .replace("<", "")
        .replace(">", "")
        .trim()

    private fun attribute(parser: XmlPullParser, localName: String): String? = (0 until parser.attributeCount)
        .mapNotNull { index ->
            val name = parser.getAttributeName(index)
            val normalized = name.substringAfter(':').lowercase(Locale.ROOT)
            if (normalized == localName.lowercase(Locale.ROOT)) parser.getAttributeValue(index) else null
        }
        .firstOrNull()

    fun parseTime(value: String?): Long {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return 0L
        return runCatching {
            when {
                raw.endsWith("ms", true) -> raw.dropLast(2).toDouble().roundToLong()
                raw.endsWith("s", true) -> (raw.dropLast(1).toDouble() * 1_000.0).roundToLong()
                raw.endsWith("m", true) -> (raw.dropLast(1).toDouble() * 60_000.0).roundToLong()
                raw.count { it == ':' } == 3 -> {
                    val parts = raw.split(':')
                    ((parts[0].toLong() * 60 + parts[1].toLong()) * 60 + parts[2].toLong()) * 1000
                }
                raw.count { it == ':' } == 2 -> {
                    val parts = raw.split(':')
                    ((parts[0].toLong() * 60 + parts[1].toLong()) * 60_000) + (parts[2].toDouble() * 1000).roundToLong()
                }
                raw.count { it == ':' } == 1 -> {
                    val parts = raw.split(':')
                    parts[0].toLong() * 60_000 + (parts[1].toDouble() * 1000).roundToLong()
                }
                else -> 0L
            }
        }.getOrDefault(0L)
    }

    private data class SpanContext(val role: String?)

    private class MutableTtmlLine(
        val startMs: Long,
        val endMs: Long?,
        val agent: String?,
    ) {
        private val main = StringBuilder()
        private val translation = StringBuilder()
        var background: Boolean = false

        fun appendMain(text: String) { main.append(text) }
        fun appendTranslation(text: String) { translation.append(text) }
        fun toImmutable() = LyricLine(startMs, endMs, main.toString(), agent, background, translation.toString().trim().ifBlank { null })
    }

    private fun List<LyricLine>.withEndTimes(): List<LyricLine> = mapIndexed { index, line ->
        line.copy(endMs = line.endMs ?: getOrNull(index + 1)?.startMs)
    }
}
