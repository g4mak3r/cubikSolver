package com.cubecraft.solver.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.cubecraft.solver.model.*
import com.cubecraft.solver.scanner.RgbColor
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.*

private data class V3(val x:Float,val y:Float,val z:Float)
private data class CamV(val x:Float,val y:Float,val z:Float)
private data class Poly(val points:List<Offset>,val depth:Float,val color:Color,val stroke:Color,val strokeWidth:Float)
private data class StickerHit(val key: StickerKey, val points: List<Offset>, val depth: Float)

@Composable
fun Cube3D(
    cube:CubeState,
    revision:Int,
    palette:Map<Face,RgbColor>,
    highlight:Move?,
    modifier:Modifier=Modifier,
    paintColor: Face? = null,
    onPaintSticker: ((StickerKey, Face) -> Unit)? = null
){
    var yaw by remember { mutableFloatStateOf(-0.56f) }
    var pitch by remember { mutableFloatStateOf(0.43f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    val hitPolys = remember { AtomicReference<List<StickerHit>>(emptyList()) }
    val transition=rememberInfiniteTransition(label="guide")
    val pulse by transition.animateFloat(0f,1f,infiniteRepeatable(tween(700,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="pulse")
    val ghostAngle=if(highlight==null) 0f else (8f+8f*pulse) * if(highlight.quarterTurns==3) -1 else 1
    val stickers=remember(revision,cube){ cube.visibleStickers() }

    val gestures = modifier
        .pointerInput(Unit){ detectTransformGestures { _, pan, z, _ ->
            yaw += pan.x*.007f
            pitch=(pitch-pan.y*.007f).coerceIn(-1.15f,1.15f)
            zoom=(zoom*z).coerceIn(.62f,1.8f)
        }}
        .pointerInput(paintColor, revision) {
            if (paintColor != null && onPaintSticker != null) {
                detectTapGestures { point ->
                    val hit = hitPolys.get()
                        .filter { pointInPolygon(point, it.points) }
                        .maxByOrNull { it.depth }
                    if (hit != null) onPaintSticker(hit.key, paintColor)
                }
            }
        }

    Canvas(gestures) {
        val n=cube.size
        val center=Offset(size.width/2,size.height/2)
        val base=size.minDimension*.70f*zoom
        fun cellCoord(i:Int)=(-.5f+(i+.5f)/n)
        fun rotateGhost(v:V3,key:StickerKey):V3 {
            val m=highlight?:return v
            if(!inMove(key,m,n)) return v
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
        fun project(c:CamV):Offset {
            val focal=2.25f
            val k=focal/(focal-c.z)
            return Offset(center.x+c.x*base*k,center.y-c.y*base*k)
        }
        fun quad(key:StickerKey,half:Float,offset:Float):List<V3>{
            val cx=if(key.nx!=0) key.nx*.5f else cellCoord(key.x)
            val cy=if(key.ny!=0) key.ny*.5f else cellCoord(key.y)
            val cz=if(key.nz!=0) key.nz*.5f else cellCoord(key.z)
            val c=V3(cx+key.nx*offset,cy+key.ny*offset,cz+key.nz*offset)
            val (u,v)=when {
                key.nz!=0->V3(1f,0f,0f) to V3(0f,1f,0f)
                key.nx!=0->V3(0f,0f,1f) to V3(0f,1f,0f)
                else->V3(1f,0f,0f) to V3(0f,0f,1f)
            }
            return listOf(
                V3(c.x-u.x*half-v.x*half,c.y-u.y*half-v.y*half,c.z-u.z*half-v.z*half),
                V3(c.x+u.x*half-v.x*half,c.y+u.y*half-v.y*half,c.z+u.z*half-v.z*half),
                V3(c.x+u.x*half+v.x*half,c.y+u.y*half+v.y*half,c.z+u.z*half+v.z*half),
                V3(c.x-u.x*half+v.x*half,c.y-u.y*half+v.y*half,c.z-u.z*half+v.z*half)
            )
        }
        fun normalVisible(key:StickerKey):Boolean {
            var v=V3(key.nx.toFloat(),key.ny.toFloat(),key.nz.toFloat())
            if(highlight!=null && inMove(key,highlight,n)) {
                val m=highlight
                val a=Math.toRadians(ghostAngle.toDouble()).toFloat()*when(m.face){Face.R,Face.U,Face.F->-1f;else->1f}
                val ca=cos(a); val sa=sin(a)
                v=when(m.face){
                    Face.R,Face.L->V3(v.x,v.y*ca-v.z*sa,v.y*sa+v.z*ca)
                    Face.U,Face.D->V3(v.x*ca+v.z*sa,v.y,-v.x*sa+v.z*ca)
                    Face.F,Face.B->V3(v.x*ca-v.y*sa,v.x*sa+v.y*ca,v.z)
                }
            }
            return camera(v).z>0f
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Accent.copy(alpha = .10f), Color.Transparent),
                center = center,
                radius = base * .78f
            ),
            radius = base * .78f,
            center = center
        )

        fun coreNormal(face: Face): V3 = when(face) {
            Face.F -> V3(0f,0f,1f)
            Face.B -> V3(0f,0f,-1f)
            Face.U -> V3(0f,1f,0f)
            Face.D -> V3(0f,-1f,0f)
            Face.R -> V3(1f,0f,0f)
            Face.L -> V3(-1f,0f,0f)
        }

        fun coreQuad(face: Face, h: Float): List<V3> = when(face) {
            Face.F -> listOf(V3(-h,-h,h),V3(h,-h,h),V3(h,h,h),V3(-h,h,h))
            Face.B -> listOf(V3(h,-h,-h),V3(-h,-h,-h),V3(-h,h,-h),V3(h,h,-h))
            Face.U -> listOf(V3(-h,h,h),V3(h,h,h),V3(h,h,-h),V3(-h,h,-h))
            Face.D -> listOf(V3(-h,-h,-h),V3(h,-h,-h),V3(h,-h,h),V3(-h,-h,h))
            Face.R -> listOf(V3(h,-h,h),V3(h,-h,-h),V3(h,h,-h),V3(h,h,h))
            Face.L -> listOf(V3(-h,-h,-h),V3(-h,-h,h),V3(-h,h,h),V3(-h,h,-h))
        }

        val polys=mutableListOf<Poly>()
        val hits=mutableListOf<StickerHit>()

        Face.entries.forEach { face ->
            if (camera(coreNormal(face)).z > 0f) {
                val q3 = coreQuad(face, .31f)
                val q = q3.map { project(camera(it)) }
                val depth = q3.map { camera(it).z }.average().toFloat()
                polys += Poly(
                    q,
                    depth,
                    MetalDark,
                    Metal.copy(alpha = .55f),
                    .8f
                )
            }
        }
        for(st in stickers){
            if(!normalVisible(st.key)) continue
            val hi=highlight?.let{inMove(st.key,it,n)}==true
            val outer3 = quad(st.key,.505f/n,-.006f)
            val outer = outer3.map { project(camera(rotateGhost(it,st.key))) }
            val outerDepth = outer3.map { camera(rotateGhost(it,st.key)).z }.average().toFloat()
            polys += Poly(
                outer,
                outerDepth,
                MetalDark,
                if (hi) Accent.copy(alpha = .85f) else MetalLight,
                if (hi) 3.4f else 1.2f
            )

            val body3 = quad(st.key,.474f/n,-.001f)
            val body = body3.map { project(camera(rotateGhost(it,st.key))) }
            val bodyDepth = body3.map { camera(rotateGhost(it,st.key)).z }.average().toFloat()
            polys += Poly(body, bodyDepth, Metal, MetalLight.copy(alpha = .72f), 1f)

            val stickerColor = faceColor(st.color,palette)
            val glow3 = quad(st.key,.425f/n,.005f)
            val glow = glow3.map { project(camera(rotateGhost(it,st.key))) }
            val glowDepth = glow3.map { camera(rotateGhost(it,st.key)).z }.average().toFloat()
            polys += Poly(glow, glowDepth, stickerColor.copy(alpha = .20f), stickerColor.copy(alpha = .32f), 2f)

            val sticker3 = quad(st.key,.385f/n,.009f)
            val q = sticker3.map { project(camera(rotateGhost(it,st.key))) }
            val dep = sticker3.map { camera(rotateGhost(it,st.key)).z }.average().toFloat()
            val paintStroke = if (paintColor != null) Color.White.copy(alpha=.75f) else stickerColor.copy(alpha=.42f)
            polys += Poly(
                q,
                dep,
                stickerColor,
                if (hi) Color.White.copy(alpha=.92f) else paintStroke,
                if (hi) 2.8f else if (paintColor != null) 1.6f else .9f
            )
            hits += StickerHit(st.key, q, dep)
        }
        hitPolys.set(hits)
        polys.sortedBy{it.depth}.forEach{p->
            val path=Path().apply{
                moveTo(p.points[0].x,p.points[0].y)
                p.points.drop(1).forEach{lineTo(it.x,it.y)}
                close()
            }
            drawPath(path,p.color)
            drawPath(path,p.stroke,style=Stroke(p.strokeWidth))
        }


        highlight?.let { move ->
            fun faceNormal(face: Face): V3 = when(face) {
                Face.F -> V3(0f,0f,1f)
                Face.B -> V3(0f,0f,-1f)
                Face.U -> V3(0f,1f,0f)
                Face.D -> V3(0f,-1f,0f)
                Face.R -> V3(1f,0f,0f)
                Face.L -> V3(-1f,0f,0f)
            }
            fun facePoint(face: Face, u: Float, v: Float, outward: Float = .565f): V3 = when(face) {

                Face.F -> V3(u, v, outward)
                Face.B -> V3(-u, v, -outward)
                Face.U -> V3(u, outward, -v)
                Face.D -> V3(u, -outward, v)
                Face.R -> V3(outward, v, -u)
                Face.L -> V3(-outward, v, u)
            }
            fun drawHead(from: Offset, tip: Offset) {
                val a = atan2(tip.y-from.y, tip.x-from.x)
                val len = 18f
                val wing = .62f
                val left = Offset(tip.x-len*cos(a-wing), tip.y-len*sin(a-wing))
                val right = Offset(tip.x-len*cos(a+wing), tip.y-len*sin(a+wing))
                val head = Path().apply {
                    moveTo(tip.x,tip.y)
                    lineTo(left.x,left.y)
                    lineTo(right.x,right.y)
                    close()
                }
                drawPath(head, Color.White.copy(alpha=.95f))
                val innerLen = 14f
                val il = Offset(tip.x-innerLen*cos(a-wing), tip.y-innerLen*sin(a-wing))
                val ir = Offset(tip.x-innerLen*cos(a+wing), tip.y-innerLen*sin(a+wing))
                val inner = Path().apply {
                    moveTo(tip.x,tip.y)
                    lineTo(il.x,il.y)
                    lineTo(ir.x,ir.y)
                    close()
                }
                drawPath(inner, Accent)
            }

            if (camera(faceNormal(move.face)).z > -.05f) {
                val clockwise = move.quarterTurns != 3
                val startDeg = if (clockwise) 205f else -25f
                val sweepDeg = if (clockwise) -230f else 230f
                val radius = .37f + .015f*pulse
                val samples = 34
                val points = List(samples) { i ->
                    val t = i.toFloat()/(samples-1)
                    val deg = startDeg + sweepDeg*t
                    val rad = Math.toRadians(deg.toDouble()).toFloat()
                    project(camera(facePoint(move.face, radius*cos(rad), radius*sin(rad))))
                }
                val arrowPath = Path().apply {
                    moveTo(points.first().x,points.first().y)
                    points.drop(1).forEach { lineTo(it.x,it.y) }
                }
                drawPath(
                    arrowPath,
                    Color.White.copy(alpha=.92f),
                    style=Stroke(width=12f,cap=StrokeCap.Round,join=StrokeJoin.Round)
                )
                drawPath(
                    arrowPath,
                    Accent,
                    style=Stroke(width=6.5f,cap=StrokeCap.Round,join=StrokeJoin.Round)
                )
                drawHead(points[points.lastIndex-1], points.last())


                if (move.quarterTurns == 2) {
                    val mid = samples/2
                    drawHead(points[mid-1], points[mid])
                }
            }
        }
    }
}

private fun pointInPolygon(point: Offset, polygon: List<Offset>): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[j]
        val intersects = ((a.y > point.y) != (b.y > point.y)) &&
            (point.x < (b.x-a.x) * (point.y-a.y) / ((b.y-a.y).takeIf { abs(it) > 1e-5f } ?: 1e-5f) + a.x)
        if (intersects) inside = !inside
        j = i
    }
    return inside
}

private fun inMove(k:StickerKey,m:Move,n:Int)=when(m.face){
    Face.R->k.x>=n-m.width
    Face.L->k.x<m.width
    Face.U->k.y>=n-m.width
    Face.D->k.y<m.width
    Face.F->k.z>=n-m.width
    Face.B->k.z<m.width
}
