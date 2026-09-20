package com.cubecraft.solver.scanner

import com.cubecraft.solver.model.Face

data class ScanPose(
    val face: Face,
    val title: String
)

val scanSequence = listOf(
    ScanPose(Face.F, "START WITH ANY FACE"),
    ScanPose(Face.R, "TURN THE CUBE LEFT"),
    ScanPose(Face.B, "TURN THE CUBE LEFT"),
    ScanPose(Face.L, "TURN THE CUBE LEFT"),
    ScanPose(Face.U, "RETURN TO THE FIRST FACE, THEN TILT UP"),
    ScanPose(Face.D, "KEEP THE FIRST FACE FRONT, THEN TILT DOWN")
)
