package com.cubecraft.solver.scanner

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class LabColor(val l: Double, val a: Double, val b: Double) {
    fun distance(other: LabColor): Double {
        val dl=l-other.l; val da=a-other.a; val db=b-other.b
        return sqrt(dl*dl + da*da + db*db)
    }
}

data class RgbColor(val r: Int, val g: Int, val b: Int) {
    fun argb(alpha: Int = 255): Int = (alpha shl 24) or (r.coerceIn(0,255) shl 16) or (g.coerceIn(0,255) shl 8) or b.coerceIn(0,255)

    fun features(): ColorFeatures {
        val rr = r.coerceIn(0,255) / 255.0
        val gg = g.coerceIn(0,255) / 255.0
        val bb = b.coerceIn(0,255) / 255.0
        val mx = max(rr, max(gg, bb))
        val mn = min(rr, min(gg, bb))
        val d = mx - mn
        val saturation = if (mx <= 1e-9) 0.0 else d / mx
        var hue = when {
            d <= 1e-9 -> 0.0
            mx == rr -> 60.0 * (((gg - bb) / d) % 6.0)
            mx == gg -> 60.0 * (((bb - rr) / d) + 2.0)
            else -> 60.0 * (((rr - gg) / d) + 4.0)
        }
        if (hue < 0.0) hue += 360.0
        val sum = (rr + gg + bb).coerceAtLeast(1e-9)
        val rn = rr / sum
        val gn = gg / sum
        val bn = bb / sum
        val redOrangeAxis = gn - bn




        if (hue < 11.0 && rn > .62 && redOrangeAxis > .035) {
            hue = 12.0 + ((redOrangeAxis - .035) * 42.0).coerceIn(0.0, 7.0)
        }

        return ColorFeatures(
            hue = hue,
            saturation = saturation,
            value = mx,
            rn = rn,
            gn = gn,
            bn = bn,
            redOrangeAxis = redOrangeAxis
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


fun idealDisplayRgb(guess: StickerGuess): RgbColor = when (guess) {
    StickerGuess.WHITE -> RgbColor(248, 247, 240)
    StickerGuess.YELLOW -> RgbColor(255, 214, 0)
    StickerGuess.RED -> RgbColor(205, 36, 48)
    StickerGuess.ORANGE -> RgbColor(255, 132, 0)
    StickerGuess.GREEN -> RgbColor(0, 158, 84)
    StickerGuess.BLUE -> RgbColor(0, 86, 190)
    StickerGuess.UNKNOWN -> RgbColor(96, 98, 91)
}


val canonicalStickerGuesses = listOf(
    StickerGuess.WHITE,
    StickerGuess.YELLOW,
    StickerGuess.RED,
    StickerGuess.ORANGE,
    StickerGuess.GREEN,
    StickerGuess.BLUE
)

private fun rgbToOpenCvLab(rgb: RgbColor): LabColor {
    fun linear(c: Double): Double = if (c <= .04045) c / 12.92 else Math.pow((c + .055) / 1.055, 2.4)

    val r = linear(rgb.r.coerceIn(0, 255) / 255.0)
    val g = linear(rgb.g.coerceIn(0, 255) / 255.0)
    val b = linear(rgb.b.coerceIn(0, 255) / 255.0)

    val x = (r * .4124564 + g * .3575761 + b * .1804375) / .95047
    val y = (r * .2126729 + g * .7151522 + b * .0721750)
    val z = (r * .0193339 + g * .1191920 + b * .9503041) / 1.08883

    fun f(t: Double): Double {
        val e = 216.0 / 24389.0
        val k = 24389.0 / 27.0
        return if (t > e) Math.cbrt(t) else (k * t + 16.0) / 116.0
    }

    val fx = f(x)
    val fy = f(y)
    val fz = f(z)
    val l = 116.0 * fy - 16.0
    val a = 500.0 * (fx - fy)
    val bb = 200.0 * (fy - fz)
    return LabColor(l * 255.0 / 100.0, a + 128.0, bb + 128.0)
}

fun canonicalColorSample(guess: StickerGuess): ColorSample {
    val rgb = idealDisplayRgb(guess)
    return ColorSample(rgbToOpenCvLab(rgb), rgb)
}

fun canonicalGuess(rgb: RgbColor): Pair<StickerGuess, Double> {
    val sample = ColorSample(rgbToOpenCvLab(rgb), rgb)
    val ranked = canonicalStickerGuesses
        .map { it to cubeColorDistance(sample, canonicalColorSample(it)) }
        .sortedBy { it.second }
    val best = ranked[0]
    val second = ranked[1]
    val confidence = ((second.second - best.second) / (second.second + 8.0))
        .coerceIn(.12, 1.0)
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
    val lab = sqrt(dl*dl + da*da + db*db)

    val sf = sample.rgb.features()
    val rf = reference.rgb.features()
    val saturation = abs(sf.saturation - rf.saturation) * 54.0
    val chromaticity = sqrt(
        (sf.rn-rf.rn)*(sf.rn-rf.rn) +
        (sf.gn-rf.gn)*(sf.gn-rf.gn) +
        (sf.bn-rf.bn)*(sf.bn-rf.bn)
    ) * 115.0

    val hue = if (sf.saturation > .24 && rf.saturation > .24) {
        circularHueDistance(sf.hue, rf.hue) * .42
    } else 0.0


    val bothRedOrange = sf.rn > .56 && rf.rn > .56 &&
        (sf.hue < 52.0 || sf.hue > 330.0) && (rf.hue < 52.0 || rf.hue > 330.0)
    val redOrange = if (bothRedOrange) {
        abs(sf.redOrangeAxis - rf.redOrangeAxis) * 245.0
    } else 0.0


    val crossesRedOrangeBoundary =
        (sf.redOrangeAxis > .035 && rf.redOrangeAxis < .012) ||
        (rf.redOrangeAxis > .035 && sf.redOrangeAxis < .012)
    val redOrangeBoundaryPenalty = if (bothRedOrange && crossesRedOrangeBoundary) 18.0 else 0.0

    val neutralPenalty = when {
        rf.saturation < .30 && sf.saturation > .38 -> (sf.saturation - .38) * 85.0
        rf.saturation > .48 && sf.saturation < .28 -> (.28 - sf.saturation) * 60.0
        else -> 0.0
    }

    return lab + saturation + chromaticity + hue + redOrange + redOrangeBoundaryPenalty + neutralPenalty
}
