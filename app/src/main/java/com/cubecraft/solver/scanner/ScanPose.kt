package com.cubecraft.solver.scanner

import com.cubecraft.solver.model.Face

data class ScanPose(
    val face: Face,
    val title: String
)

val scanSequence = listOf(
    ScanPose(Face.F, "ANY FACE"),
    ScanPose(Face.R, "TURN LEFT"),
    ScanPose(Face.B, "TURN LEFT"),
    ScanPose(Face.L, "TURN LEFT"),
    ScanPose(Face.U, "FIND TARGET"),
    ScanPose(Face.D, "FIND TARGET")
)
