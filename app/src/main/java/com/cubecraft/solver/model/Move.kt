package com.cubecraft.solver.model

data class Move(
    val face: Face,
    val width: Int = 1,
    /** 1=clockwise, 2=half turn, 3=counter-clockwise, viewed straight at the face. */
    val quarterTurns: Int = 1
) {
    init { require(width >= 1); require(quarterTurns in 1..3) }
    fun inverse() = copy(quarterTurns = if (quarterTurns == 2) 2 else 4 - quarterTurns)

    fun notation(): String {
        val prefix = if (width <= 2) "" else width.toString()
        val wide = if (width >= 2) "w" else ""
        val suffix = when (quarterTurns) { 2 -> "2"; 3 -> "'"; else -> "" }
        return "$prefix${face.symbol}$wide$suffix"
    }

    companion object {
        private val re = Regex("^(\\d+)?([URFDLBurfdlb])([wW])?([2']?)$")
        fun parse(token: String): Move? {
            val m = re.matchEntire(token.trim()) ?: return null
            val explicit = m.groupValues[1].toIntOrNull()
            val face = Face.fromSymbol(m.groupValues[2][0])
            val wide = m.groupValues[3].isNotEmpty()
            val width = explicit ?: if (wide) 2 else 1
            val turns = when (m.groupValues[4]) { "2" -> 2; "'" -> 3; else -> 1 }
            return Move(face, width, turns)
        }

        fun parseAlgorithm(text: String): List<Move> =
            text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.mapNotNull(::parse)
    }
}
