package com.cubecraft.solver.scanner

import com.cubecraft.solver.model.Face

object StandardCubeScheme {
    private data class Axis(val x: Int, val y: Int, val z: Int)

    private val axis = mapOf(
        StickerGuess.WHITE to Axis(0, 1, 0),
        StickerGuess.YELLOW to Axis(0, -1, 0),
        StickerGuess.RED to Axis(1, 0, 0),
        StickerGuess.ORANGE to Axis(-1, 0, 0),
        StickerGuess.GREEN to Axis(0, 0, 1),
        StickerGuess.BLUE to Axis(0, 0, -1)
    )

    private val colorByAxis = axis.entries.associate { it.value to it.key }

    fun opposite(color: StickerGuess): StickerGuess? {
        val a = axis[color] ?: return null
        return colorByAxis[Axis(-a.x, -a.y, -a.z)]
    }

    fun expected(
        confirmed: Map<Face, StickerGuess>,
        target: Face
    ): StickerGuess? {
        val front = confirmed[Face.F] ?: return null
        val right = confirmed[Face.R]

        return when (target) {
            Face.F -> front
            Face.R -> null
            Face.B -> opposite(front)
            Face.L -> right?.let(::opposite)
            Face.U -> {
                val r = right ?: return null
                val fAxis = axis[front] ?: return null
                val rAxis = axis[r] ?: return null
                if (dot(fAxis, rAxis) != 0) return null
                colorByAxis[cross(fAxis, rAxis)]
            }
            Face.D -> {
                val up = expected(confirmed, Face.U) ?: return null
                opposite(up)
            }
        }
    }

    private fun dot(a: Axis, b: Axis): Int =
        a.x * b.x + a.y * b.y + a.z * b.z

    private fun cross(a: Axis, b: Axis): Axis =
        Axis(
            a.y * b.z - a.z * b.y,
            a.z * b.x - a.x * b.z,
            a.x * b.y - a.y * b.x
        )
}
