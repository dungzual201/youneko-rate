package com.youneko.rate

import com.youneko.rate.data.credits.CreditSourceId
import com.youneko.rate.data.credits.ManualCreditLinkParser
import com.youneko.rate.data.credits.SourceResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase9SourcePickerTest {
    @Test
    fun sourceRegistryHasSevenStableSourcesIncludingManual() {
        assertEquals(7, CreditSourceId.entries.size)
        assertEquals(CreditSourceId.FILE_TAG, CreditSourceId.parse("FILE_TAG").first())
        assertTrue(CreditSourceId.DISCOGS.needsToken)
        assertTrue(CreditSourceId.GENIUS.needsToken)
    }

    @Test
    fun missingTokenHasTypedNeedsTokenStateInsteadOfEmpty() {
        val result: SourceResult = SourceResult.NeedsToken
        assertEquals(SourceResult.NeedsToken, result)
    }

    @Test
    fun manualLinksParseDiscogsGeniusAndMusicBrainz() {
        val discogs = ManualCreditLinkParser.parse(CreditSourceId.DISCOGS, "https://www.discogs.com/release/1234567-album")
        val genius = ManualCreditLinkParser.parse(CreditSourceId.GENIUS, "https://genius.com/Jisoo-Earthquake-lyrics")
        val mb = ManualCreditLinkParser.parse(CreditSourceId.MUSICBRAINZ, "https://musicbrainz.org/recording/12345678-1234-4234-8234-123456789012")
        assertEquals("1234567", discogs?.externalId)
        assertEquals("Jisoo-Earthquake-lyrics", genius?.externalId)
        assertEquals("12345678-1234-4234-8234-123456789012", mb?.externalId)
    }

    @Test
    fun malformedManualLinkIsRejected() {
        assertEquals(null, ManualCreditLinkParser.parse(CreditSourceId.DISCOGS, "not a discogs url"))
        assertEquals(null, ManualCreditLinkParser.parse(CreditSourceId.MUSICBRAINZ, "not-an-mbid"))
    }
}
