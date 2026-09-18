package com.cubecraft.solver.scanner

import com.cubecraft.solver.model.Face

data class ScanPose(val face: Face, val title: String, val instruction: String)

val scanSequence = listOf(
    ScanPose(Face.F, "START WITH ANY FACE", ""),
    ScanPose(Face.R, "TURN LEFT", ""),
    ScanPose(Face.B, "TURN LEFT", ""),
    ScanPose(Face.L, "TURN LEFT", ""),
    ScanPose(Face.U, "RETURN TO {FRONT_CENTER} CENTER", "TILT DOWN"),
    ScanPose(Face.D, "RETURN TO {FRONT_CENTER} CENTER", "TILT UP")
)
