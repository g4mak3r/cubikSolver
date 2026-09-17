package com.cubecraft.solver.model

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
    init { require(size == 3 || size == 5) { "cubikSolver currently supports 3x3 and 5x5" } }
    private val stickers = mutableMapOf<StickerKey, Face>()

    init { resetSolved() }

    fun resetSolved() {
        stickers.clear()
        for (face in Face.entries) for (r in 0 until size) for (c in 0 until size) {
            stickers[keyFromFaceCell(face, r, c)] = face
        }
    }

    /**
     * Copy the exact internal sticker map instead of serializing through snapshot/loadFaces.
     * A scanned cube may be unsolved and contain any legal color arrangement; cloning it should
     * never re-validate or reinterpret that arrangement.
     */
    fun deepCopy(): CubeState {
        val copy = CubeState(size)
        copy.stickers.clear()
        copy.stickers.putAll(stickers)
        return copy
    }

    fun snapshot(): Map<Face, List<Face>> =
        Face.entries.associateWith { face -> faceColors(face) }

    fun loadFaces(faces: Map<Face, List<Face>>) {
        val n = size
        val expected = n * n
        val bad = Face.entries.filter { face -> faces[face]?.size != expected }
        require(bad.isEmpty()) {
            val details = Face.entries.joinToString { face ->
                "${face.symbol}=${faces[face]?.size ?: 0}"
            }
            "Expected $expected stickers on every face, got $details"
        }

        stickers.clear()
        for (face in Face.entries) {
            val values = faces.getValue(face)
            for (r in 0 until n) for (c in 0 until n) {
                stickers[keyFromFaceCell(face, r, c)] = values[r * n + c]
            }
        }

        check(stickers.size == 6 * expected) {
            "Internal cube geometry contains ${stickers.size} stickers, expected ${6 * expected}"
        }
    }

    /**
     * Snapshot one face in row-major scan order.
     *
     * Keep n outside buildList. Inside buildList { ... }, an unqualified `size` resolves to the
     * MutableList builder's current size (initially 0), not CubeState.size. That shadowing bug made
     * every face snapshot empty and ultimately produced min2phase errors such as "index: 4, size: 0".
     */
    fun faceColors(face: Face): List<Face> {
        val n = size
        return buildList(n * n) {
            for (r in 0 until n) for (c in 0 until n) {
                val key = keyFromFaceCell(face, r, c)
                add(requireNotNull(stickers[key]) { "Missing sticker ${face.symbol}[$r,$c] at $key" })
            }
        }
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
        // A 5x5 needs three-layer wide turns as a compact way to express the physical middle
        // slice: 3Rw followed by Rw' is the isolated third layer. The 3x3 solver itself still emits
        // outer turns only.
        val maxWidth = if (size == 5) 3 else 1
        require(move.width <= maxWidth) { "Turn width ${move.width} is too large for ${size}x$size" }
        repeat(move.quarterTurns) { quarterTurn(move.face, move.width) }
    }

    fun applyAll(moves: Iterable<Move>) = moves.forEach(::apply)

    private fun quarterTurn(face: Face, width: Int) {
        val out = mutableMapOf<StickerKey, Face>()
        for ((key, color) in stickers) {
            out[if (key.inSlab(face, width, size)) key.rotateClockwise(face, size) else key] = color
        }
        stickers.clear()
        stickers.putAll(out)
    }

    /**
     * Serialize any supported cube in standard URFDLB face order, each face row-major.
     * 3x3 -> 54 chars, 5x5 -> 150 chars. This is the stable bridge format for external solvers.
     */
    fun toUrfdlbString(): String {
        val chars = 6 * size * size
        return buildString(chars) {
            listOf(Face.U, Face.R, Face.F, Face.D, Face.L, Face.B).forEach { face ->
                faceColors(face).forEach { append(it.symbol) }
            }
        }
    }

    fun toMin2PhaseString(): String {
        require(size == 3) { "min2phase serialization expects a 3x3 cube" }
        return toUrfdlbString()
    }

    /** Odd-cube fixed centers, corners and middle edge pieces form a legal 3x3 skeleton. */
    fun reducedSkeleton3x3(): CubeState {
        val n = size
        require(n % 2 == 1) { "Reduced 3x3 skeleton requires an odd cube size" }
        if (n == 3) return deepCopy()
        val pick = listOf(0, n / 2, n - 1)
        val faces = Face.entries.associateWith { face ->
            val src = faceColors(face)
            buildList(9) {
                for (r in pick) for (c in pick) add(src[r * n + c])
            }
        }
        return CubeState(3).also { it.loadFaces(faces) }
    }

    fun colorCounts(): Map<Face, Int> =
        Face.entries.associateWith { face -> stickers.values.count { it == face } }

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