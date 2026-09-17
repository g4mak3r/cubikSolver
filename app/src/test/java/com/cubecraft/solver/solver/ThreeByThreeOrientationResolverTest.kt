package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.model.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreeByThreeOrientationResolverTest {
    @Test
    fun rotatedScanFacesCanBeRecoveredIntoALegalCube() {
        val cube = CubeState(3)
        cube.applyAll(Move.parseAlgorithm("R U R' U' F2 L D2 B' R2"))
        val original = cube.snapshot()

        val scanned = original.toMutableMap().apply {
            put(Face.U, rotate(getValue(Face.U), 3, 1))
            put(Face.R, rotate(getValue(Face.R), 3, 3))
            put(Face.B, rotate(getValue(Face.B), 3, 2))
            put(Face.D, rotate(getValue(Face.D), 3, 1))
        }

        val resolved = ThreeByThreeOrientationResolver.resolve(scanned)
        assertTrue(resolved.verified)

        val rebuilt = CubeState(3).also { it.loadFaces(resolved.faces) }
        assertNull(Min2PhaseSolver().validate(rebuilt))
    }

    @Test
    fun rotatedFiveByFiveScanFacesRecoverTheWhole25StickerFaces() {
        val cube = CubeState(5)
        cube.applyAll(Move.parseAlgorithm("Rw U F2 Lw' D R B2 Uw'"))
        val original = cube.snapshot()

        val scanned = original.toMutableMap().apply {
            put(Face.U, rotate(getValue(Face.U), 5, 1))
            put(Face.R, rotate(getValue(Face.R), 5, 3))
            put(Face.B, rotate(getValue(Face.B), 5, 2))
            put(Face.L, rotate(getValue(Face.L), 5, 1))
            put(Face.D, rotate(getValue(Face.D), 5, 3))
        }

        val resolved = ThreeByThreeOrientationResolver.resolve(scanned)
        assertTrue(resolved.verified)
        assertTrue(resolved.changed)

        // The front face is our scan anchor, so this scramble has a unique minimum-cost recovery.
        // Comparing all 150 stickers catches the dangerous case where only the 3x3 skeleton was
        // rotated back but the 5x5 wings/centres were left in camera orientation.
        assertEquals(original, resolved.faces)

        val rebuilt = CubeState(5).also { it.loadFaces(resolved.faces) }
        assertNull(Min2PhaseSolver().validate(rebuilt.reducedSkeleton3x3()))
    }

    private fun rotate(src: List<Face>, n: Int, turns: Int): List<Face> {
        var out = src
        repeat(turns) {
            val old = out
            out = List(n * n) { idx ->
                val r = idx / n
                val c = idx % n
                old[(n - 1 - c) * n + r]
            }
        }
        return out
    }
}
