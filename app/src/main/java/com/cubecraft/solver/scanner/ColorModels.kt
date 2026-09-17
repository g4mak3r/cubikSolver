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
        return ColorFeatures(
            hue = hue,
            saturation = saturation,
            value = mx,
            rn = rr / sum,
            gn = gg / sum,
            bn = bb / sum
        )
    }
}

data class ColorFeatures(
    val hue: Double,
    val saturation: Double,
    val value: Double,
    val rn: Double,
    val gn: Double,
    val bn: Double
)

data class ColorSample(val lab: LabColor, val rgb: RgbColor)

fun circularHueDistance(a: Double, b: Double): Double {
    val d = abs(a - b) % 360.0
    return min(d, 360.0 - d)
}

/**
 * Distance tuned for cube stickers photographed by a phone camera.
 *
 * Plain Euclidean Lab overweights illumination shifts. On a warm scene that can make a white
 * sticker look closer to yellow. We keep Lab as the base signal, but deliberately give chroma,
 * saturation and hue more influence than absolute lightness. Hue is only trusted when both
 * colors are sufficiently saturated, so white/gray noise does not create arbitrary hue costs.
 */
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

    // Strongly separate neutral white from chromatic yellow/orange even under warm auto-WB.
    val neutralPenalty = when {
        rf.saturation < .30 && sf.saturation > .38 -> (sf.saturation - .38) * 85.0
        rf.saturation > .48 && sf.saturation < .28 -> (.28 - sf.saturation) * 60.0
        else -> 0.0
    }

    return lab + saturation + chromaticity + hue + neutralPenalty
}
