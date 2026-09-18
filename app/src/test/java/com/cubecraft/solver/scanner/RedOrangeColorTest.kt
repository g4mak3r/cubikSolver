package com.cubecraft.solver.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RedOrangeColorTest {
    @Test
    fun flameOrangeIsRecognizedAsOrange() {
        val samples = listOf(
            RgbColor(255, 33, 0),
            RgbColor(255, 85, 5),
            RgbColor(255, 100, 8),
            RgbColor(255, 132, 0)
        )

        samples.forEach {
            assertEquals(StickerGuess.ORANGE, recognitionGuess(it).first)
        }
    }

    @Test
    fun cubeRedsStayRed() {
        val samples = listOf(
            RgbColor(195, 28, 37),
            RgbColor(225, 28, 35),
            RgbColor(235, 25, 35),
            RgbColor(255, 0, 0)
        )

        samples.forEach {
            assertEquals(StickerGuess.RED, recognitionGuess(it).first)
        }
    }

    @Test
    fun canonicalDisplayOrangeIsVisuallyDistinctFromRed() {
        val orange = idealDisplayRgb(StickerGuess.ORANGE)
        val red = idealDisplayRgb(StickerGuess.RED)

        assertTrue(orange.g - red.g > 70)
        assertTrue(orange.r >= red.r)
        assertTrue(orange.b < red.b)
    }
}
