package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Move

sealed interface SolverResult {
    data class Success(val moves: List<Move>) : SolverResult
    data class Invalid(val reason: String) : SolverResult
    data class Unavailable(val reason: String) : SolverResult
}

interface CubeSolver { fun solve(state: CubeState): SolverResult }
