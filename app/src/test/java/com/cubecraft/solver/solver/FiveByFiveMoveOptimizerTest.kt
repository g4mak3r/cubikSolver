package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FiveByFiveMoveOptimizerTest {
    @Test
    fun collapsesCommutingAxisTurns() {
        val source = Move.parseAlgorithm("R L R' 3Rw Rw' 3Rw' Rw")
        val optimized = FiveByFiveMoveOptimizer.optimize(source)

        assertTrue(optimized.size < source.size)
        assertEquivalent(source, optimized)
    }

    @Test
    fun preservesMixedSequenceExactly() {
        val source = Move.parseAlgorithm(
            "Rw R' 3Rw2 Rw2 U F U' F' Lw L Lw' R2 D Dw D' Dw'"
        )
        val optimized = FiveByFiveMoveOptimizer.optimize(source)

        assertTrue(optimized.size <= source.size)
        assertEquivalent(source, optimized)
    }

    private fun assertEquivalent(a: List<Move>, b: List<Move>) {
        val left = CubeState(5).also { it.applyAll(a) }
        val right = CubeState(5).also { it.applyAll(b) }
        assertEquals(left.toUrfdlbString(), right.toUrfdlbString())
    }
}
