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

        fun findFirstWrapped(
            wrappers: WrapperPool,
            state: ByteArray,
            mode: Int,
            before: Int,
            requireCenters: Boolean
        ): WrappedResult? {
            if (handle == 0L || wrappers.handle == 0L) return null
            return decodeWrapped(
                findFirstWrappedNative(
                    handle,
                    wrappers.handle,
                    state,
                    mode,
                    before,
                    requireCenters
                )
            )
        }

        fun findBestWrapped(
            wrappers: WrapperPool,
            state: ByteArray,
            mode: Int,
            floor: Int,
            requireCenters: Boolean
        ): WrappedResult? {
            if (handle == 0L || wrappers.handle == 0L) return null
            return decodeWrapped(
                findBestWrappedNative(
                    handle,
                    wrappers.handle,
                    state,
                    mode,
                    floor,
                    requireCenters
                )
            )
        }
    }

    class WrapperPool internal constructor(
        internal val handle: Long,
        val count: Int
    )

    data class Result(val index: Int, val score: Int)

    data class WrappedResult(
        val wrapper: Int,
        val index: Int,
        val score: Int
    )

    fun createPool(permutations: List<ShortArray>): Pool? {
        if (!available || permutations.isEmpty()) return null
        val flat = flatten(permutations) ?: return null
        val handle = createPoolNative(flat, permutations.size)
        if (handle == 0L) return null
        return Pool(handle, permutations.size)
    }

    fun createWrapperPool(
        setups: List<ShortArray?>,
        undos: List<ShortArray?>
    ): WrapperPool? {
        if (!available || setups.isEmpty() || setups.size != undos.size) return null
        val identity = ShortArray(150) { it.toShort() }
        val flatSetups = flatten(setups.map { it ?: identity }) ?: return null
        val flatUndos = flatten(undos.map { it ?: identity }) ?: return null
        val handle = createWrapperPoolNative(
            flatSetups,
            flatUndos,
            setups.size
        )
        if (handle == 0L) return null
        return WrapperPool(handle, setups.size)
    }

    private fun flatten(permutations: List<ShortArray>): ShortArray? {
        val flat = ShortArray(permutations.size * 150)
        var offset = 0
        permutations.forEach { perm ->
            if (perm.size != 150) return null
            perm.copyInto(flat, offset)
            offset += 150
        }
        return flat
    }

    private fun decodeWrapped(packed: Long): WrappedResult? {
        if (packed < 0L) return null
        return WrappedResult(
            wrapper = ((packed ushr 24) and 0xFFFFFF).toInt(),
            index = (packed and 0xFFFFFF).toInt(),
            score = ((packed ushr 48) and 0xFFFF).toInt()
        )
    }

    private external fun createPoolNative(flatPerms: ShortArray, count: Int): Long
    private external fun createWrapperPoolNative(
        flatSetups: ShortArray,
        flatUndos: ShortArray,
        count: Int
    ): Long

    private external fun destroyPoolNative(handle: Long)
    private external fun destroyWrapperPoolNative(handle: Long)

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

    private external fun findFirstWrappedNative(
        poolHandle: Long,
        wrapperHandle: Long,
        state: ByteArray,
        mode: Int,
        before: Int,
        requireCenters: Boolean
    ): Long

    private external fun findBestWrappedNative(
        poolHandle: Long,
        wrapperHandle: Long,
        state: ByteArray,
        mode: Int,
        floor: Int,
        requireCenters: Boolean
    ): Long
}
