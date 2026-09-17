package com.cubecraft.solver.scanner

import org.junit.Assert.assertTrue
import org.junit.Test

class RedOrangeColorTest {
    @Test
    fun flameOrangeStaysSeparateFromRed() {
        val orange = RgbColor(0xFF, 0x21, 0x00)
        val red = RgbColor(0xC3, 0x1C, 0x25)
        val of = orange.features()
        val rf = red.features()

        // Approximate photo-picked colors from the target physical cube.
        assertTrue("orange must leave the red live-hint bucket", of.hue >= 11.0 && of.hue < 43.0)
        assertTrue("red must stay in the red live-hint bucket", rf.hue >= 348.0 || rf.hue < 11.0)
        assertTrue("orange should sit on the warm side of the red/orange axis", of.redOrangeAxis > .035)
        assertTrue("red should sit on the cool/neutral side of the red/orange axis", rf.redOrangeAxis < .012)
    }

    @Test
    fun finalDistanceSeparatesThePairEvenAtEqualLab() {
        val lab = LabColor(128.0, 128.0, 128.0)
        val orange = ColorSample(lab, RgbColor(0xFF, 0x21, 0x00))
        val red = ColorSample(lab, RgbColor(0xC3, 0x1C, 0x25))

        assertTrue(cubeColorDistance(orange, orange) < cubeColorDistance(orange, red))
        assertTrue(cubeColorDistance(red, red) < cubeColorDistance(red, orange))
    }
}
