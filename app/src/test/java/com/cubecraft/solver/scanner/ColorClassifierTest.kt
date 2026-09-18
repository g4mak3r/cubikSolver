package com.cubecraft.solver.scanner

import org.junit.Assert.assertTrue
import org.junit.Test

class ColorClassifierTest {
    private fun sample(rgb: RgbColor) = ColorSample(LabColor(128.0, 128.0, 128.0), rgb)

    @Test
    fun warmWhiteStaysCloserToWhiteThanYellow() {
        val warmWhite = sample(RgbColor(235, 220, 190))
        val whiteRef = sample(RgbColor(242, 235, 218))
        val yellowRef = sample(RgbColor(250, 210, 25))
        assertTrue(cubeColorDistance(warmWhite, whiteRef) < cubeColorDistance(warmWhite, yellowRef))
    }

    @Test
    fun flameOrangeStaysCloserToOrangeThanRed() {
        val orange = sample(RgbColor(255, 85, 5))
        val orangeRef = sample(RgbColor(255, 100, 8))
        val redRef = sample(RgbColor(235, 25, 35))
        assertTrue(cubeColorDistance(orange, orangeRef) < cubeColorDistance(orange, redRef))
    }

    @Test
    fun brightRedStaysCloserToRedThanOrange() {
        val red = sample(RgbColor(235, 25, 35))
        val redRef = sample(RgbColor(225, 28, 35))
        val orangeRef = sample(RgbColor(255, 95, 5))
        assertTrue(cubeColorDistance(red, redRef) < cubeColorDistance(red, orangeRef))
    }


    @Test
    fun balancedClassifierAlwaysExposesAllSixCanonicalColors() {
        val physical = mapOf(
            com.cubecraft.solver.model.Face.U to StickerGuess.WHITE,
            com.cubecraft.solver.model.Face.R to StickerGuess.RED,
            com.cubecraft.solver.model.Face.F to StickerGuess.GREEN,
            com.cubecraft.solver.model.Face.D to StickerGuess.YELLOW,
            com.cubecraft.solver.model.Face.L to StickerGuess.ORANGE,
            com.cubecraft.solver.model.Face.B to StickerGuess.BLUE
        )
        val captures = com.cubecraft.solver.model.Face.entries.map { face ->
            val color = physical.getValue(face)
            val canonical = colorSample(idealDisplayRgb(color))
            CapturedFace(
                face = face,
                samples = List(9) { canonical },
                quality = 1f,
                guesses = List(9) { StickerGuess.RED }
            )
        }

        val result = BalancedClassifier.classify(captures, 3)

        assertTrue(result.colorFaces.keys.containsAll(canonicalStickerGuesses))
        assertTrue(result.colorFaces.size == 6)
        assertTrue(
            result.palette.values.toSet() ==
                canonicalStickerGuesses.map(::idealDisplayRgb).toSet()
        )
    }
}
