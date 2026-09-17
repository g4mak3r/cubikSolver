package com.cubecraft.solver.scanner

import kotlin.math.sqrt

data class LabColor(val l: Double, val a: Double, val b: Double) {
    fun distance(other: LabColor): Double {
        val dl=l-other.l; val da=a-other.a; val db=b-other.b
        return sqrt(dl*dl + da*da + db*db)
    }
}

data class RgbColor(val r: Int, val g: Int, val b: Int) {
    fun argb(alpha: Int = 255): Int = (alpha shl 24) or (r.coerceIn(0,255) shl 16) or (g.coerceIn(0,255) shl 8) or b.coerceIn(0,255)
}

data class ColorSample(val lab: LabColor, val rgb: RgbColor)
