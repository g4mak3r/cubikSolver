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

        // Speed-cube oranges can be almost pure red in HSV. A photographed flame orange such as
        // #FF2100 has H≈7.8°, which used to fall into our RED bucket. The useful distinction is
        // that orange carries clearly more green than blue, while the cube's red is neutral/cool
        // on that axis. Nudge only this tiny near-zero warm-red region away from the red boundary.
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
    /** Illumination-resistant discriminator: positive = warmer/oranger, negative = cooler/redder. */
    val redOrangeAxis: Double
)

data class ColorSample(val lab: LabColor, val rgb: RgbColor)

/** Canonical display colors. Camera RGB is evidence for classification, never UI paint. */
fun idealDisplayRgb(guess: StickerGuess): RgbColor = when (guess) {
    StickerGuess.WHITE -> RgbColor(248, 247, 240)
    StickerGuess.YELLOW -> RgbColor(255, 214, 0)
    StickerGuess.RED -> RgbColor(205, 36, 48)
    StickerGuess.ORANGE -> RgbColor(255, 132, 0)
    StickerGuess.GREEN -> RgbColor(0, 158, 84)
    StickerGuess.BLUE -> RgbColor(0, 86, 190)
    StickerGuess.UNKNOWN -> RgbColor(96, 98, 91)
}

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
 *
 * Red/orange needs an extra axis because modern fluorescent oranges can sit only a few HSV degrees
 * away from red. Normalized (green - blue) is much more stable for that pair than a hard RGB value.
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

    // Special red/orange separation. Only activate when both colors are strongly red-dominant,
    // so green/blue/yellow are unaffected. This is based on relative channels, not fixed exposure.
    val bothRedOrange = sf.rn > .56 && rf.rn > .56 &&
        (sf.hue < 52.0 || sf.hue > 330.0) && (rf.hue < 52.0 || rf.hue > 330.0)
    val redOrange = if (bothRedOrange) {
        abs(sf.redOrangeAxis - rf.redOrangeAxis) * 245.0
    } else 0.0

    // If one side of the comparison is clearly orange-like and the other clearly red-like,
    // add a categorical margin. The gap is intentionally wide enough to tolerate auto-WB drift.
    val crossesRedOrangeBoundary =
        (sf.redOrangeAxis > .035 && rf.redOrangeAxis < .012) ||
        (rf.redOrangeAxis > .035 && sf.redOrangeAxis < .012)
    val redOrangeBoundaryPenalty = if (bothRedOrange && crossesRedOrangeBoundary) 18.0 else 0.0

    // Strongly separate neutral white from chromatic yellow/orange even under warm auto-WB.
    val neutralPenalty = when {
        rf.saturation < .30 && sf.saturation > .38 -> (sf.saturation - .38) * 85.0
        rf.saturation > .48 && sf.saturation < .28 -> (.28 - sf.saturation) * 60.0
        else -> 0.0
    }

    return lab + saturation + chromaticity + hue + redOrange + redOrangeBoundaryPenalty + neutralPenalty
}
