package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.model.Move
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
            put(Face.U, rotate(getValue(Face.U), 1))
            put(Face.R, rotate(getValue(Face.R), 3))
            put(Face.B, rotate(getValue(Face.B), 2))
            put(Face.D, rotate(getValue(Face.D), 1))
        }

        val resolved = ThreeByThreeOrientationResolver.resolve(scanned)
        assertTrue(resolved.verified)

        val rebuilt = CubeState(3).also { it.loadFaces(resolved.faces) }
        assertNull(Min2PhaseSolver().validate(rebuilt))
    }

    private fun rotate(src: List<Face>, turns: Int): List<Face> {
        var out = src
        repeat(turns) {
            val old = out
            out = List(9) { idx ->
                val r = idx / 3
                val c = idx % 3
                old[(2 - c) * 3 + r]
            }
        }
        return out
    }
}
