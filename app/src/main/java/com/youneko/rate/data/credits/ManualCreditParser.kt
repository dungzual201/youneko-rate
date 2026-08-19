package com.youneko.rate.data.credits

/** A credit entered by the user before it is persisted in Room. */
data class ManualCreditDraft(
    val personName: String,
    val role: String,
)

object ManualCreditParser {
    private val knownRoles = setOf(
        "artist", "vocal", "vocals", "writer", "songwriter", "producer", "co-producer",
        "composer", "lyricist", "arranger", "mixing engineer", "mastering engineer",
        "recording", "recording engineer", "guitar", "bass", "drums", "keyboard", "piano",
        "strings", "sáng tác", "nhạc sĩ", "lời", "ca sĩ", "hát chính", "sản xuất",
        "đồng sản xuất", "hòa âm", "phối khí", "kỹ thuật thu âm", "trộn âm", "làm chủ âm thanh",
        "trình diễn", "nhạc cụ", "khác",
    )

    fun parse(text: String): List<ManualCreditDraft> = text.lineSequence()
        .mapNotNull(::parseLine)
        .distinctBy { it.personName.trim().lowercase() to it.role.trim().lowercase() }
        .toList()

    private fun parseLine(raw: String): ManualCreditDraft? {
        val line = raw.trim().removePrefix("-").removePrefix("•").trim()
        if (line.isBlank()) return null
        val separator = when {
            line.contains('—') -> "—"
            line.contains(" - ") -> " - "
            line.contains(':') -> ":"
            else -> return null
        }
        val parts = line.split(separator, limit = 2).map(String::trim)
        if (parts.size != 2 || parts.any(String::isBlank)) return null
        val left = parts[0]
        val right = parts[1]
        val leftIsRole = isKnownRole(left)
        val rightIsRole = isKnownRole(right)
        val (person, role) = when {
            separator == ":" && leftIsRole -> right to left
            rightIsRole -> left to right
            separator == ":" -> right to left
            else -> left to right
        }
        return ManualCreditDraft(person.trim(), role.trim()).takeIf { it.personName.isNotBlank() && it.role.isNotBlank() }
    }

    private fun isKnownRole(value: String): Boolean = value.trim().lowercase() in knownRoles
}
