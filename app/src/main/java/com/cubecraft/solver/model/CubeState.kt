package com.cubecraft.solver.model

import kotlin.math.max

data class StickerKey(
    val x: Int, val y: Int, val z: Int,
    val nx: Int, val ny: Int, val nz: Int
)

data class VisibleSticker(val key: StickerKey, val color: Face)

/**
 * Geometry-backed NxN sticker model. Colors travel with sticker coordinates; rendering,
 * scanning and solvers all read the same state. Supports 3x3 and 5x5, including wide turns.
 */
class CubeState(val size: Int) {
    init { require(size == 3 || size == 5) { "CUBECRAFT currently supports 3x3 and 5x5" } }
    private val stickers = mutableMapOf<StickerKey, Face>()

    init { resetSolved() }

    fun resetSolved() {
        stickers.clear()
        for (face in Face.entries) for (r in 0 until size) for (c in 0 until size) {
            stickers[keyFromFaceCell(face, r, c)] = face
        }
    }

    fun deepCopy(): CubeState = CubeState(size).also { it.loadFaces(snapshot()) }

    fun snapshot(): Map<Face, List<Face>> = Face.entries.associateWith(::faceColors)

    fun loadFaces(faces: Map<Face, List<Face>>) {
        require(Face.entries.all { faces[it]?.size == size * size })
        stickers.clear()
        for (face in Face.entries) {
            val values = faces.getValue(face)
            for (r in 0 until size) for (c in 0 until size) {
                stickers[keyFromFaceCell(face, r, c)] = values[r * size + c]
            }
        }
    }

    fun faceColors(face: Face): List<Face> = buildList(size * size) {
        for (r in 0 until size) for (c in 0 until size) add(stickers.getValue(keyFromFaceCell(face, r, c)))
    }

    fun visibleStickers(): List<VisibleSticker> = stickers.map { VisibleSticker(it.key, it.value) }

    fun stickerColor(key: StickerKey): Face? = stickers[key]

    fun setStickerColor(key: StickerKey, color: Face): Boolean {
        if (!stickers.containsKey(key)) return false
        stickers[key] = color
        return true
    }

    fun isFixedCenter(key: StickerKey): Boolean {
        val m = size / 2
        return when {
            key.nx != 0 -> key.y == m && key.z == m
            key.ny != 0 -> key.x == m && key.z == m
            key.nz != 0 -> key.x == m && key.y == m
            else -> false
        }
    }

    fun isSolved(): Boolean = Face.entries.all { f -> faceColors(f).all { it == f } }

    fun apply(move: Move) {
        require(move.width <= (size / 2).coerceAtLeast(1)) { "Turn width ${move.width} is too large for ${size}x$size" }
        repeat(move.quarterTurns) { quarterTurn(move.face, move.width) }
    }

    fun applyAll(moves: Iterable<Move>) = moves.forEach(::apply)

    private fun quarterTurn(face: Face, width: Int) {
        val out = mutableMapOf<StickerKey, Face>()
        for ((key, color) in stickers) {
            out[if (key.inSlab(face, width, size)) key.rotateClockwise(face, size) else key] = color
        }
        stickers.clear(); stickers.putAll(out)
    }

    fun toMin2PhaseString(): String {
        require(size == 3)
        return buildString(54) {
            listOf(Face.U, Face.R, Face.F, Face.D, Face.L, Face.B).forEach { face ->
                faceColors(face).forEach { append(it.symbol) }
            }
        }
    }

    /** Odd-cube fixed centers, corners and middle edge pieces form a legal 3x3 skeleton. */
    fun reducedSkeleton3x3(): CubeState {
        require(size % 2 == 1)
        if (size == 3) return deepCopy()
        val pick = listOf(0, size / 2, size - 1)
        val faces = Face.entries.associateWith { face ->
            val src = faceColors(face)
            buildList(9) { for (r in pick) for (c in pick) add(src[r * size + c]) }
        }
        return CubeState(3).also { it.loadFaces(faces) }
    }

    fun colorCounts(): Map<Face, Int> = Face.entries.associateWith { face -> stickers.values.count { it == face } }

    fun keyFromFaceCell(face: Face, r: Int, c: Int): StickerKey = when (face) {
        Face.F -> StickerKey(c, size - 1 - r, size - 1, 0, 0, 1)
        Face.B -> StickerKey(size - 1 - c, size - 1 - r, 0, 0, 0, -1)
        Face.U -> StickerKey(c, size - 1, r, 0, 1, 0)
        Face.D -> StickerKey(c, 0, size - 1 - r, 0, -1, 0)
        Face.R -> StickerKey(size - 1, size - 1 - r, size - 1 - c, 1, 0, 0)
        Face.L -> StickerKey(0, size - 1 - r, c, -1, 0, 0)
    }
}

private fun StickerKey.inSlab(face: Face, width: Int, n: Int): Boolean = when (face) {
    Face.R -> x >= n - width
    Face.L -> x < width
    Face.U -> y >= n - width
    Face.D -> y < width
    Face.F -> z >= n - width
    Face.B -> z < width
}

private fun StickerKey.rotateClockwise(face: Face, n: Int): StickerKey = when (face) {
    // Sign is defined in world axes and chosen to match Singmaster clockwise as viewed at each face.
    Face.R -> rotX(-1, n)
    Face.L -> rotX(+1, n)
    Face.U -> rotY(-1, n)
    Face.D -> rotY(+1, n)
    Face.F -> rotZ(-1, n)
    Face.B -> rotZ(+1, n)
}

private fun StickerKey.rotX(sign: Int, n: Int) = if (sign > 0)
    copy(y = n - 1 - z, z = y, ny = -nz, nz = ny)
else copy(y = z, z = n - 1 - y, ny = nz, nz = -ny)

private fun StickerKey.rotY(sign: Int, n: Int) = if (sign > 0)
    copy(x = z, z = n - 1 - x, nx = nz, nz = -nx)
else copy(x = n - 1 - z, z = x, nx = -nz, nz = nx)

private fun StickerKey.rotZ(sign: Int, n: Int) = if (sign > 0)
    copy(x = n - 1 - y, y = x, nx = -ny, ny = nx)
else copy(x = y, y = n - 1 - x, nx = ny, ny = -nx)
