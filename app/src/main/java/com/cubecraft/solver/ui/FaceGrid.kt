package com.cubecraft.solver.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.scanner.RgbColor
import kotlin.math.floor

@Composable
fun FaceGrid(
    gridSize: Int,
    values: List<Face>,
    palette: Map<Face, RgbColor>,
    modifier: Modifier = Modifier,
    onTap: ((Int) -> Unit)? = null
) {
    Canvas(
        modifier.pointerInput(onTap) {
            if (onTap != null) {
                detectTapGestures { point ->
                    val cell = size.width.toFloat() / gridSize
                    val col = floor(point.x / cell).toInt().coerceIn(0, gridSize - 1)
                    val row = floor(point.y / cell).toInt().coerceIn(0, gridSize - 1)
                    onTap(row * gridSize + col)
                }
            }
        }
    ) {
        val cell = size.width / gridSize
        for (row in 0 until gridSize) for (col in 0 until gridSize) {
            val index = row * gridSize + col
            val pad = cell * .055f
            val topLeft = Offset(col * cell + pad, row * cell + pad)
            val box = Size(cell - 2 * pad, cell - 2 * pad)
            val radius = CornerRadius(cell * .07f)
            val color = faceColor(values[index], palette)

            drawRoundRect(
                color.copy(alpha = .18f),
                Offset(topLeft.x - 1.5f, topLeft.y - 1.5f),
                Size(box.width + 3f, box.height + 3f),
                radius
            )
            drawRoundRect(color, topLeft, box, radius)
            drawRoundRect(
                Color.Black.copy(alpha = .42f),
                topLeft,
                box,
                radius,
                style = Stroke(1.2f)
            )
        }
    }
}
