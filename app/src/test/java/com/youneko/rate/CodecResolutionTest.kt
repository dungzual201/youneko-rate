package com.youneko.rate

import com.youneko.rate.data.audio.CodecDetector
import com.youneko.rate.data.audio.CodecGroup
import com.youneko.rate.data.audio.DetectionSource
import org.junit.Assert.assertEquals
import org.junit.Test

class CodecResolutionTest {
    @Test
    fun magicBytesWinOverRawMime() {
        val resolved = CodecDetector.resolve("fLaC........".encodeToByteArray(), "audio/raw", "m4a")
        assertEquals("FLAC", resolved.canonical)
        assertEquals(CodecGroup.LOSSLESS, resolved.group)
        assertEquals(DetectionSource.MAGIC_BYTES, resolved.detectedBy)
    }

    @Test
    fun wavRawMimeIsLossless() {
        val header = ByteArray(12).also {
            "RIFF".encodeToByteArray().copyInto(it, 0)
            "WAVE".encodeToByteArray().copyInto(it, 8)
        }
        val resolved = CodecDetector.resolve(header, "audio/raw", "wav")
        assertEquals("WAV", resolved.canonical)
        assertEquals(CodecGroup.LOSSLESS, resolved.group)
    }

    @Test
    fun mp3MagicWinsOverExtension() {
        val resolved = CodecDetector.resolve("ID3\u0004".encodeToByteArray(), "audio/raw", "flac")
        assertEquals("MP3", resolved.canonical)
        assertEquals(CodecGroup.LOSSY, resolved.group)
    }

    @Test
    fun mp4SampleEntrySeparatesAlacAndMp4a() {
        val alac = "....ftyp....stsd....alac".encodeToByteArray()
        val aac = "....ftyp....stsd....mp4a".encodeToByteArray()
        assertEquals("ALAC", CodecDetector.resolve(alac, "audio/mp4a-latm", "m4a").canonical)
        assertEquals(CodecGroup.LOSSLESS, CodecDetector.resolve(alac, "audio/mp4a-latm", "m4a").group)
        assertEquals("AAC", CodecDetector.resolve(aac, "audio/mp4a-latm", "m4a").canonical)
        assertEquals(CodecGroup.LOSSY, CodecDetector.resolve(aac, "audio/mp4a-latm", "m4a").group)
    }

    @Test
    fun extensionFallbackResolvesLosslessWavWhenMimeIsRaw() {
        val resolved = CodecDetector.resolve(byteArrayOf(), "audio/raw", "wav")
        assertEquals("WAV", resolved.canonical)
        assertEquals(CodecGroup.LOSSLESS, resolved.group)
        assertEquals(DetectionSource.EXTENSION, resolved.detectedBy)
    }

    @Test
    fun unknownKeepsUnknownAndDoesNotPrintRawMimeAsCanonical() {
        val resolved = CodecDetector.resolve(byteArrayOf(0x01, 0x02, 0x03), "audio/raw", null)
        assertEquals("UNKNOWN", resolved.canonical)
        assertEquals(CodecGroup.UNKNOWN, resolved.group)
    }
}
