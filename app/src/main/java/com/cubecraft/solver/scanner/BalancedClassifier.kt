package com.cubecraft.solver.scanner

import com.cubecraft.solver.model.Face

/**
 * Global minimum-cost assignment with exact color capacities.
 * Each center defines one logical color. Hungarian assignment guarantees exactly N^2 stickers
 * per logical color instead of allowing nearest-neighbour drift under difficult illumination.
 */
object BalancedClassifier {
    private data class Item(val capturedFace: Face, val cell: Int, val sample: ColorSample, val isCenter: Boolean)

    fun classify(captures: List<CapturedFace>, size: Int): ClassifiedScan {
        require(captures.size == 6)
        val byFace = captures.associateBy { it.face }
        require(Face.entries.all { byFace[it]?.samples?.size == size * size })
        val center = size * size / 2
        val refs = Face.entries.associateWith { face -> byFace.getValue(face).rotatedSamples(size)[center] }
        val palette = refs.mapValues { it.value.rgb }

        val items = buildList {
            for (face in Face.entries) {
                val samples = byFace.getValue(face).rotatedSamples(size)
                samples.forEachIndexed { idx, s -> add(Item(face, idx, s, idx == center)) }
            }
        }
        val targets = buildList { Face.entries.forEach { f -> repeat(size * size) { add(f) } } }
        val n = items.size
        val costs = Array(n) { i -> DoubleArray(n) { j ->
            val item = items[i]; val target = targets[j]
            if (item.isCenter && target != item.capturedFace) 100_000.0
            else cubeColorDistance(item.sample, refs.getValue(target))
        } }
        val assignment = hungarian(costs)
        val perFace = Face.entries.associateWith { face -> MutableList(size * size) { face } }
        var sum = 0.0
        items.forEachIndexed { i, item ->
            val target = targets[assignment[i]]
            perFace.getValue(item.capturedFace)[item.cell] = target
            sum += costs[i][assignment[i]]
        }
        return ClassifiedScan(perFace.mapValues { it.value.toList() }, palette, sum / n)
    }

    // O(n^3) Hungarian algorithm, 1-indexed internally. n <= 150 for 5x5.
    private fun hungarian(a: Array<DoubleArray>): IntArray {
        val n = a.size
        val u = DoubleArray(n + 1); val v = DoubleArray(n + 1)
        val p = IntArray(n + 1); val way = IntArray(n + 1)
        for (i in 1..n) {
            p[0] = i
            var j0 = 0
            val minv = DoubleArray(n + 1) { Double.POSITIVE_INFINITY }
            val used = BooleanArray(n + 1)
            do {
                used[j0] = true
                val i0 = p[j0]
                var delta = Double.POSITIVE_INFINITY
                var j1 = 0
                for (j in 1..n) if (!used[j]) {
                    val cur = a[i0 - 1][j - 1] - u[i0] - v[j]
                    if (cur < minv[j]) { minv[j] = cur; way[j] = j0 }
                    if (minv[j] < delta) { delta = minv[j]; j1 = j }
                }
                for (j in 0..n) if (used[j]) { u[p[j]] += delta; v[j] -= delta } else minv[j] -= delta
                j0 = j1
            } while (p[j0] != 0)
            do {
                val j1 = way[j0]
                p[j0] = p[j1]
                j0 = j1
            } while (j0 != 0)
        }
        val ans = IntArray(n)
        for (j in 1..n) if (p[j] > 0) ans[p[j] - 1] = j - 1
        return ans
    }
}
