package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Move
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Min2PhaseValidationTest {
    private val solver = Min2PhaseSolver()

    @Test
    fun solvedCubeIsValid() {
        assertNull(solver.validate(CubeState(3)))
    }

    @Test
    fun legalScrambleFromOurGeometryIsValidToMin2Phase() {
        val cube = CubeState(3)
        cube.applyAll(Move.parseAlgorithm("R U R' U' F2 L D2 B' R2"))
        assertNull(solver.validate(cube))
    }

    @Test
    fun nativeVerifierRejectsMalformedFaceletsWithoutLaunchingSearch() {
        val malformed = "U".repeat(54)
        assertTrue(solver.verifyFacelets(malformed) != 0)
    }
}
