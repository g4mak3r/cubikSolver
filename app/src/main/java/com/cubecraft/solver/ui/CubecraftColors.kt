package com.cubecraft.solver.ui

import androidx.compose.ui.graphics.Color
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.scanner.RgbColor
import com.cubecraft.solver.scanner.StickerGuess

/*
 * cubikSolver visual system
 * -------------------------
 * Not "AI dashboard" chrome. Think late-80s/early-90s laboratory terminal: warm plastic,
 * black ink, one phosphor-green signal color, square geometry and restrained typography.
 */
val AppBg = Color(0xFFF0EDE2)
val Panel = Color(0xFFF8F5EA)
val Panel2 = Color(0xFFE4E0D3)
val Viewport = Color(0xFF171B18)
val Ink = Color(0xFF151713)
val InkSoft = Color(0xFF343831)
val Muted = Color(0xFF74766C)
val Outline = Color(0xFFB8B4A7)
val Accent = Color(0xFF1F6B43)
val AccentSoft = Color(0xFFDCE8DD)
val Success = Color(0xFF13733E)
val SuccessSoft = Color(0xFFDDEBDD)
val Danger = Color(0xFFAD3C2C)
val DangerSoft = Color(0xFFF1DDD6)
val SignalAmber = Color(0xFFD67A16)
val CameraChrome = Color(0xEA101410)

/*
 * These are intentionally *not* sampled from the user's physical cube.
 * Camera RGB is calibration data. Once a sticker has been classified, the digital cube uses a
 * clean canonical palette so red/orange (and every other pair) remain easy to distinguish.
 */
private val idealWhite = RgbColor(246, 243, 232)
private val idealYellow = RgbColor(255, 211, 0)
private val idealRed = RgbColor(199, 31, 48)
private val idealOrange = RgbColor(255, 133, 0) // unmistakable orange, never "almost red"
private val idealGreen = RgbColor(0, 158, 83)
private val idealBlue = RgbColor(0, 82, 184)

val defaultPalette = mapOf(
    Face.U to idealWhite,
    Face.D to idealYellow,
    Face.R to idealRed,
    Face.L to idealOrange,
    Face.F to idealGreen,
    Face.B to idealBlue
)

fun idealRgbForGuess(guess: StickerGuess): RgbColor = when (guess) {
    StickerGuess.WHITE -> idealWhite
    StickerGuess.YELLOW -> idealYellow
    StickerGuess.RED -> idealRed
    StickerGuess.ORANGE -> idealOrange
    StickerGuess.GREEN -> idealGreen
    StickerGuess.BLUE -> idealBlue
    StickerGuess.UNKNOWN -> RgbColor(92, 94, 86)
}

/**
 * Convert a photographed centre reference to one of six clean display colors.
 * This is display-only. The actual classifier continues to use the original calibrated RGB/Lab.
 */
private fun idealizeObserved(rgb: RgbColor): RgbColor {
    val f = rgb.features()
    return when {
        f.saturation < .28 && f.value > .42 -> idealWhite
        f.hue in 38.0..78.0 -> idealYellow
        f.hue in 72.0..175.0 -> idealGreen
        f.hue in 175.0..285.0 -> idealBlue
        f.hue in 12.0..38.0 -> idealOrange
        (f.hue < 12.0 || f.hue > 330.0) && f.redOrangeAxis > .032 -> idealOrange
        else -> idealRed
    }
}

fun faceColor(face: Face, palette: Map<Face, RgbColor>): Color {
    val observed = palette[face]
    val rgb = if (observed == null) defaultPalette.getValue(face) else idealizeObserved(observed)
    return Color(rgb.argb())
}
