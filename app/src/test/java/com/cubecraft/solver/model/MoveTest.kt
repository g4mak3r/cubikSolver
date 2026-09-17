package com.cubecraft.solver.model
import org.junit.Assert.*
import org.junit.Test
class MoveTest {
 @Test fun notationRoundTrip(){ for(s in listOf("R","U'","F2","Rw","Lw'","2Uw2","3Fw")){ val m=Move.parse(s)!!; assertEquals(m,Move.parse(m.notation())) } }
}
