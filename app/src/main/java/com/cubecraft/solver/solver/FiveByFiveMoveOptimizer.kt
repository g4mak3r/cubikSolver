package com.cubecraft.solver.solver

import com.cubecraft.solver.model.Face
import com.cubecraft.solver.model.Move
import java.util.ArrayDeque

internal object FiveByFiveMoveOptimizer {
    private enum class Axis { X, Y, Z }

    private val tables = Axis.entries.associateWith(::buildTable)

    fun optimize(moves: List<Move>): List<Move> {
        if (moves.size < 2) return moves

        var current = moves
        while (true) {
            val next = optimizeRuns(current)
            if (next.size >= current.size) return next
            current = next
        }
    }

    private fun optimizeRuns(moves: List<Move>): List<Move> {
        val out = ArrayList<Move>(moves.size)
        var start = 0

        while (start < moves.size) {
            val axis = axisOf(moves[start].face)
            var end = start + 1
            while (end < moves.size && axisOf(moves[end].face) == axis) end++

            val code = encodeRun(moves, start, end, axis)
            val best = tables.getValue(axis)[code]
            if (best != null) out += best else out += moves.subList(start, end)
            start = end
        }

        return out
    }

    private fun buildTable(axis: Axis): Array<List<Move>?> {
        val table = arrayOfNulls<List<Move>>(1024)
        val queue = ArrayDeque<Int>()
        table[0] = emptyList()
        queue.addLast(0)

        val generators = generators(axis)

        while (queue.isNotEmpty()) {
            val code = queue.removeFirst()
            val path = table[code] ?: continue

            for (move in generators) {
                val next = applyToCode(code, move, axis)
                if (table[next] != null) continue
                table[next] = path + move
                queue.addLast(next)
            }
        }

        return table
    }

    private fun generators(axis: Axis): List<Move> {
        val faces = when (axis) {
            Axis.X -> listOf(Face.L, Face.R)
            Axis.Y -> listOf(Face.D, Face.U)
            Axis.Z -> listOf(Face.B, Face.F)
        }

        return buildList(18) {
            for (face in faces) {
                for (width in 1..3) {
                    for (turns in 1..3) {
                        add(Move(face, width, turns))
                    }
                }
            }
        }
    }

    private fun encodeRun(
        moves: List<Move>,
        start: Int,
        end: Int,
        axis: Axis
    ): Int {
        var code = 0
        for (i in start until end) {
            code = applyToCode(code, moves[i], axis)
        }
        return code
    }

    private fun applyToCode(code: Int, move: Move, axis: Axis): Int {
        val values = decode(code)
        val positive = when (axis) {
            Axis.X -> move.face == Face.R
            Axis.Y -> move.face == Face.U
            Axis.Z -> move.face == Face.F
        }
        val amount = ((if (positive) -1 else 1) * move.quarterTurns).mod(4)

        if (positive) {
            for (layer in 5 - move.width until 5) {
                values[layer] = (values[layer] + amount) and 3
            }
        } else {
            for (layer in 0 until move.width) {
                values[layer] = (values[layer] + amount) and 3
            }
        }

        return encode(values)
    }

    private fun encode(values: IntArray): Int {
        var code = 0
        var scale = 1
        for (value in values) {
            code += (value and 3) * scale
            scale *= 4
        }
        return code
    }

    private fun decode(code: Int): IntArray {
        var value = code
        return IntArray(5) {
            val digit = value and 3
            value = value ushr 2
            digit
        }
    }

    private fun axisOf(face: Face): Axis = when (face) {
        Face.L, Face.R -> Axis.X
        Face.D, Face.U -> Axis.Y
        Face.B, Face.F -> Axis.Z
    }
}
