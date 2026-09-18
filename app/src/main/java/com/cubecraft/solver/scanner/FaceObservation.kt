package com.cubecraft.solver.scanner

import com.cubecraft.solver.model.Face

data class NormalizedPoint(val x: Float, val y: Float)

enum class StickerGuess(val label: String, val displayName: String) {
    WHITE("W", "WHITE"),
    YELLOW("Y", "YELLOW"),
    RED("R", "RED"),
    ORANGE("O", "ORANGE"),
    GREEN("G", "GREEN"),
    BLUE("B", "BLUE"),
    UNKNOWN("?", "COLOR")
}

data class LiveSticker(
    val rgb: RgbColor,
    val guess: StickerGuess,
    val confidence: Float
)

data class FaceObservation(
    val samples: List<ColorSample>,
    val quality: Float,
    
    val detected: Boolean,
    
    val tracked: Boolean = detected,
    
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
    
    val guesses: List<StickerGuess> = emptyList(),
    
    val manualGuesses: Map<Int, StickerGuess> = emptyMap(),
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

    fun rotatedGuesses(size: Int): List<StickerGuess> {
        var out = List(size * size) { idx ->
            manualGuesses[idx] ?: guesses.getOrElse(idx) { StickerGuess.UNKNOWN }
        }
        repeat((rotationQuarterTurns % 4 + 4) % 4) {
            val src = out
            out = List(size * size) { idx ->
                val r = idx / size; val c = idx % size
                src[(size - 1 - c) * size + r]
            }
        }
        return out
    }

    fun rotatedManualGuesses(size: Int): Map<Int, StickerGuess> {
        var out = manualGuesses
        repeat((rotationQuarterTurns % 4 + 4) % 4) {
            val next = mutableMapOf<Int, StickerGuess>()
            out.forEach { (idx, guess) ->
                val r = idx / size
                val c = idx % size
                val nr = c
                val nc = size - 1 - r
                next[nr * size + nc] = guess
            }
            out = next
        }
        return out
    }
}

data class ClassifiedScan(
    val faces: Map<Face, List<Face>>,
    val palette: Map<Face, RgbColor>,
    val colorFaces: Map<StickerGuess, Face>,
    val meanDistance: Double
)
