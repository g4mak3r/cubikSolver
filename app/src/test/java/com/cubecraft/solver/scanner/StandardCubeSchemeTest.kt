package com.cubecraft.solver.scanner

import com.cubecraft.solver.model.Face
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StandardCubeSchemeTest {
    @Test
    fun predictsRemainingCentersFromFrontAndRight() {
        val confirmed = mapOf(
            Face.F to StickerGuess.GREEN,
            Face.R to StickerGuess.RED
        )

        assertEquals(StickerGuess.BLUE, StandardCubeScheme.expected(confirmed, Face.B))
        assertEquals(StickerGuess.ORANGE, StandardCubeScheme.expected(confirmed, Face.L))
        assertEquals(StickerGuess.WHITE, StandardCubeScheme.expected(confirmed, Face.U))
        assertEquals(StickerGuess.YELLOW, StandardCubeScheme.expected(confirmed, Face.D))
    }

    @Test
    fun refusesPredictionForOppositeFirstPair() {
        val confirmed = mapOf(
            Face.F to StickerGuess.GREEN,
            Face.R to StickerGuess.BLUE
        )

        assertNull(StandardCubeScheme.expected(confirmed, Face.U))
    }
}
