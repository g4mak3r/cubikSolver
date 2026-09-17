package com.cubecraft.solver.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.cubecraft.solver.model.*
import com.cubecraft.solver.scanner.RgbColor
import kotlin.math.*

private data class V3(val x:Float,val y:Float,val z:Float)
private data class CamV(val x:Float,val y:Float,val z:Float)
private data class Poly(val points:List<Offset>,val depth:Float,val color:Color,val stroke:Color,val strokeWidth:Float)

@Composable
fun Cube3D(
    cube:CubeState,
    revision:Int,
    palette:Map<Face,RgbColor>,
    highlight:Move?,
    modifier:Modifier=Modifier
){
    var yaw by remember { mutableFloatStateOf(-0.56f) }
    var pitch by remember { mutableFloatStateOf(0.43f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    val transition=rememberInfiniteTransition(label="guide")
    val pulse by transition.animateFloat(0f,1f,infiniteRepeatable(tween(700,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="pulse")
    val ghostAngle=if(highlight==null) 0f else (8f+8f*pulse) * if(highlight.quarterTurns==3) -1 else 1
    val stickers=remember(revision,cube){ cube.visibleStickers() }

    Canvas(modifier.pointerInput(Unit){ detectTransformGestures { _, pan, z, _ ->
        yaw += pan.x*.007f; pitch=(pitch-pan.y*.007f).coerceIn(-1.15f,1.15f); zoom=(zoom*z).coerceIn(.62f,1.8f)
    }}) {
        val n=cube.size; val center=Offset(size.width/2,size.height/2); val base=size.minDimension*.72f*zoom
        fun cellCoord(i:Int)=(-.5f+(i+.5f)/n)
        fun rotateGhost(v:V3,key:StickerKey):V3 {
            val m=highlight?:return v; if(!inMove(key,m,n)) return v
            val baseAngle=Math.toRadians(ghostAngle.toDouble()).toFloat()
            val cwSign=when(m.face){ Face.R,Face.U,Face.F->-1f; Face.L,Face.D,Face.B->1f }
            val a=baseAngle*cwSign
            val ca=cos(a); val sa=sin(a)
            return when(m.face){
                Face.R,Face.L->V3(v.x,v.y*ca-v.z*sa,v.y*sa+v.z*ca)
                Face.U,Face.D->V3(v.x*ca+v.z*sa,v.y,-v.x*sa+v.z*ca)
                Face.F,Face.B->V3(v.x*ca-v.y*sa,v.x*sa+v.y*ca,v.z)
            }
        }
        fun camera(v:V3):CamV {
            val cy=cos(yaw); val sy=sin(yaw); val x1=v.x*cy+v.z*sy; val z1=-v.x*sy+v.z*cy
            val cp=cos(pitch); val sp=sin(pitch); val y2=v.y*cp-z1*sp; val z2=v.y*sp+z1*cp
            return CamV(x1,y2,z2)
        }
        fun project(c:CamV):Offset { val focal=2.25f; val k=focal/(focal-c.z); return Offset(center.x+c.x*base*k,center.y-c.y*base*k) }
        fun quad(key:StickerKey,half:Float,offset:Float):List<V3>{
            val cx=if(key.nx!=0) key.nx*.5f else cellCoord(key.x)
            val cy=if(key.ny!=0) key.ny*.5f else cellCoord(key.y)
            val cz=if(key.nz!=0) key.nz*.5f else cellCoord(key.z)
            val c=V3(cx+key.nx*offset,cy+key.ny*offset,cz+key.nz*offset)
            val (u,v)=when { key.nz!=0->V3(1f,0f,0f) to V3(0f,1f,0f); key.nx!=0->V3(0f,0f,1f) to V3(0f,1f,0f); else->V3(1f,0f,0f) to V3(0f,0f,1f) }
            return listOf(
                V3(c.x-u.x*half-v.x*half,c.y-u.y*half-v.y*half,c.z-u.z*half-v.z*half),
                V3(c.x+u.x*half-v.x*half,c.y+u.y*half-v.y*half,c.z+u.z*half-v.z*half),
                V3(c.x+u.x*half+v.x*half,c.y+u.y*half+v.y*half,c.z+u.z*half+v.z*half),
                V3(c.x-u.x*half+v.x*half,c.y-u.y*half+v.y*half,c.z-u.z*half+v.z*half)
            )
        }
        fun normalVisible(key:StickerKey):Boolean {
            var v=V3(key.nx.toFloat(),key.ny.toFloat(),key.nz.toFloat())
            // Ghost rotation also rotates normals adequately by treating them as vectors around origin.
            if(highlight!=null && inMove(key,highlight,n)) {
                val m=highlight; val a=Math.toRadians(ghostAngle.toDouble()).toFloat()*when(m.face){Face.R,Face.U,Face.F->-1f;else->1f}
                val ca=cos(a); val sa=sin(a)
                v=when(m.face){ Face.R,Face.L->V3(v.x,v.y*ca-v.z*sa,v.y*sa+v.z*ca); Face.U,Face.D->V3(v.x*ca+v.z*sa,v.y,-v.x*sa+v.z*ca); Face.F,Face.B->V3(v.x*ca-v.y*sa,v.x*sa+v.y*ca,v.z) }
            }
            return camera(v).z>0f
        }
        val polys=mutableListOf<Poly>()
        for(st in stickers){
            if(!normalVisible(st.key)) continue
            val hi=highlight?.let{inMove(st.key,it,n)}==true
            val dark=quad(st.key,.49f/n,0f).map{ project(camera(rotateGhost(it,st.key))) }
            val darkDepth=quad(st.key,.49f/n,0f).map{camera(rotateGhost(it,st.key)).z}.average().toFloat()
            polys+=Poly(dark,darkDepth,Color(0xFF090A0C),if(hi) Accent else Color.Black,if(hi) 4f else 1f)
            val q=quad(st.key,.405f/n,.006f).map{ project(camera(rotateGhost(it,st.key))) }
            val dep=quad(st.key,.405f/n,.006f).map{camera(rotateGhost(it,st.key)).z}.average().toFloat()
            polys+=Poly(q,dep,faceColor(st.color,palette),if(hi) Accent.copy(alpha=.9f) else Color(0x33000000),if(hi) 3f else 1f)
        }
        polys.sortedBy{it.depth}.forEach{p->
            val path=Path().apply{ moveTo(p.points[0].x,p.points[0].y); p.points.drop(1).forEach{lineTo(it.x,it.y)}; close() }
            drawPath(path,p.color); drawPath(path,p.stroke,style=Stroke(p.strokeWidth))
        }
    }
}

private fun inMove(k:StickerKey,m:Move,n:Int)=when(m.face){
    Face.R->k.x>=n-m.width; Face.L->k.x<m.width; Face.U->k.y>=n-m.width; Face.D->k.y<m.width; Face.F->k.z>=n-m.width; Face.B->k.z<m.width
}
