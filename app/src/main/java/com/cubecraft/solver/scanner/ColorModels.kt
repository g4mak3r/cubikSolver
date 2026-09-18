package com.cubecraft.solver.scanner

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class LabColor(val l: Double, val a: Double, val b: Double)

data class RgbColor(val r: Int, val g: Int, val b: Int) {
    fun argb(alpha: Int = 255): Int =
        (alpha shl 24) or
            (r.coerceIn(0, 255) shl 16) or
            (g.coerceIn(0, 255) shl 8) or
            b.coerceIn(0, 255)

    fun features(): ColorFeatures {
        val rr = r.coerceIn(0, 255) / 255.0
        val gg = g.coerceIn(0, 255) / 255.0
        val bb = b.coerceIn(0, 255) / 255.0
        val high = max(rr, max(gg, bb))
        val low = min(rr, min(gg, bb))
        val delta = high - low
        val saturation = if (high <= 1e-9) 0.0 else delta / high

        var hue = when {
            delta <= 1e-9 -> 0.0
            high == rr -> 60.0 * (((gg - bb) / delta) % 6.0)
            high == gg -> 60.0 * (((bb - rr) / delta) + 2.0)
            else -> 60.0 * (((rr - gg) / delta) + 4.0)
        }
        if (hue < 0.0) hue += 360.0

        val sum = (rr + gg + bb).coerceAtLeast(1e-9)
        return ColorFeatures(
            hue = hue,
            saturation = saturation,
            value = high,
            rn = rr / sum,
            gn = gg / sum,
            bn = bb / sum,
            redOrangeAxis = gg / sum - bb / sum
        )
    }
}

data class ColorFeatures(
    val hue: Double,
    val saturation: Double,
    val value: Double,
    val rn: Double,
    val gn: Double,
    val bn: Double,
    val redOrangeAxis: Double
)

data class ColorSample(val lab: LabColor, val rgb: RgbColor)

val canonicalStickerGuesses = listOf(
    StickerGuess.WHITE,
    StickerGuess.YELLOW,
    StickerGuess.RED,
    StickerGuess.ORANGE,
    StickerGuess.GREEN,
    StickerGuess.BLUE
)

fun idealDisplayRgb(guess: StickerGuess): RgbColor = when (guess) {
    StickerGuess.WHITE -> RgbColor(248, 247, 240)
    StickerGuess.YELLOW -> RgbColor(255, 214, 0)
    StickerGuess.RED -> RgbColor(205, 36, 48)
    StickerGuess.ORANGE -> RgbColor(255, 132, 0)
    StickerGuess.GREEN -> RgbColor(0, 158, 84)
    StickerGuess.BLUE -> RgbColor(0, 86, 190)
    StickerGuess.UNKNOWN -> RgbColor(96, 98, 91)
}

private data class RecognitionBand(
    val hueRanges: List<ClosedFloatingPointRange<Double>>,
    val satCore: ClosedFloatingPointRange<Double>,
    val valueCore: ClosedFloatingPointRange<Double>
)

private val recognitionBands = mapOf(
    StickerGuess.WHITE to RecognitionBand(
        emptyList(),
        0.0..0.24,
        0.58..1.0
    ),
    StickerGuess.YELLOW to RecognitionBand(
        listOf(20.0..50.0),
        0.42..1.0,
        0.48..1.0
    ),
    StickerGuess.RED to RecognitionBand(
        listOf(0.0..5.5, 160.0..179.5),
        0.45..1.0,
        0.25..1.0
    ),
    StickerGuess.ORANGE to RecognitionBand(
        listOf(3.0..23.0),
        0.52..1.0,
        0.50..1.0
    ),
    StickerGuess.GREEN to RecognitionBand(
        listOf(40.0..100.0),
        0.35..1.0,
        0.22..1.0
    ),
    StickerGuess.BLUE to RecognitionBand(
        listOf(95.0..160.0),
        0.45..1.0,
        0.30..1.0
    )
)

private val recognitionVariants = mapOf(
    StickerGuess.WHITE to listOf(
        RgbColor(255, 255, 255),
        RgbColor(242, 235, 218),
        RgbColor(235, 220, 190)
    ),
    StickerGuess.YELLOW to listOf(
        RgbColor(255, 255, 0),
        RgbColor(255, 214, 0),
        RgbColor(250, 210, 25)
    ),
    StickerGuess.RED to listOf(
        RgbColor(255, 0, 0),
        RgbColor(235, 25, 35),
        RgbColor(195, 28, 37)
    ),
    StickerGuess.ORANGE to listOf(
        RgbColor(255, 165, 0),
        RgbColor(255, 100, 8),
        RgbColor(255, 85, 5),
        RgbColor(255, 33, 0)
    ),
    StickerGuess.GREEN to listOf(
        RgbColor(0, 255, 0),
        RgbColor(0, 180, 80),
        RgbColor(0, 158, 84)
    ),
    StickerGuess.BLUE to listOf(
        RgbColor(0, 0, 255),
        RgbColor(0, 86, 190),
        RgbColor(30, 90, 220)
    )
)

fun colorSample(rgb: RgbColor): ColorSample =
    ColorSample(rgbToOpenCvLab(rgb), rgb)

fun recognitionCost(rgb: RgbColor, guess: StickerGuess): Double {
    if (guess == StickerGuess.UNKNOWN) return 1_000.0

    val f = rgb.features()
    val h = f.hue / 2.0
    val s = f.saturation
    val v = f.value
    val band = recognitionBands.getValue(guess)

    if (guess == StickerGuess.WHITE) {
        val sat = rangePenalty(s, band.satCore) * 80.0
        val value = rangePenalty(v, band.valueCore) * 45.0
        return sat + value
    }

    val hue = band.hueRanges.minOf { hueRangePenalty(h, it) }
    val sat = rangePenalty(s, band.satCore)
    val value = rangePenalty(v, band.valueCore)

    val redOrange = when (guess) {
        StickerGuess.RED -> if (f.redOrangeAxis > .035) {
            (f.redOrangeAxis - .035) * 220.0 + 16.0
        } else 0.0
        StickerGuess.ORANGE -> if (f.redOrangeAxis < .018) {
            (.018 - f.redOrangeAxis) * 240.0 + 12.0
        } else 0.0
        else -> 0.0
    }

    val variants = recognitionVariants.getValue(guess)
    val variantDistance = variants.minOf { variant ->
        val vf = variant.features()
        val dh = circularHueDistance(f.hue, vf.hue) / 180.0
        val ds = abs(f.saturation - vf.saturation)
        val dv = abs(f.value - vf.value)
        val dc = sqrt(
            (f.rn - vf.rn) * (f.rn - vf.rn) +
                (f.gn - vf.gn) * (f.gn - vf.gn) +
                (f.bn - vf.bn) * (f.bn - vf.bn)
        )
        dh * 18.0 + ds * 12.0 + dv * 5.0 + dc * 26.0
    }

    return hue * 3.6 + sat * 28.0 + value * 14.0 + redOrange + variantDistance
}

fun recognitionGuess(rgb: RgbColor): Pair<StickerGuess, Double> {
    val ranked = canonicalStickerGuesses
        .map { it to recognitionCost(rgb, it) }
        .sortedBy { it.second }

    val best = ranked[0]
    val second = ranked[1]
    val margin = second.second - best.second
    val confidence = (margin / (second.second + 10.0))
        .coerceIn(.10, 1.0)

    return best.first to confidence
}

fun circularHueDistance(a: Double, b: Double): Double {
    val d = abs(a - b) % 360.0
    return min(d, 360.0 - d)
}

fun cubeColorDistance(sample: ColorSample, reference: ColorSample): Double {
    val sl = sample.lab
    val rl = reference.lab
    val dl = (sl.l - rl.l) * .42
    val da = (sl.a - rl.a) * 1.05
    val db = (sl.b - rl.b) * 1.16
    val lab = sqrt(dl * dl + da * da + db * db)

    val sf = sample.rgb.features()
    val rf = reference.rgb.features()
    val saturation = abs(sf.saturation - rf.saturation) * 54.0
    val chromaticity = sqrt(
        (sf.rn - rf.rn) * (sf.rn - rf.rn) +
            (sf.gn - rf.gn) * (sf.gn - rf.gn) +
            (sf.bn - rf.bn) * (sf.bn - rf.bn)
    ) * 115.0

    val hue = if (sf.saturation > .24 && rf.saturation > .24) {
        circularHueDistance(sf.hue, rf.hue) * .42
    } else {
        0.0
    }

    val bothWarm = sf.rn > .56 && rf.rn > .56 &&
        (sf.hue < 52.0 || sf.hue > 330.0) &&
        (rf.hue < 52.0 || rf.hue > 330.0)

    val warmAxis = if (bothWarm) {
        abs(sf.redOrangeAxis - rf.redOrangeAxis) * 245.0
    } else {
        0.0
    }

    val neutralPenalty = when {
        rf.saturation < .30 && sf.saturation > .38 ->
            (sf.saturation - .38) * 85.0
        rf.saturation > .48 && sf.saturation < .28 ->
            (.28 - sf.saturation) * 60.0
        else -> 0.0
    }

    return lab + saturation + chromaticity + hue + warmAxis + neutralPenalty
}

private fun rgbToOpenCvLab(rgb: RgbColor): LabColor {
    fun linear(c: Double): Double =
        if (c <= .04045) c / 12.92 else Math.pow((c + .055) / 1.055, 2.4)

    val r = linear(rgb.r.coerceIn(0, 255) / 255.0)
    val g = linear(rgb.g.coerceIn(0, 255) / 255.0)
    val b = linear(rgb.b.coerceIn(0, 255) / 255.0)

    val x = (r * .4124564 + g * .3575761 + b * .1804375) / .95047
    val y = r * .2126729 + g * .7151522 + b * .0721750
    val z = (r * .0193339 + g * .1191920 + b * .9503041) / 1.08883

    fun f(t: Double): Double {
        val e = 216.0 / 24389.0
        val k = 24389.0 / 27.0
        return if (t > e) Math.cbrt(t) else (k * t + 16.0) / 116.0
    }

    val fx = f(x)
    val fy = f(y)
    val fz = f(z)

    return LabColor(
        (116.0 * fy - 16.0) * 255.0 / 100.0,
        500.0 * (fx - fy) + 128.0,
        200.0 * (fy - fz) + 128.0
    )
}

private fun rangePenalty(value: Double, range: ClosedFloatingPointRange<Double>): Double =
    when {
        value < range.start -> range.start - value
        value > range.endInclusive -> value - range.endInclusive
        else -> 0.0
    }

private fun hueRangePenalty(
    hue: Double,
    range: ClosedFloatingPointRange<Double>
): Double {
    if (hue in range) return 0.0
    val direct = min(abs(hue - range.start), abs(hue - range.endInclusive))
    val wrapped = min(
        abs((hue + 180.0) - range.start),
        abs((hue - 180.0) - range.endInclusive)
    )
    return min(direct, wrapped)
}
