package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.model.StickerKey

/**
 * Piece/topology helpers for the 5x5 reduction engine.
 *
 * These helpers deliberately inspect CubeState's geometric sticker positions instead of assuming
 * hard-coded 1..150 facelet indexes. That keeps scanner state, move replay and the future solver on
 * the same coordinate system.
 */
object FiveByFiveTopology {
    data class Position(val x: Int, val y: Int, val z: Int)

    data class EdgeCubie(
        val position: Position,
        /** Location face -> sticker color currently visible on that side of the edge cubie. */
        val colorsByLocationFace: Map<Face, Face>,
        /** 1 or 3 for a wing, 2 for the fixed middle-edge orbit on a 5x5. */
        val lineCoordinate: Int
    ) {
        val isMiddle: Boolean get() = lineCoordinate == 2
        val isWing: Boolean get() = !isMiddle
    }

    data class EdgeSlot(
        /** The two faces meeting at this physical edge location, e.g. U/F. */
        val locationFaces: Set<Face>,
        /** Three cubies ordered by their coordinate along the edge line. */
        val cubies: List<EdgeCubie>
    ) {
        val middle: EdgeCubie get() = cubies.single { it.isMiddle }
        val wings: List<EdgeCubie> get() = cubies.filter { it.isWing }

        /**
         * A reduced edge is paired when all three cubies show one consistent color on each side.
         * The color pair does not have to belong to this location yet; outer 3x3 turns handle that.
         */
        fun isPaired(): Boolean = locationFaces.all { locationFace ->
            val colors = cubies.mapNotNull { it.colorsByLocationFace[locationFace] }
            colors.size == 3 && colors.distinct().size == 1
        }
    }

    fun solvedMovableCenterCount(cube: CubeState): Int {
        require(cube.size == 5) { "5x5 topology requires CubeState(5)" }
        var solved = 0
        for (face in Face.entries) {
            val colors = cube.faceColors(face)
            for (r in 1..3) for (c in 1..3) {
                if (r == 2 && c == 2) continue // fixed center, not part of the 48-piece search
                if (colors[r * 5 + c] == face) solved++
            }
        }
        return solved
    }

    fun centersSolved(cube: CubeState): Boolean = solvedMovableCenterCount(cube) == 48

    fun edgeSlots(cube: CubeState): List<EdgeSlot> {
        require(cube.size == 5) { "5x5 topology requires CubeState(5)" }
        val n = cube.size

        val byPosition = cube.visibleStickers().groupBy {
            Position(it.key.x, it.key.y, it.key.z)
        }

        val edgeCubies = byPosition.mapNotNull { (position, stickers) ->
            val boundaryCount = listOf(position.x, position.y, position.z)
                .count { it == 0 || it == n - 1 }
            if (boundaryCount != 2 || stickers.size != 2) return@mapNotNull null

            val lineCoordinate = listOf(position.x, position.y, position.z)
                .single { it != 0 && it != n - 1 }
            val colors = stickers.associate { sticker ->
                locationFace(sticker.key) to sticker.color
            }
            if (colors.size != 2) return@mapNotNull null

            EdgeCubie(position, colors, lineCoordinate)
        }

        return edgeCubies
            .groupBy { it.colorsByLocationFace.keys }
            .map { (faces, cubies) -> EdgeSlot(faces, cubies.sortedBy { it.lineCoordinate }) }
            .sortedBy { slot -> slot.locationFaces.map { it.ordinal }.sorted().joinToString(",") }
    }

    fun middleEdges(cube: CubeState): List<EdgeCubie> = edgeSlots(cube).map { it.middle }

    fun wings(cube: CubeState): List<EdgeCubie> = edgeSlots(cube).flatMap { it.wings }

    fun pairedEdgeCount(cube: CubeState): Int = edgeSlots(cube).count { it.isPaired() }

    fun edgesPaired(cube: CubeState): Boolean = pairedEdgeCount(cube) == 12

    private fun locationFace(key: StickerKey): Face = when {
        key.ny > 0 -> Face.U
        key.ny < 0 -> Face.D
        key.nx > 0 -> Face.R
        key.nx < 0 -> Face.L
        key.nz > 0 -> Face.F
        key.nz < 0 -> Face.B
        else -> error("Sticker has no outward normal: $key")
    }
}
