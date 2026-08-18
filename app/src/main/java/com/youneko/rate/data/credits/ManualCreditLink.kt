package com.youneko.rate.data.credits

data class ManualCreditLink(
    val sourceId: CreditSourceId,
    val externalId: String,
    val url: String,
)

object ManualCreditLinkParser {
    fun parse(source: CreditSourceId, raw: String): ManualCreditLink? {
        val value = raw.trim()
        if (value.isBlank()) return null
        return when (source) {
            CreditSourceId.DISCOGS -> Regex("(?:discogs\\.com/(?:release|master)/)([0-9]+)", RegexOption.IGNORE_CASE).find(value)?.groupValues?.getOrNull(1)?.let { ManualCreditLink(source, it, value) }
            CreditSourceId.GENIUS -> value.substringBefore('?').trimEnd('/').substringAfterLast('/').takeIf { it.isNotBlank() }?.let { ManualCreditLink(source, it, value) }
            CreditSourceId.MUSICBRAINZ -> Regex("[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}").find(value)?.value?.let { ManualCreditLink(source, it, value) }
            else -> null
        }
    }
}
