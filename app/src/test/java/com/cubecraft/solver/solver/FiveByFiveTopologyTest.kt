package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Move
import org.junit.Assert.*
import org.junit.Test

class FiveByFiveTopologyTest {
    @Test fun solvedCubeHasExpectedReductionPieces() {
        val cube = CubeState(5)
        val slots = FiveByFiveTopology.edgeSlots(cube)

        assertEquals(48, FiveByFiveTopology.solvedMovableCenterCount(cube))
        assertTrue(FiveByFiveTopology.centersSolved(cube))
        assertEquals(12, slots.size)
        assertTrue(slots.all { it.cubies.size == 3 })
        assertTrue(slots.all { it.wings.size == 2 })
        assertEquals(12, FiveByFiveTopology.middleEdges(cube).size)
        assertEquals(24, FiveByFiveTopology.wings(cube).size)
        assertEquals(12, FiveByFiveTopology.pairedEdgeCount(cube))
        assertTrue(FiveByFiveTopology.edgesPaired(cube))
    }

    @Test fun topologySurvivesWideScrambleWithoutHistory() {
        val cube = CubeState(5)
        val scramble = Move.parseAlgorithm("Rw U F2 Lw' D Bw R2 Uw'")
        cube.applyAll(scramble)

        val slots = FiveByFiveTopology.edgeSlots(cube)
        assertEquals(12, slots.size)
        assertTrue(slots.all { it.cubies.size == 3 })
        assertEquals(12, FiveByFiveTopology.middleEdges(cube).size)
        assertEquals(24, FiveByFiveTopology.wings(cube).size)
        assertFalse(FiveByFiveTopology.centersSolved(cube))
        assertTrue(FiveByFiveTopology.pairedEdgeCount(cube) < 12)
    }

    @Test fun inverseWideScrambleRestoresReductionGoals() {
        val cube = CubeState(5)
        val scramble = Move.parseAlgorithm("Rw U F2 Lw' D Bw R2 Uw'")
        cube.applyAll(scramble)
        cube.applyAll(scramble.asReversed().map { it.inverse() })

        assertTrue(cube.isSolved())
        assertTrue(FiveByFiveTopology.centersSolved(cube))
        assertTrue(FiveByFiveTopology.edgesPaired(cube))
    }
}
