package com.cubecraft.solver.solver

internal object NativeFiveByFiveKernel {
    const val MODE_CENTERS = 0
    const val MODE_EDGES = 1

    val available: Boolean = runCatching {
        System.loadLibrary("cubik555")
        true
    }.getOrDefault(false)

    class Pool internal constructor(
        private val handle: Long,
        val count: Int
    ) {
        fun findFirstImproving(
            state: ByteArray,
            undo: ShortArray?,
            mode: Int,
            before: Int,
            requireCenters: Boolean
        ): Int {
            if (handle == 0L) return -1
            return findFirstImprovingNative(
                handle,
                state,
                undo,
                mode,
                before,
                requireCenters
            )
        }

        fun findBest(
            state: ByteArray,
            undo: ShortArray?,
            mode: Int,
            floor: Int,
            requireCenters: Boolean
        ): Result? {
            if (handle == 0L) return null
            val packed = findBestNative(
                handle,
                state,
                undo,
                mode,
                floor,
                requireCenters
            )
            if (packed < 0L) return null
            return Result(
                index = packed.toInt(),
                score = (packed ushr 32).toInt()
            )
        }
    }

    data class Result(val index: Int, val score: Int)

    fun createPool(permutations: List<ShortArray>): Pool? {
        if (!available || permutations.isEmpty()) return null
        val flat = ShortArray(permutations.size * 150)
        var offset = 0
        permutations.forEach { perm ->
            if (perm.size != 150) return null
            perm.copyInto(flat, offset)
            offset += 150
        }
        val handle = createPoolNative(flat, permutations.size)
        if (handle == 0L) return null
        return Pool(handle, permutations.size)
    }

    private external fun createPoolNative(flatPerms: ShortArray, count: Int): Long
    private external fun destroyPoolNative(handle: Long)
    private external fun findFirstImprovingNative(
        handle: Long,
        state: ByteArray,
        undo: ShortArray?,
        mode: Int,
        before: Int,
        requireCenters: Boolean
    ): Int

    private external fun findBestNative(
        handle: Long,
        state: ByteArray,
        undo: ShortArray?,
        mode: Int,
        floor: Int,
        requireCenters: Boolean
    ): Long
}
