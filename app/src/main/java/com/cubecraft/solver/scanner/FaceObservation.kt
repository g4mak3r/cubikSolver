package com.cubecraft.solver.scanner

import com.cubecraft.solver.model.Face

data class NormalizedPoint(val x: Float, val y: Float)

enum class StickerGuess(val label: String) {
    WHITE("W"), YELLOW("Y"), RED("R"), ORANGE("O"), GREEN("G"), BLUE("B"), UNKNOWN("?")
}

data class LiveSticker(
    val rgb: RgbColor,
    val guess: StickerGuess,
    val confidence: Float
)

data class FaceObservation(
    val samples: List<ColorSample>,
    val quality: Float,
    /** True only when a new contour was detected in this exact frame. */
    val detected: Boolean,
    /** True while the AR tracker owns a quadrilateral, including a short loss grace period. */
    val tracked: Boolean = detected,
    /** TL, TR, BR, BL in normalized analysis-frame coordinates. */
    val corners: List<NormalizedPoint>? = null,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val stickers: List<LiveSticker> = emptyList(),
    val timestampMs: Long = System.currentTimeMillis()
) {
    val uncertainCount: Int get() = stickers.count { it.confidence < 0.56f }
}

data class CapturedFace(
    val face: Face,
    val samples: List<ColorSample>,
    val quality: Float,
    val rotationQuarterTurns: Int = 0
) {
    fun rotatedSamples(size: Int): List<ColorSample> {
        var out = samples
        repeat((rotationQuarterTurns % 4 + 4) % 4) {
            val src = out
            out = List(size * size) { idx ->
                val r = idx / size; val c = idx % size
                src[(size - 1 - c) * size + r]
            }
        }
        return out
    }
}

data class ClassifiedScan(
    val faces: Map<Face, List<Face>>,
    val palette: Map<Face, RgbColor>,
    val meanDistance: Double
)
