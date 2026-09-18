package com.cubecraft.solver.scanner

import com.cubecraft.solver.model.Face

object BalancedClassifier {
    private data class Item(
        val capturedFace: Face,
        val cell: Int,
        val sample: ColorSample,
        val center: Boolean,
        val manual: StickerGuess?
    )

    private data class Calibration(
        val refs: Map<Face, ColorSample>,
        val colorByFace: Map<Face, StickerGuess>,
        val faceByColor: Map<StickerGuess, Face>,
        val palette: Map<Face, RgbColor>
    )

    fun classify(captures: List<CapturedFace>, size: Int): ClassifiedScan {
        val byFace = validatedCaptures(captures, size)
        val calibration = calibrate(byFace, size)
        val center = size * size / 2

        val items = buildList {
            for (face in Face.entries) {
                val capture = byFace.getValue(face)
                val samples = capture.rotatedSamples(size)
                val manual = capture.rotatedManualGuesses(size)
                samples.forEachIndexed { index, sample ->
                    add(Item(face, index, sample, index == center, manual[index]))
                }
            }
        }

        val targets = buildList {
            Face.entries.forEach { face ->
                repeat(size * size) { add(face) }
            }
        }

        val costs = Array(items.size) { i ->
            DoubleArray(items.size) { j ->
                val item = items[i]
                val target = targets[j]
                val forced = item.manual?.let(calibration.faceByColor::get)
                when {
                    item.center && target != item.capturedFace -> 1_000_000.0
                    forced != null && target != forced -> 1_000_000.0
                    else -> stickerCost(item.sample, target, calibration)
                }
            }
        }

        val assignment = hungarian(costs)
        val perFace = Face.entries.associateWith { face ->
            MutableList(size * size) { face }
        }

        var sum = 0.0
        items.forEachIndexed { i, item ->
            val target = targets[assignment[i]]
            perFace.getValue(item.capturedFace)[item.cell] = target
            sum += costs[i][assignment[i]]
        }

        return ClassifiedScan(
            faces = perFace.mapValues { it.value.toList() },
            palette = calibration.palette,
            colorFaces = calibration.faceByColor,
            meanDistance = sum / items.size
        )
    }

    fun classifyNearest(captures: List<CapturedFace>, size: Int): ClassifiedScan {
        val byFace = validatedCaptures(captures, size)
        val calibration = calibrate(byFace, size)
        val center = size * size / 2
        var sum = 0.0
        var count = 0

        val perFace = Face.entries.associateWith { capturedFace ->
            val capture = byFace.getValue(capturedFace)
            val samples = capture.rotatedSamples(size)
            val manual = capture.rotatedManualGuesses(size)

            samples.mapIndexed { index, sample ->
                val forced = manual[index]?.let(calibration.faceByColor::get)
                val chosen = when {
                    index == center -> capturedFace
                    forced != null -> forced
                    else -> Face.entries.minBy { stickerCost(sample, it, calibration) }
                }
                sum += stickerCost(sample, chosen, calibration)
                count++
                chosen
            }
        }

        return ClassifiedScan(
            faces = perFace,
            palette = calibration.palette,
            colorFaces = calibration.faceByColor,
            meanDistance = if (count == 0) 0.0 else sum / count
        )
    }

    private fun calibrate(
        byFace: Map<Face, CapturedFace>,
        size: Int
    ): Calibration {
        val center = size * size / 2
        val refs = Face.entries.associateWith { face ->
            byFace.getValue(face).rotatedSamples(size)[center]
        }

        val colors = canonicalStickerGuesses
        val costs = Array(Face.entries.size) { i ->
            val face = Face.entries[i]
            val capture = byFace.getValue(face)
            val sample = refs.getValue(face)
            val manual = capture.rotatedManualGuesses(size)[center]
            val live = capture.rotatedGuesses(size).getOrElse(center) { StickerGuess.UNKNOWN }

            DoubleArray(colors.size) { j ->
                val color = colors[j]
                when {
                    manual != null && color != manual -> 1_000_000.0
                    else -> {
                        var cost = cubeColorDistance(sample, canonicalColorSample(color))
                        if (manual == null && live != StickerGuess.UNKNOWN && live != color) {
                            cost += 5.0
                        }
                        cost
                    }
                }
            }
        }

        val assignment = hungarian(costs)
        val colorByFace = Face.entries.indices.associate { i ->
            Face.entries[i] to colors[assignment[i]]
        }
        val faceByColor = colorByFace.entries.associate { (face, color) ->
            color to face
        }
        val palette = Face.entries.associateWith { face ->
            idealDisplayRgb(colorByFace.getValue(face))
        }

        return Calibration(refs, colorByFace, faceByColor, palette)
    }

    private fun stickerCost(
        sample: ColorSample,
        target: Face,
        calibration: Calibration
    ): Double {
        val adaptive = cubeColorDistance(sample, calibration.refs.getValue(target))
        val canonical = cubeColorDistance(
            sample,
            canonicalColorSample(calibration.colorByFace.getValue(target))
        )
        return adaptive * .82 + canonical * .18
    }

    private fun validatedCaptures(
        captures: List<CapturedFace>,
        size: Int
    ): Map<Face, CapturedFace> {
        require(size == 3 || size == 5)
        val byFace = captures.associateBy { it.face }
        require(Face.entries.all(byFace::containsKey))
        require(
            Face.entries.all {
                byFace.getValue(it).samples.size == size * size
            }
        )
        return byFace
    }

    private fun hungarian(cost: Array<DoubleArray>): IntArray {
        val n = cost.size
        val u = DoubleArray(n + 1)
        val v = DoubleArray(n + 1)
        val p = IntArray(n + 1)
        val way = IntArray(n + 1)

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

                for (j in 1..n) {
                    if (used[j]) continue
                    val current = cost[i0 - 1][j - 1] - u[i0] - v[j]
                    if (current < minv[j]) {
                        minv[j] = current
                        way[j] = j0
                    }
                    if (minv[j] < delta) {
                        delta = minv[j]
                        j1 = j
                    }
                }

                for (j in 0..n) {
                    if (used[j]) {
                        u[p[j]] += delta
                        v[j] -= delta
                    } else {
                        minv[j] -= delta
                    }
                }
                j0 = j1
            } while (p[j0] != 0)

            do {
                val j1 = way[j0]
                p[j0] = p[j1]
                j0 = j1
            } while (j0 != 0)
        }

        val result = IntArray(n)
        for (j in 1..n) {
            if (p[j] > 0) result[p[j] - 1] = j - 1
        }
        return result
    }
}
