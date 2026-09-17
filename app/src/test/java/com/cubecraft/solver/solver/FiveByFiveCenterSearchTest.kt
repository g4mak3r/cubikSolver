package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Move
import org.junit.Assert.assertTrue
import org.junit.Test

class FiveByFiveCenterSearchTest {
    @Test fun solvedCentersReturnImmediately() {
        val result = FiveByFiveCenterSearch(maxExpanded = 100).solve(CubeState(5))
        assertTrue(result is FiveByFiveCenterSearch.Result.Success)
        val success = result as FiveByFiveCenterSearch.Result.Success
        assertTrue(success.moves.isEmpty())
        assertTrue(success.expanded == 0)
    }

    @Test fun solvesSingleInnerSliceFromStickersOnly() {
        val cube = CubeState(5)
        cube.applyAll(Move.parseAlgorithm("Rw R'"))
        assertTrue(!FiveByFiveTopology.centersSolved(cube))

        val result = FiveByFiveCenterSearch(maxExpanded = 10_000).solve(cube)
        assertTrue(result is FiveByFiveCenterSearch.Result.Success)
        val success = result as FiveByFiveCenterSearch.Result.Success

        val replay = cube.deepCopy()
        replay.applyAll(success.moves)
        assertTrue(FiveByFiveTopology.centersSolved(replay))
    }

    @Test fun solvesTwoIndependentInnerSlicesFromStickersOnly() {
        val cube = CubeState(5)
        cube.applyAll(Move.parseAlgorithm("Rw R' Uw U'"))
        assertTrue(!FiveByFiveTopology.centersSolved(cube))

        val result = FiveByFiveCenterSearch(maxExpanded = 25_000).solve(cube)
        assertTrue(result is FiveByFiveCenterSearch.Result.Success)
        val success = result as FiveByFiveCenterSearch.Result.Success

        val replay = cube.deepCopy()
        replay.applyAll(success.moves)
        assertTrue(FiveByFiveTopology.centersSolved(replay))
    }
}
