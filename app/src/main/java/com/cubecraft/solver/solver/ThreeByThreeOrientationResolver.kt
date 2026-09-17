package com.cubecraft.solver.solver

import com.cubecraft.solver.model.Face
import cs.min2phase.Search

/**
 * Recovers per-face scan rotation from the physically constrained odd-cube 3x3 skeleton.
 *
 * The first FRONT scan defines our visual reference and stays at rotation 0. The remaining five
 * face grids can each be rotated by 0/90/180/270 degrees. That is only 4^5 = 1024 candidates.
 *
 * For a 5x5 we do not ask min2phase to understand 150 stickers. Instead we take the four corners,
 * four middle edge stickers and fixed centre from every face. Those 54 stickers form the same
 * physical 3x3 skeleton, so the verifier can recover the orientation of the complete 5x5 face and
 * we then rotate all 25 stickers together.
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
        val n = detectSize(input)
            ?: return Result(input, Face.entries.associateWith { 0 }, false)

        val cache = Face.entries.associateWith { face ->
            List(4) { turns -> rotate(input.getValue(face), n, turns) }
        }
        val verifier = Search()
        val variableFaces = listOf(Face.U, Face.R, Face.B, Face.L, Face.D)

        var bestFaces: Map<Face, List<Face>>? = null
        var bestRotations: Map<Face, Int>? = null
        var bestCost = Int.MAX_VALUE

        search@ for (u in 0..3) for (r in 0..3) for (b in 0..3) for (l in 0..3) for (d in 0..3) {
            val turns = intArrayOf(u, r, b, l, d)
            val cost = turns.sumOf(::rotationCost)
            if (cost > bestCost) continue

            val rotations = mutableMapOf<Face, Int>(Face.F to 0)
            variableFaces.forEachIndexed { i, face -> rotations[face] = turns[i] }
            val candidate = Face.entries.associateWith { face ->
                cache.getValue(face)[rotations.getValue(face)]
            }

            val code = try {
                verifier.verify(toSkeletonFacelets(candidate, n))
            } catch (_: Throwable) {
                return Result(input, Face.entries.associateWith { 0 }, false)
            }
            if (code == 0 && cost < bestCost) {
                bestCost = cost
                bestFaces = candidate
                bestRotations = rotations.toMap()
                if (cost == 0) break@search
            }
        }

        return if (bestFaces != null && bestRotations != null) {
            Result(bestFaces!!, bestRotations!!, true)
        } else {
            Result(input, Face.entries.associateWith { 0 }, false)
        }
    }

    private fun detectSize(input: Map<Face, List<Face>>): Int? {
        val count = input[Face.F]?.size ?: return null
        val n = when (count) {
            9 -> 3
            25 -> 5
            else -> return null
        }
        return n.takeIf { Face.entries.all { face -> input[face]?.size == n * n } }
    }

    private fun rotate(src: List<Face>, n: Int, quarterTurns: Int): List<Face> {
        var out = src
        repeat((quarterTurns % 4 + 4) % 4) {
            val previous = out
            out = List(n * n) { idx ->
                val row = idx / n
                val col = idx % n
                previous[(n - 1 - col) * n + row]
            }
        }
        return out
    }

    /** URFDLB serialization of corners + middle edges + fixed centres only. */
    private fun toSkeletonFacelets(faces: Map<Face, List<Face>>, n: Int): String {
        val pick = intArrayOf(0, n / 2, n - 1)
        return buildString(54) {
            listOf(Face.U, Face.R, Face.F, Face.D, Face.L, Face.B).forEach { face ->
                val src = faces.getValue(face)
                for (row in pick) for (col in pick) append(src[row * n + col].symbol)
            }
        }
    }

    private fun rotationCost(turns: Int): Int = when (turns and 3) {
        0 -> 0
        2 -> 2
        else -> 1
    }
}
