package com.cubecraft.solver.ui

import androidx.compose.ui.graphics.Color
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.scanner.RgbColor
import com.cubecraft.solver.scanner.StickerGuess
import com.cubecraft.solver.scanner.idealDisplayRgb

// Graphite surfaces, glacier-blue actions; puzzle sticker colors stay independent.
val AppBg = Color(0xFF101217)
val Panel = Color(0xFF191C23)
val Panel2 = Color(0xFF242832)
val Viewport = Color(0xFF14171D)
val Ink = Color(0xFFF1F3F8)
val InkSoft = Color(0xFFB2BAC9)
val Muted = Color(0xFF8993A5)
val Outline = Color(0xFF333947)
val Accent = Color(0xFFB0C7FF)
val AccentSoft = Color(0xFF26334D)
val Success = Color(0xFF99D9B5)
val SuccessSoft = Color(0xFF1C3027)
val Danger = Color(0xFFFFABA5)
val DangerSoft = Color(0xFF392426)
val SignalAmber = Color(0xFFF1C887)
val CameraChrome = Color(0xE6101217)
val Metal = Color(0xFF1C212B)
val MetalLight = Color(0xFF40495A)
val MetalDark = Color(0xFF0D1015)

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
