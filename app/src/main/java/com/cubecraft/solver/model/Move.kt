package com.cubecraft.solver.model

data class Move(
    val face: Face,
    val width: Int = 1,
    val quarterTurns: Int = 1,
    val depth: Int = 1
) {
    init {
        require(width >= 1)
        require(depth >= 1)
        require(quarterTurns in 1..3)
    }

    fun inverse() =
        copy(quarterTurns = if (quarterTurns == 2) 2 else 4 - quarterTurns)

    fun notation(): String {
        val suffix = when (quarterTurns) {
            2 -> "2"
            3 -> "'"
            else -> ""
        }

        if (depth > 1) {
            val span = if (width == 1) depth.toString() else "${depth}-${depth + width - 1}"
            return "$span${face.symbol}$suffix"
        }

        val prefix = if (width <= 2) "" else width.toString()
        val wide = if (width >= 2) "w" else ""
        return "$prefix${face.symbol}$wide$suffix"
    }

    companion object {
        private val re = Regex("^(\\d+)?([URFDLBurfdlb])([wW])?([2']?)$")

        fun parse(token: String): Move? {
            val m = re.matchEntire(token.trim()) ?: return null
            val explicit = m.groupValues[1].toIntOrNull()
            val face = Face.fromSymbol(m.groupValues[2][0])
            val wide = m.groupValues[3].isNotEmpty()
            val turns = when (m.groupValues[4]) {
                "2" -> 2
                "'" -> 3
                else -> 1
            }

            return when {
                explicit != null && wide -> Move(face, explicit, turns, 1)
                explicit != null -> Move(face, 1, turns, explicit)
                wide -> Move(face, 2, turns, 1)
                else -> Move(face, 1, turns, 1)
            }
        }

        fun parseAlgorithm(text: String): List<Move> =
            text.trim()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .mapNotNull(::parse)
    }
}
