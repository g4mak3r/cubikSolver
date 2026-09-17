package com.cubecraft.solver.scanner

import com.cubecraft.solver.model.Face

data class ScanPose(val face: Face, val title: String, val instruction: String)

val scanSequence = listOf(
    ScanPose(Face.F, "FRONT", "Choose any face as FRONT. Keep the same top edge pointing upward. Its center color will be remembered."),
    ScanPose(Face.R, "RIGHT", "Rotate the whole cube left to reveal its RIGHT face. Do not roll it."),
    ScanPose(Face.B, "BACK", "Rotate left again to the BACK face. Keep the same top edge up."),
    ScanPose(Face.L, "LEFT", "Rotate left again to the LEFT face. Keep the cube level."),
    ScanPose(Face.U, "UP", "Return to the first face ({FRONT_CENTER} center), then tilt the cube down so the UP face looks at the camera."),
    ScanPose(Face.D, "DOWN", "Return to the first face ({FRONT_CENTER} center), then tilt the cube up so the DOWN face looks at the camera.")
)
