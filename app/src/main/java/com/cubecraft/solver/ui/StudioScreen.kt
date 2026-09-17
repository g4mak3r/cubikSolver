package com.cubecraft.solver.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubecraft.solver.model.*
import com.cubecraft.solver.scanner.RgbColor

@Composable
fun StudioScreen(
    size: Int, cube: CubeState, revision: Int, palette: Map<Face,RgbColor>, history: List<Move>,
    solution: List<Move>, solutionIndex: Int, guide: Move?, solving: Boolean, message: String?,
    canReturnToScan: Boolean, onBack: () -> Unit, onMove: (Move) -> Unit, onUndo: () -> Unit,
    onRedo: () -> Unit, onReturnScan: () -> Unit, onReset: () -> Unit, onSolve: () -> Unit,
    onNext: () -> Unit, onPrev: () -> Unit, onPaintSticker: (StickerKey, Face) -> Unit
) {
    var width by remember(size) { mutableIntStateOf(1) }
    var turns by remember { mutableIntStateOf(1) }
    var editMode by remember { mutableStateOf(false) }
    var paintColor by remember { mutableStateOf(Face.F) }

    Column(Modifier.fillMaxSize().background(AppBg).padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
                Text("‹  HOME", color = InkSoft, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.padding(start = 5.dp)) {
                Text("CUBECRAFT", color = Ink, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 1.sp)
                Text("$size × $size · DIGITAL TWIN", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { editMode = !editMode }) {
                Text(if (editMode) "DONE EDIT" else "EDIT COLORS", color = if (editMode) Accent else InkSoft, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }

        Spacer(Modifier.height(5.dp))
        Surface(
            Modifier.fillMaxWidth().weight(1f), color = Viewport,
            shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, Outline)
        ) {
            Box {
                Cube3D(
                    cube = cube,
                    revision = revision,
                    palette = palette,
                    highlight = if (editMode) null else guide,
                    modifier = Modifier.fillMaxSize(),
                    paintColor = if (editMode) paintColor else null,
                    onPaintSticker = if (editMode) onPaintSticker else null
                )
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(12.dp)
                        .background(Panel.copy(alpha = .86f), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        if (editMode) "TAP STICKER TO PAINT · DRAG TO ORBIT" else "DRAG TO ORBIT · PINCH TO ZOOM",
                        color = if (editMode) Accent else Muted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (editMode) {
            Spacer(Modifier.height(8.dp))
            PaintPalette(palette, paintColor) { paintColor = it }
        } else if (solution.isNotEmpty()) {
            Spacer(Modifier.height(8.dp)); GuideCard(guide, solutionIndex, solution.size, onPrev, onNext)
        }

        message?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = if (it.contains("not bundled") || it.contains("Invalid") || it.contains("exactly")) Danger else Muted, fontSize = 10.sp, maxLines = 3)
        }

        Spacer(Modifier.height(7.dp))
        Surface(color = Panel, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Outline)) {
            Column(Modifier.padding(9.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(1 to "CW", 3 to "CCW", 2 to "180°").forEach { (v,label) ->
                        if (turns == v) Button(
                            onClick = { turns = v }, enabled = !editMode, modifier = Modifier.weight(1f), contentPadding = PaddingValues(5.dp)
                        ) { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        else OutlinedButton(
                            onClick = { turns = v }, enabled = !editMode, modifier = Modifier.weight(1f), contentPadding = PaddingValues(5.dp)
                        ) { Text(label, fontSize = 10.sp, color = InkSoft) }
                    }
                    if (size == 5) OutlinedButton(
                        onClick = { width = if (width == 1) 2 else 1 }, enabled = !editMode,
                        modifier = Modifier.weight(1f), contentPadding = PaddingValues(5.dp)
                    ) { Text(if (width == 1) "OUTER" else "WIDE", fontSize = 10.sp, color = InkSoft) }
                }
                Spacer(Modifier.height(5.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Face.entries.forEach { f ->
                        OutlinedButton(
                            onClick = { onMove(Move(f, width, turns)) }, enabled = !editMode,
                            modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 0.dp, vertical = 7.dp)
                        ) { Text(f.symbol.toString(), color = Ink, fontWeight = FontWeight.Black) }
                    }
                }
                Spacer(Modifier.height(5.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    OutlinedButton(onClick = onUndo, enabled = !editMode, modifier = Modifier.weight(1f)) { Text("UNDO", fontSize = 9.sp, color = InkSoft) }
                    OutlinedButton(onClick = onRedo, enabled = !editMode, modifier = Modifier.weight(1f)) { Text("REDO", fontSize = 9.sp, color = InkSoft) }
                    OutlinedButton(
                        onClick = if (canReturnToScan) onReturnScan else onReset,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (canReturnToScan) "TO SCAN" else "RESET", fontSize = 9.sp, color = InkSoft) }
                    Button(onClick = onSolve, enabled = !solving && !editMode, modifier = Modifier.weight(1.15f)) {
                        Text(if (solving) "…" else "SOLVE", fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        if (history.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(history.takeLast(12).joinToString(" ") { it.notation() }, color = Muted, fontSize = 9.sp, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PaintPalette(palette: Map<Face,RgbColor>, selected: Face, onSelect: (Face) -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Outline)) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
            Text("PAINT STICKERS", color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text("Choose a color, then tap a visible sticker. Fixed centers stay locked.", color = Muted, fontSize = 9.sp)
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Face.entries.forEach { face ->
                    val active = face == selected
                    Surface(
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(face) },
                        color = if (active) AccentSoft else Panel2,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(if (active) 2.dp else 1.dp, if (active) Accent else Outline)
                    ) {
                        Column(Modifier.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(21.dp)
                                    .background(faceColor(face, palette), RoundedCornerShape(6.dp))
                                    .border(1.dp, Color.Black.copy(alpha=.18f), RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(face.symbol.toString(), color = Ink, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideCard(move: Move?, index: Int, total: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(AccentSoft, RoundedCornerShape(18.dp)).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (move == null) "SOLUTION COMPLETE" else "STEP ${index+1} / $total", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text(move?.let { describeMove(it) } ?: "Cube should now be solved.", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            move?.let { Text(it.notation(), color = Muted, fontSize = 11.sp) }
        }
        OutlinedButton(onClick = onPrev, enabled = index > 0, contentPadding = PaddingValues(9.dp)) { Text("‹") }
        Spacer(Modifier.width(5.dp))
        Button(onClick = onNext, enabled = move != null, contentPadding = PaddingValues(11.dp)) { Text("NEXT", fontSize = 10.sp) }
    }
}

private fun describeMove(m: Move): String {
    val face = when (m.face) { Face.U->"UP"; Face.D->"DOWN"; Face.L->"LEFT"; Face.R->"RIGHT"; Face.F->"FRONT"; Face.B->"BACK" }
    val layer = if (m.width > 1) " two layers" else " face"
    val dir = when (m.quarterTurns) { 1->"clockwise ↻"; 2->"180°"; else->"counter-clockwise ↺" }
    return "$face$layer · $dir"
}
