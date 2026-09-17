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
}
