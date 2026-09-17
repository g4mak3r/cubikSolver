package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Face

data class ValidationReport(val ok:Boolean,val messages:List<String>)

class CubeValidator(private val three: Min2PhaseSolver = Min2PhaseSolver()) {
    fun validate(state: CubeState): ValidationReport {
        val messages=mutableListOf<String>()
        val expected=state.size*state.size
        state.colorCounts().forEach { (face,count) -> if(count!=expected) messages += "${face.symbol}: $count stickers, expected $expected" }
        Face.entries.forEach { face ->
            val center=state.faceColors(face)[expected/2]
            if(center!=face) messages += "Center of ${face.symbol} does not match scan identity"
        }
        if(messages.isEmpty()) {
            val skeleton=state.reducedSkeleton3x3()
            three.validate(skeleton)?.let { messages += "Outer-piece validation: $it" }
        }
        return ValidationReport(messages.isEmpty(), if(messages.isEmpty()) listOf("Color counts, centers and 3x3 skeleton are physically consistent.") else messages)
    }
}
