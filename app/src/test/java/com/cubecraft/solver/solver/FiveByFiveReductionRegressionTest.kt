package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Move
import org.junit.Assert.assertTrue
import org.junit.Test

class FiveByFiveReductionRegressionTest {
    private val scrambles = listOf(
        "Rw U2 Fw' R2 Dw Lw2 B' Uw Rw' F2 D Lw' U2 Bw R' Dw2 Fw U' L2 Rw B2 Uw' F R2 Dw' Lw U Fw2 D2 Rw'",
        "Uw Rw2 F' Dw L2 Bw' U Rw F2 Uw2 Lw' D B2 R' Fw U2 Dw' L Rw2 B Uw F' Dw2 R2 Lw F2 U' Bw2 D Rw",
        "Rw F2 Uw' Lw D2 Bw R' Dw2 Fw' U L2 Rw2 B' Uw F Dw' Lw2 U2 R Fw B2 Dw Rw' U' L F2 Uw2 Bw' D R2"
    )

    @Test fun reducesLongStickerOnlyStatesWithinBoundedBudget() {
        scrambles.forEach { scramble ->
            val state = CubeState(5)
            state.applyAll(Move.parseAlgorithm(scramble))

            val result = FiveByFiveMacroReduction.solve(
                state = state.deepCopy(),
                budgetMillis = 12_000L
            )

            assertTrue(
                "$scramble -> ${result.diagnostic}",
                result.centresSolved && result.edgesPaired
            )
        }
    }
}
