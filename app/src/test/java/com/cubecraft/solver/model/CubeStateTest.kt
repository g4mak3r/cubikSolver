package com.cubecraft.solver.model

import org.junit.Assert.*
import org.junit.Test

class CubeStateTest {
    @Test fun fourQuarterTurnsReturnToSolved(){
        for(n in listOf(3,5)) for(face in Face.entries){
            val c=CubeState(n); repeat(4){c.apply(Move(face))}; assertTrue("$n ${face.symbol}",c.isSolved())
        }
    }
    @Test fun moveThenInverseReturnsToSolved(){
        for(n in listOf(3,5)) for(face in Face.entries){
            val c=CubeState(n); val m=Move(face,1,1); c.apply(m); c.apply(m.inverse()); assertTrue(c.isSolved())
        }
    }
    @Test fun wideMoveThenInverseReturnsToSolved(){
        val c=CubeState(5); for(face in Face.entries){ val m=Move(face,2,1); c.apply(m); c.apply(m.inverse()); assertTrue(face.name,c.isSolved()) }
    }
    @Test fun colorCountsAreInvariant(){
        val c=CubeState(5); c.applyAll(Move.parseAlgorithm("Rw U F2 Lw' D R B2")); assertTrue(c.colorCounts().values.all{it==25})
    }
}
