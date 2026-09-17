package com.cubecraft.solver.ui

import androidx.compose.ui.graphics.Color
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.scanner.RgbColor

// CUBECRAFT 0.4 visual system: a light precision-instrument UI.
// High contrast is intentional: Ink on white/soft slate, white on blue actions.
val AppBg = Color(0xFFF3F6FA)
val Panel = Color(0xFFFFFFFF)
val Panel2 = Color(0xFFE9EFF7)
val Viewport = Color(0xFFE7EDF5)
val Ink = Color(0xFF152033)
val InkSoft = Color(0xFF344054)
val Muted = Color(0xFF667085)
val Outline = Color(0xFFD4DCE7)
val Accent = Color(0xFF2F6FED)
val AccentSoft = Color(0xFFE8F0FF)
val Success = Color(0xFF12805C)
val SuccessSoft = Color(0xFFE7F6F0)
val Danger = Color(0xFFD92D20)
val DangerSoft = Color(0xFFFFECEA)
val CameraChrome = Color(0xE6121A26)

val defaultPalette = mapOf(
    Face.U to RgbColor(242,242,238),
    Face.D to RgbColor(255,213,0),
    Face.R to RgbColor(190,30,55),
    Face.L to RgbColor(255,95,0),
    Face.F to RgbColor(0,155,72),
    Face.B to RgbColor(0,70,173)
)

fun faceColor(face: Face, palette: Map<Face,RgbColor>) =
    Color((palette[face] ?: defaultPalette.getValue(face)).argb())
