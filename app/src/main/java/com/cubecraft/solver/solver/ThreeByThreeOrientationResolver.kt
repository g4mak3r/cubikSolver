package com.cubecraft.solver.solver

import com.cubecraft.solver.model.Face
import cs.min2phase.Search

/**
 * Recovers per-face scan rotation for a 3x3 using min2phase's native physical-state verifier.
 *
 * The first FRONT scan defines our visual reference and stays at rotation 0. The remaining five
 * face grids can each be rotated by 0/90/180/270 degrees. That is only 4^5 = 1024 candidates,
 * and Search.verify() is cheap because it does not initialize solver pruning tables.
 */
object ThreeByThreeOrientationResolver {
    data class Result(
        val faces: Map<Face, List<Face>>,
        val rotations: Map<Face, Int>,
        val verified: Boolean
    ) {
        val changed: Boolean get() = rotations.values.any { it != 0 }
    }

    fun resolve(input: Map<Face, List<Face>>): Result {
        val validShape = Face.entries.all { input[it]?.size == 9 }
        if (!validShape) return Result(input, Face.entries.associateWith { 0 }, false)

        val cache = Face.entries.associateWith { face ->
            List(4) { turns -> rotate(input.getValue(face), turns) }
        }
        val verifier = Search()
        val variableFaces = listOf(Face.U, Face.R, Face.B, Face.L, Face.D)

        var bestFaces: Map<Face, List<Face>>? = null
        var bestRotations: Map<Face, Int>? = null
        var bestCost = Int.MAX_VALUE

        for (u in 0..3) for (r in 0..3) for (b in 0..3) for (l in 0..3) for (d in 0..3) {
            val turns = intArrayOf(u, r, b, l, d)
            val cost = turns.sumOf(::rotationCost)
            if (cost > bestCost) continue

            val rotations = mutableMapOf<Face, Int>(Face.F to 0)
            variableFaces.forEachIndexed { i, face -> rotations[face] = turns[i] }
            val candidate = Face.entries.associateWith { face ->
                cache.getValue(face)[rotations.getValue(face)]
            }

            val code = try {
                verifier.verify(toFacelets(candidate))
            } catch (_: Throwable) {
                return Result(input, Face.entries.associateWith { 0 }, false)
            }
            if (code == 0 && cost < bestCost) {
                bestCost = cost
                bestFaces = candidate
                bestRotations = rotations.toMap()
                if (cost == 0) break
            }
        }

        return if (bestFaces != null && bestRotations != null) {
            Result(bestFaces!!, bestRotations!!, true)
        } else {
            Result(input, Face.entries.associateWith { 0 }, false)
        }
    }

    private fun rotate(src: List<Face>, quarterTurns: Int): List<Face> {
        var out = src
        repeat((quarterTurns % 4 + 4) % 4) {
            val previous = out
            out = List(9) { idx ->
                val row = idx / 3
                val col = idx % 3
                previous[(2 - col) * 3 + row]
            }
        }
        return out
    }

    private fun toFacelets(faces: Map<Face, List<Face>>): String = buildString(54) {
        listOf(Face.U, Face.R, Face.F, Face.D, Face.L, Face.B).forEach { face ->
            faces.getValue(face).forEach { append(it.symbol) }
        }
    }

    private fun rotationCost(turns: Int): Int = when (turns and 3) {
        0 -> 0
        2 -> 2
        else -> 1
    }
}
