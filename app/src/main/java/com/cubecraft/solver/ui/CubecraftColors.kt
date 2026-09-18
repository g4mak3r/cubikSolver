package com.cubecraft.solver.ui

import androidx.compose.ui.graphics.Color
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.scanner.RgbColor
import com.cubecraft.solver.scanner.StickerGuess
import com.cubecraft.solver.scanner.idealDisplayRgb

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
private val idealWhite = idealDisplayRgb(StickerGuess.WHITE)
private val idealYellow = idealDisplayRgb(StickerGuess.YELLOW)
private val idealRed = idealDisplayRgb(StickerGuess.RED)
private val idealOrange = idealDisplayRgb(StickerGuess.ORANGE)
private val idealGreen = idealDisplayRgb(StickerGuess.GREEN)
private val idealBlue = idealDisplayRgb(StickerGuess.BLUE)

val defaultPalette = mapOf(
    Face.U to idealWhite,
    Face.D to idealYellow,
    Face.R to idealRed,
    Face.L to idealOrange,
    Face.F to idealGreen,
    Face.B to idealBlue
)

fun idealRgbForGuess(guess: StickerGuess): RgbColor = idealDisplayRgb(guess)

fun faceColor(face: Face, palette: Map<Face, RgbColor>): Color {
    val rgb = palette[face] ?: defaultPalette.getValue(face)
    return Color(rgb.argb())
}
