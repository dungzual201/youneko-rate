package com.youneko.rate.data.lyrics

import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.Locale
import kotlin.math.roundToLong

private const val LYRICS_TAG = "LYRICS"

object LyricsParser {
    fun sniff(raw: String): LyricsFormat {
        val head = raw.trimStart().lowercase(Locale.ROOT)
        return when {
            head.startsWith("<tt") || head.contains("http://www.w3.org/ns/ttml") -> LyricsFormat.TTML
            raw.lineSequence().any { it.trimStart().matches(Regex("\\[\\d{1,2}:\\d{2}.*")) } -> LyricsFormat.LRC
            else -> LyricsFormat.PLAIN
        }
    }

    fun parse(raw: String, fileName: String? = null): Lyrics {
        val format = sniff(raw)
        val rawHead = raw.take(500).replace("\n", "\\n").replace("\r", "\\r")
        Log.i(LYRICS_TAG, "LYRICS: format=$format rawHead=$rawHead file=${fileName ?: "unknown"}")
        return when (format) {
            LyricsFormat.TTML -> parseTtmlSafely(raw, fileName)
            LyricsFormat.LRC -> parseLrc(raw)
            LyricsFormat.PLAIN -> Lyrics.Plain(sanitizeReadableText(raw.trim(), "plain"))
        }
    }

    fun parseTtml(raw: String): Lyrics.Timed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        parser.setInput(StringReader(raw))
        val lines = mutableListOf<LyricLine>()
        val spanContexts = ArrayDeque<SpanContext>()
        var current: MutableTtmlLine? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase(Locale.ROOT)) {
                        "p" -> current = MutableTtmlLine(
                            startMs = parseTime(attribute(parser, "begin")),
                            endMs = attribute(parser, "end")?.let(::parseTime),
                            agent = attribute(parser, "agent")?.cleanAgent(),
                        ).also { it.background = attribute(parser, "role") == "x-bg" }
                        "span" -> if (current != null) {
                            val role = attribute(parser, "role")
                            val agent = attribute(parser, "agent")?.cleanAgent()
                            if (agent != null) current?.agent = agent
                            spanContexts.addLast(
                                SpanContext(
                                    role = role,
                                    startMs = attribute(parser, "begin")?.let(::parseTime),
                                    endMs = attribute(parser, "end")?.let(::parseTime),
                                ),
                            )
                            if (role == "x-bg") current?.background = true
                        }
                        "br" -> current?.appendMain("\n")
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.ENTITY_REF -> {
                    current?.let { line ->
                        val text = parser.text.orEmpty()
                        val span = spanContexts.lastOrNull()
                        if (span?.role == "x-translation") {
                            line.appendTranslation(text)
                        } else {
                            line.appendMain(text, span?.startMs, span?.endMs)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name.lowercase(Locale.ROOT)) {
                        "span" -> if (spanContexts.isNotEmpty()) spanContexts.removeLast()
                        "p" -> current?.let { line ->
                            lines += line.toImmutable()
                            current = null
                            spanContexts.clear()
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
                LyricLine((minute * 60 + second) * 1000 + ms, text = sanitizeReadableText(text, "lrc"))
            }
        }.filter { it.text.isNotBlank() }.sortedBy { it.startMs }.toList()
        return Lyrics.Timed(lines.withEndTimes())
    }

    private fun parseTtmlSafely(raw: String, fileName: String?): Lyrics = runCatching { parseTtml(raw) }.getOrElse { error ->
        Log.w(LYRICS_TAG, "TTML parse failed for ${fileName ?: "unknown file"}", error)
        Lyrics.Plain(stripMarkup(raw))
    }

    private fun stripMarkup(raw: String): String = sanitizeReadableText(
        raw.replace(Regex("<[^>]*>"), "").trim(),
        "fallback",
    )

    private fun sanitizeReadableText(text: String, source: String): String {
        val cleaned = text
            .replace(Regex("<\\s*\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?\\s*>"), "")
            .replace(Regex("<[^>]*>"), "")
            .replace(Regex("^\\s*v\\d+:\\s*", RegexOption.IGNORE_CASE), "")
            .replace("<", "")
            .replace(">", "")
        if (cleaned != text || Regex("<\\s*\\d{1,2}:\\d{2}").containsMatchIn(text)) {
            Log.w(LYRICS_TAG, "LYRICS: leftover marker in line=$source")
        }
        return cleaned
    }

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

    private data class SpanContext(
        val role: String?,
        val startMs: Long?,
        val endMs: Long?,
    )

    private class MutableTtmlLine(
        val startMs: Long,
        val endMs: Long?,
        var agent: String?,
    ) {
        private val main = StringBuilder()
        private val translation = StringBuilder()
        private val wordTimings = mutableListOf<WordTiming>()
        var background: Boolean = false

        fun appendMain(text: String, wordStartMs: Long? = null, wordEndMs: Long? = null) {
            val readable = sanitizeReadableText(text, "ttml")
            main.append(readable)
            if (wordStartMs != null) {
                val word = readable.trim()
                if (word.isNotBlank()) wordTimings += WordTiming(wordStartMs, wordEndMs, word)
            }
        }

        fun appendTranslation(text: String) { translation.append(sanitizeReadableText(text, "translation")) }

        fun toImmutable(): LyricLine {
            val text = main.toString().replace(Regex("[ \\t\\r\\n]+"), " ").trim()
            return LyricLine(
                startMs = startMs,
                endMs = endMs,
                text = sanitizeReadableText(text, "ttml-final"),
                words = wordTimings.toList(),
                agent = agent,
                isBackground = background,
                translation = translation.toString().trim().ifBlank { null },
            )
        }
    }

    private fun String.cleanAgent(): String = substringAfterLast(':').trim().removePrefix("v1:").removePrefix("v2:").trim()

    private fun List<LyricLine>.withEndTimes(): List<LyricLine> = mapIndexed { index, line ->
        line.copy(endMs = line.endMs ?: getOrNull(index + 1)?.startMs)
    }
}
