package com.youneko.rate

import com.youneko.rate.data.credits.CreditSourceId
import com.youneko.rate.data.credits.SourceResult
import com.youneko.rate.data.local.entity.CreditEntity
import com.youneko.rate.ui.credits.CreditsContentState
import com.youneko.rate.ui.credits.CreditsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditsRenderInvariantTest {
    @Test
    fun storedSourceUsesEnumIdNotDisplayName() {
        assertEquals(CreditSourceId.MUSICBRAINZ, CreditSourceId.fromStored("MUSICBRAINZ"))
        assertEquals(CreditSourceId.MUSICBRAINZ, CreditSourceId.fromStored("MusicBrainz"))
        assertEquals(CreditSourceId.FILE_TAG, CreditSourceId.fromStored("file_tags"))
        assertEquals(CreditSourceId.ITUNES, CreditSourceId.fromStored("Apple Music"))
    }

    @Test
    fun headerAndRenderedRowsUseSameCount() {
        val rows = listOf(row("1", null), row("2", "track-1"), row("3", "track-1"))
        val state = CreditsUiState(
            perSource = mapOf(CreditSourceId.MUSICBRAINZ to SourceResult.Success(rows.map { it.toCandidate() })),
            perSourceCredits = mapOf(CreditSourceId.MUSICBRAINZ to rows),
            activeSources = setOf(CreditSourceId.MUSICBRAINZ),
            content = CreditsContentState.Data,
        )
        val renderedRows = state.rowsFor(CreditSourceId.MUSICBRAINZ)
        assertEquals(renderedRows.size, state.rowsFor(CreditSourceId.MUSICBRAINZ).size)
        assertEquals(3, renderedRows.size)
        assertTrue(state.hasRenderableRows)
    }

    @Test
    fun emptyStateCannotCoexistWithRenderableRows() {
        val state = CreditsUiState(
            perSourceCredits = mapOf(CreditSourceId.MUSICBRAINZ to listOf(row("1", null))),
            activeSources = setOf(CreditSourceId.MUSICBRAINZ),
            content = CreditsContentState.Data,
        )
        assertFalse(state.content == CreditsContentState.Empty && state.hasRenderableRows)
    }

    private fun row(id: String, trackId: String?) = CreditEntity(
        id = id,
        albumId = if (trackId == null) "album-1" else null,
        trackId = trackId,
        personName = "Person $id",
        personMbid = null,
        role = "Mix",
        instrumentOrAttribute = null,
        sourceProvider = CreditSourceId.MUSICBRAINZ.name,
        sourceUrl = null,
        sortOrder = 0,
        beginDate = null,
        endDate = null,
    )

    private fun CreditEntity.toCandidate() = com.youneko.rate.data.musicbrainz.CreditCandidate(
        personName, personMbid, role, instrumentOrAttribute, sourceProvider, sourceUrl, beginDate, endDate,
    )
}
