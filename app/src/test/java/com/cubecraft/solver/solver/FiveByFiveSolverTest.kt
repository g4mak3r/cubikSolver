package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Move
import org.junit.Assert.assertTrue
import org.junit.Test

class FiveByFiveSolverTest {
    private val solver = FiveByFiveSolver()

    @Test fun solvesPureInnerSliceFromStateOnly() {
        assertStickerStateSolve("Rw R'")
    }

    @Test fun solvesTwoAxisInnerSliceStateOnly() {
        assertStickerStateSolve("Rw R' Uw U'")
    }

    @Test fun solvesMixedOuterAndWideScrambleFromStateOnly() {
        assertStickerStateSolve("Rw U F2 Lw' D R B2 Uw'")
    }

    @Test fun solvesLongerMixedWideScrambleFromStateOnly() {
        assertStickerStateSolve("Rw U2 Fw' R2 D Lw B U' Rw2 F")
    }

    @Test fun solvesRandomLikeWideScrambleFromStateOnly() {
        assertStickerStateSolve("Rw U Fw2 D' Lw B2 Uw R2 F' Dw L2 Bw' U2 Rw'")
    }

    @Test fun solvesLongRandomLikeStateOnly() {
        assertStickerStateSolve("Rw U Fw2 D' Lw B2 Uw R2 F' Dw L2 Bw' U2 Rw' F2 Uw2 L' Dw Rw2 B U' Fw D2 Lw' R")
    }

    private fun assertStickerStateSolve(scramble: String) {
        val scannedState = CubeState(5)
        scannedState.applyAll(Move.parseAlgorithm(scramble))

        // Deliberately do not pass the scramble/history to the solver. Its only input is the final
        // 150-sticker state, matching a camera scan.
        val result = solver.solve(scannedState.deepCopy())
        assertTrue("$scramble -> $result", result is SolverResult.Success)

        val moves = (result as SolverResult.Success).moves
        val replay = scannedState.deepCopy()
        replay.applyAll(moves)
        assertTrue("solution did not replay for $scramble: ${moves.joinToString(" ") { it.notation() }}", replay.isSolved())
    }
}
