package com.cubecraft.solver.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.scanner.RgbColor
import kotlin.math.floor

@Composable
fun FaceGrid(gridSize:Int,values:List<Face>,palette:Map<Face,RgbColor>,modifier:Modifier=Modifier,onTap:((Int)->Unit)?=null){
    Canvas(modifier.pointerInput(onTap){ if(onTap!=null) detectTapGestures{p->
        val cell=this.size.width.toFloat()/gridSize; val c=floor(p.x/cell).toInt().coerceIn(0,gridSize-1); val r=floor(p.y/cell).toInt().coerceIn(0,gridSize-1); onTap(r*gridSize+c)
    }}){
        val cell=this.size.width/gridSize
        for(r in 0 until gridSize) for(c in 0 until gridSize){
            val idx=r*gridSize+c; val pad=cell*.07f
            drawRoundRect(faceColor(values[idx],palette),Offset(c*cell+pad,r*cell+pad),Size(cell-2*pad,cell-2*pad),cornerRadius=androidx.compose.ui.geometry.CornerRadius(cell*.12f))
            drawRoundRect(Color.Black.copy(alpha=.35f),Offset(c*cell+pad,r*cell+pad),Size(cell-2*pad,cell-2*pad),cornerRadius=androidx.compose.ui.geometry.CornerRadius(cell*.12f),style=Stroke(1.5f))
        }
    }
}
