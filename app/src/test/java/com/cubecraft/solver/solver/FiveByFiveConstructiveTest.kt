package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.model.Move
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException
import kotlin.random.Random

class FiveByFiveConstructiveTest {
    @Test fun solvesOneHundredRandomStickerOnlyStates() {
        val solver = FiveByFiveSolver()
        val random = Random(5552026)
        val elapsed = ArrayList<Long>()
        val lengths = ArrayList<Int>()
        repeat(100) { sample ->
            val cube = CubeState(5)
            repeat(60) {
                cube.apply(Move(Face.entries[random.nextInt(6)], random.nextInt(1, 3), random.nextInt(1, 4)))
            }
            val original = cube.toUrfdlbString()
            val start = System.nanoTime()
            // Reload the stickers to rule out any scramble-history shortcut.
            val scan = CubeState(5).also { it.loadFaces(cube.snapshot()) }
            val result = solver.solve(scan)
            elapsed += (System.nanoTime() - start) / 1_000_000
            assertTrue("sample $sample: $result", result is SolverResult.Success)
            val moves = (result as SolverResult.Success).moves
            lengths += moves.size
            cube.applyAll(moves)
            assertTrue("sample $sample replay failed", cube.isSolved())
            assertEquals("solver changed input", original, scan.toUrfdlbString())
        }
        println("5x5 100x60 turns: firstSample=${elapsed.first()}ms, median=${elapsed.sorted()[50]}ms, max=${elapsed.max()}ms; moves=${lengths.min()}..${lengths.max()}")
    }

    @Test fun rejectsImpossibleSkeletonBeforeReduction() {
        val cube = CubeState(5)
        swap(cube, Face.U, 4, 2, Face.F, 0, 2)
        val result = FiveByFiveSolver().solve(cube)
        assertTrue("$result", result is SolverResult.Invalid)
    }

    @Test fun rejectsCentersExchangedBetweenOrbits() {
        val cube = CubeState(5)
        swap(cube, Face.U, 1, 1, Face.R, 1, 2)
        val result = FiveByFiveSolver().solve(cube)
        assertTrue("$result", result is SolverResult.Invalid)
    }

    @Test fun rejectsSingleFlippedWing() {
        val cube = CubeState(5)
        swap(cube, Face.U, 4, 1, Face.F, 0, 1)
        val result = FiveByFiveSolver().solve(cube)
        assertTrue("$result", result is SolverResult.Invalid)
        assertFalse(CubeValidator().validate(cube).ok)
    }

    @Test fun solvesTwoSwappedColorsWithinEachCenterOrbit() {
        for (column in 1..2) {
            val cube = CubeState(5)
            swap(cube, Face.U, 1, column, Face.R, 1, column)
            val result = FiveByFiveSolver().solve(cube)
            assertTrue("$result", result is SolverResult.Success)
            cube.applyAll((result as SolverResult.Success).moves)
            assertTrue(cube.isSolved())
        }
    }

    @Test fun supportsCancellationDuringReduction() {
        val cube = CubeState(5).also { it.applyAll(Move.parseAlgorithm("Rw Uw Fw Lw Dw Bw")) }
        var checks = 0
        try {
            FiveByFiveSolver().solve(cube) { if (++checks == 5) throw CancellationException() }
            fail("cancellation was swallowed")
        } catch (_: CancellationException) {
            assertEquals(5, checks)
        }
    }

    @Test fun historyShortcutFallsBackWhenHistoryDoesNotMatch() {
        val cube = CubeState(5).also { it.applyAll(Move.parseAlgorithm("Rw Uw Fw' R2")) }
        val result = FiveByFiveSolver().solveKnownHistory(cube, Move.parseAlgorithm("U R"))
        assertTrue("$result", result is SolverResult.Success)
        cube.applyAll((result as SolverResult.Success).moves)
        assertTrue(cube.isSolved())
    }

    private fun swap(cube: CubeState, a: Face, ar: Int, ac: Int, b: Face, br: Int, bc: Int) {
        val ak = cube.keyFromFaceCell(a, ar, ac)
        val bk = cube.keyFromFaceCell(b, br, bc)
        val av = cube.stickerColor(ak)!!
        val bv = cube.stickerColor(bk)!!
        cube.setStickerColor(ak, bv)
        cube.setStickerColor(bk, av)
    }
}
