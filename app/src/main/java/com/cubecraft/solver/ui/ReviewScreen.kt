package com.cubecraft.solver.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.scanner.RgbColor
import com.cubecraft.solver.solver.ValidationReport

@Composable
fun ReviewScreen(
    size: Int,
    faces: Map<Face, List<Face>>,
    palette: Map<Face, RgbColor>,
    report: ValidationReport?,
    message: String?,
    onRotate: (Face) -> Unit,
    onSetColor: (Face, Int, Face) -> Unit,
    onAccept: () -> Unit,
    onRescan: () -> Unit,
    onBack: () -> Unit
) {
    var editingFace by remember { mutableStateOf<Face?>(null) }

    Column(
        Modifier.fillMaxSize().background(AppBg).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("←", color = Ink, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
            }
            Spacer(Modifier.weight(1f))
        }

        Box(
            Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            CubeNet(size, faces, palette) { editingFace = it }
        }

        if (report?.ok == false) {
            Text(
                report.messages.firstOrNull() ?: message ?: "CHECK STATE",
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                color = Danger,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 12.sp,
                maxLines = 2
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = onRescan,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(3.dp),
                border = BorderStroke(1.dp, Outline)
            ) {
                Text("RESCAN", color = InkSoft, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            }
            Button(
                onClick = onAccept,
                enabled = report?.ok == true,
                modifier = Modifier.weight(1.35f).height(48.dp),
                shape = RoundedCornerShape(3.dp)
            ) {
                Text(
                    "CONTINUE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .7.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }

    editingFace?.let { face ->
        FaceEditor(
            size = size,
            face = face,
            colors = faces.getValue(face),
            palette = palette,
            onSetColor = { index, color -> onSetColor(face, index, color) },
            onRotate = { onRotate(face) },
            onDismiss = { editingFace = null }
        )
    }
}

@Composable
private fun FaceEditor(
    size: Int,
    face: Face,
    colors: List<Face>,
    palette: Map<Face, RgbColor>,
    onSetColor: (Int, Face) -> Unit,
    onRotate: () -> Unit,
    onDismiss: () -> Unit
) {
    val center = size * size / 2
    var brush by remember(face) { mutableStateOf<Face?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier.fillMaxSize().background(AppBg).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp)) {
                    Text("←", color = Ink, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRotate) {
                    Text("↻", color = InkSoft, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
                }
            }

            Spacer(Modifier.weight(.3f))

            PaintableFace(
                size = size,
                colors = colors,
                palette = palette,
                brush = brush,
                locked = center,
                onPaint = { index ->
                    brush?.let { onSetColor(index, it) }
                }
            )

            Spacer(Modifier.weight(.4f))

            FacePalette(palette, brush) { brush = it }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(3.dp)
            ) {
                Text(
                    "DONE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .8.sp
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PaintableFace(
    size: Int,
    colors: List<Face>,
    palette: Map<Face, RgbColor>,
    brush: Face?,
    locked: Int,
    onPaint: (Int) -> Unit
) {
    val latestPaint by rememberUpdatedState(onPaint)
    val enabled = brush != null

    Column(
        Modifier.fillMaxWidth(if (size == 3) .78f else .92f)
            .aspectRatio(1f)
            .background(MetalDark, RoundedCornerShape(7.dp))
            .border(1.dp, MetalLight, RoundedCornerShape(7.dp))
            .padding(if (size == 3) 5.dp else 3.dp)
            .pointerInput(size, enabled) {
                if (!enabled) return@pointerInput

                fun cellAt(position: Offset): Int? {
                    if (position.x !in 0f..this.size.width.toFloat() ||
                        position.y !in 0f..this.size.height.toFloat()
                    ) return null
                    val col = (position.x / (this.size.width.toFloat() / size))
                        .toInt().coerceIn(0, size - 1)
                    val row = (position.y / (this.size.height.toFloat() / size))
                        .toInt().coerceIn(0, size - 1)
                    return row * size + col
                }

                awaitEachGesture {
                    var last = -1
                    val down = awaitFirstDown(requireUnconsumed = false)
                    cellAt(down.position)?.takeIf { it != locked }?.let {
                        last = it
                        latestPaint(it)
                    }
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (change.pressed) {
                                val cell = cellAt(change.position)
                                if (cell != null && cell != locked && cell != last) {
                                    last = cell
                                    latestPaint(cell)
                                }
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        for (row in 0 until size) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0 until size) {
                    val index = row * size + col
                    Box(
                        Modifier.weight(1f).fillMaxHeight().padding(1.5.dp)
                            .background(
                                faceColor(colors[index], palette),
                                RoundedCornerShape(if (size == 3) 5.dp else 3.dp)
                            )
                            .border(
                                if (index == locked) 1.5.dp else .6.dp,
                                if (index == locked) Ink.copy(alpha = .65f)
                                else Color.Black.copy(alpha = .3f),
                                RoundedCornerShape(if (size == 3) 5.dp else 3.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun FacePalette(
    palette: Map<Face, RgbColor>,
    selected: Face?,
    onSelect: (Face) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Face.entries.forEach { face ->
            val active = selected == face
            val color = faceColor(face, palette)
            Surface(
                modifier = Modifier.weight(1f).height(46.dp),
                onClick = { onSelect(face) },
                color = color,
                shape = RoundedCornerShape(3.dp),
                border = BorderStroke(
                    if (active) 3.dp else 1.dp,
                    if (active) Accent else Color.Black.copy(alpha = .32f)
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        face.symbol.toString(),
                        color = if (color.luminance() > .52f) Color.Black else Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CubeNet(
    size: Int,
    faces: Map<Face, List<Face>>,
    palette: Map<Face, RgbColor>,
    onFace: (Face) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 5.dp
        val side = ((maxWidth - gap * 3) / 4).coerceAtMost(if (size == 3) 92.dp else 94.dp)

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Spacer(Modifier.size(side))
                NetFace(Face.U, side, size, faces, palette, onFace)
                Spacer(Modifier.size(side))
                Spacer(Modifier.size(side))
            }
            Spacer(Modifier.height(gap))
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                NetFace(Face.L, side, size, faces, palette, onFace)
                NetFace(Face.F, side, size, faces, palette, onFace)
                NetFace(Face.R, side, size, faces, palette, onFace)
                NetFace(Face.B, side, size, faces, palette, onFace)
            }
            Spacer(Modifier.height(gap))
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Spacer(Modifier.size(side))
                NetFace(Face.D, side, size, faces, palette, onFace)
                Spacer(Modifier.size(side))
                Spacer(Modifier.size(side))
            }
        }
    }
}

@Composable
private fun NetFace(
    face: Face,
    side: Dp,
    size: Int,
    faces: Map<Face, List<Face>>,
    palette: Map<Face, RgbColor>,
    onFace: (Face) -> Unit
) {
    Box(
        Modifier.size(side)
            .border(1.dp, Outline, RoundedCornerShape(4.dp))
            .clickable { onFace(face) }
            .padding(2.dp)
    ) {
        FaceGrid(
            gridSize = size,
            values = faces.getValue(face),
            palette = palette,
            modifier = Modifier.fillMaxSize(),
            onTap = { onFace(face) }
        )
    }
}
