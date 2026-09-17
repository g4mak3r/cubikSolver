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

    @Test fun deepCopyPreservesArbitraryScannedState(){
        val faces = mapOf(
            Face.U to listOf(Face.U,Face.R,Face.B, Face.F,Face.U,Face.D, Face.L,Face.B,Face.R),
            Face.R to listOf(Face.B,Face.U,Face.L, Face.D,Face.R,Face.F, Face.U,Face.D,Face.B),
            Face.F to listOf(Face.R,Face.L,Face.D, Face.B,Face.F,Face.U, Face.R,Face.B,Face.L),
            Face.D to listOf(Face.F,Face.D,Face.U, Face.L,Face.D,Face.R, Face.B,Face.F,Face.U),
            Face.L to listOf(Face.D,Face.F,Face.R, Face.U,Face.L,Face.B, Face.F,Face.R,Face.D),
            Face.B to listOf(Face.L,Face.B,Face.F, Face.R,Face.B,Face.L, Face.D,Face.U,Face.F)
        )
        val original = CubeState(3).also { it.loadFaces(faces) }
        val copy = original.deepCopy()

        assertEquals(original.snapshot(), copy.snapshot())
        assertNotSame(original, copy)

        copy.setStickerColor(copy.keyFromFaceCell(Face.U, 0, 0), Face.D)
        assertNotEquals(original.snapshot(), copy.snapshot())
    }
}
