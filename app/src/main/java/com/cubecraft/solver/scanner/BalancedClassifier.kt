package com.cubecraft.solver.scanner

import com.cubecraft.solver.model.Face

/**
 * Global minimum-cost assignment with exact color capacities.
 * Each center defines one logical color. Hungarian assignment guarantees exactly N^2 stickers
 * per logical color instead of allowing nearest-neighbour drift under difficult illumination.
 */
object BalancedClassifier {
    private data class Item(
        val capturedFace: Face,
        val cell: Int,
        val sample: ColorSample,
        val isCenter: Boolean,
        val manualGuess: StickerGuess?
    )

    fun classify(captures: List<CapturedFace>, size: Int): ClassifiedScan {
        val byFace = validatedCaptures(captures, size)
        val center = size * size / 2
        val refs = Face.entries.associateWith { face -> byFace.getValue(face).rotatedSamples(size)[center] }
        val palette = refs.mapValues { it.value.rgb }
        val faceByRealColor = uniqueCenterColorMap(byFace, size)

        val items = buildList {
            for (face in Face.entries) {
                val capture = byFace.getValue(face)
                val samples = capture.rotatedSamples(size)
                val overrides = capture.rotatedManualGuesses(size)
                samples.forEachIndexed { idx, s ->
                    add(Item(face, idx, s, idx == center, overrides[idx]))
                }
            }
        }
        val targets = buildList { Face.entries.forEach { f -> repeat(size * size) { add(f) } } }
        val n = items.size
        val costs = Array(n) { i -> DoubleArray(n) { j ->
            val item = items[i]
            val target = targets[j]
            val forcedFace = item.manualGuess?.let(faceByRealColor::get)
            when {
                item.isCenter && target != item.capturedFace -> 1_000_000.0
                forcedFace != null && target != forcedFace -> 1_000_000.0
                else -> cubeColorDistance(item.sample, refs.getValue(target))
            }
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

    /**
     * Fail-safe path used only if balanced assignment itself throws.
     * It still calibrates against the six captured centers, but classifies each sticker
     * independently. Counts may therefore be imperfect; ReviewScreen + CubeValidator will show
     * that to the user instead of silently throwing them back into another six-face scan.
     */
    fun classifyNearest(captures: List<CapturedFace>, size: Int): ClassifiedScan {
        val byFace = validatedCaptures(captures, size)
        val center = size * size / 2
        val refs = Face.entries.associateWith { face -> byFace.getValue(face).rotatedSamples(size)[center] }
        val palette = refs.mapValues { it.value.rgb }
        val faceByRealColor = uniqueCenterColorMap(byFace, size)
        var sum = 0.0
        var count = 0

        val perFace = Face.entries.associateWith { capturedFace ->
            val capture = byFace.getValue(capturedFace)
            val samples = capture.rotatedSamples(size)
            val overrides = capture.rotatedManualGuesses(size)
            samples.mapIndexed { idx, sample ->
                val forcedFace = overrides[idx]?.let(faceByRealColor::get)
                val chosen = when {
                    idx == center -> capturedFace
                    forcedFace != null -> forcedFace
                    else -> Face.entries.minBy { target -> cubeColorDistance(sample, refs.getValue(target)) }
                }
                sum += cubeColorDistance(sample, refs.getValue(chosen))
                count++
                chosen
            }
        }
        return ClassifiedScan(perFace, palette, if (count == 0) 0.0 else sum / count)
    }

    /**
     * The scan sequence names faces by orientation (F/R/B/L/U/D), not by physical color.
     * Manual edits are made with real color labels (W/Y/R/O/G/B), so after all six faces exist we
     * map each unique center label back to its logical face identity. Ambiguous/unknown center
     * labels simply disable hard forcing for that color instead of making classification crash.
     */
    private fun uniqueCenterColorMap(
        byFace: Map<Face, CapturedFace>,
        size: Int
    ): Map<StickerGuess, Face> {
        val center = size * size / 2
        val centerColors = Face.entries.associateWith { face ->
            byFace.getValue(face).rotatedGuesses(size).getOrElse(center) { StickerGuess.UNKNOWN }
        }
        val counts = centerColors.values.groupingBy { it }.eachCount()
        return centerColors.entries
            .filter { (face, guess) ->
                face in Face.entries && guess != StickerGuess.UNKNOWN && counts[guess] == 1
            }
            .associate { (face, guess) -> guess to face }
    }

    private fun validatedCaptures(captures: List<CapturedFace>, size: Int): Map<Face, CapturedFace> {
        require(size == 3 || size == 5) { "Unsupported cube size: $size" }
        val byFace = captures.associateBy { it.face }
        val missing = Face.entries.filter { byFace[it] == null }
        require(missing.isEmpty()) {
            "Missing captured faces: ${missing.joinToString { it.symbol.toString() }}"
        }
        val bad = Face.entries.filter { byFace.getValue(it).samples.size != size * size }
        require(bad.isEmpty()) {
            "Wrong sticker count on: ${bad.joinToString { it.symbol.toString() }}"
        }
        return byFace
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
