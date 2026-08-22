package com.youneko.rate

import androidx.compose.ui.graphics.Color
import com.youneko.rate.data.artwork.CoverPalette
import com.youneko.rate.data.artwork.adjustHslLightness
import com.youneko.rate.data.artwork.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverPaletteRegressionTest {
    @Test
    fun contrastRatiosForThreeRepresentativeCoverColorsAreComputedExactly() {
        val ratios = listOf(
            contrastRatio(Color(0xFF101820), Color.White),
            contrastRatio(Color(0xFFF2C14E), Color.Black),
            contrastRatio(Color(0xFF6B2D5C), Color.White),
        )
        assertEquals(17.89, ratios[0], 0.02)
        assertEquals(12.51, ratios[1], 0.02)
        assertEquals(9.74, ratios[2], 0.02)
        assertTrue(ratios.all { it >= 4.5 })
    }

    @Test
    fun hslLightnessAdjustmentMovesColorWithoutUsingARepeatingAnimation() {
        val original = Color(0xFF777777)
        val lighter = adjustHslLightness(original, 0.20f)
        assertTrue(lighter.red > original.red)
        assertTrue(contrastRatio(lighter, Color.Black) > contrastRatio(original, Color.Black))
    }

    @Test
    fun swatchesAreOrderedAndLimitedToSixNonNullColors() {
        val palette = CoverPalette(
            dominant = Color.Red,
            vibrant = Color.Green,
            darkVibrant = Color.Blue,
            muted = Color.Gray,
            darkMuted = Color.Black,
            lightVibrant = Color.White,
            onDominant = Color.White,
        )
        assertEquals(6, palette.swatches.size)
        assertEquals(Color.Red, palette.swatches[0])
        assertEquals(Color.Green, palette.swatches[1])
        assertEquals(Color.Blue, palette.swatches[2])
        assertEquals(Color.White, palette.swatches[3])
        assertEquals(Color.Gray, palette.swatches[4])
        assertEquals(Color.Black, palette.swatches[5])
    }
}
