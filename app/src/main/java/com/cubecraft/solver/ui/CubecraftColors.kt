package com.cubecraft.solver.ui

import androidx.compose.ui.graphics.Color
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.scanner.RgbColor
import com.cubecraft.solver.scanner.StickerGuess
import com.cubecraft.solver.scanner.idealDisplayRgb

val AppBg = Color(0xFF080A0C)
val Panel = Color(0xFF0D1013)
val Panel2 = Color(0xFF14191E)
val Viewport = Color(0xFF050607)
val Ink = Color(0xFFE9EEF2)
val InkSoft = Color(0xFFA9B2BA)
val Muted = Color(0xFF626B74)
val Outline = Color(0xFF252C33)
val Accent = Color(0xFF69E6B4)
val AccentSoft = Color(0xFF10241D)
val Success = Color(0xFF69E6B4)
val SuccessSoft = Color(0xFF10241D)
val Danger = Color(0xFFFF665E)
val DangerSoft = Color(0xFF2A1212)
val SignalAmber = Color(0xFFFFA344)
val CameraChrome = Color(0xE6080A0C)
val Metal = Color(0xFF171C21)
val MetalLight = Color(0xFF2C343C)
val MetalDark = Color(0xFF090B0D)

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
