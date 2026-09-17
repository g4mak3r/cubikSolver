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
import kotlin.math.roundToInt

@Composable
fun StudioScreen(
    size: Int, cube: CubeState, revision: Int, palette: Map<Face,RgbColor>, history: List<Move>,
    solution: List<Move>, solutionIndex: Int, guide: Move?, solving: Boolean, message: String?,
    canReturnToScan: Boolean, onBack: () -> Unit, onMove: (Move) -> Unit, onUndo: () -> Unit,
    onRedo: () -> Unit, onReturnScan: () -> Unit, onReset: () -> Unit, onSolve: () -> Unit,
    onNext: () -> Unit, onPrev: () -> Unit, onSeek: (Int) -> Unit,
    onPaintSticker: (StickerKey, Face) -> Unit
) {
    var width by remember(size) { mutableIntStateOf(1) }
    var turns by remember { mutableIntStateOf(1) }
    var editMode by remember { mutableStateOf(false) }
    var paintColor by remember { mutableStateOf(Face.F) }
    val scanned3x3 = canReturnToScan && size == 3

    Column(Modifier.fillMaxSize().background(AppBg).padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
                Text("‹  HOME", color = InkSoft, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.padding(start = 5.dp)) {
                Text("cubikSolver", color = Ink, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = .4.sp)
                Text(
                    if (scanned3x3) "3 × 3 · GUIDED SOLVE" else "$size × $size · DIGITAL TWIN",
                    color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.weight(1f))
            if (canReturnToScan) {
                TextButton(onClick = { editMode = !editMode }) {
                    Text(
                        if (editMode) "DONE EDIT" else "EDIT COLORS",
                        color = if (editMode) Accent else InkSoft,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        Spacer(Modifier.height(5.dp))
        Surface(
            Modifier.fillMaxWidth().weight(1f),
            color = Viewport,
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Outline)
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
                        .background(Panel.copy(alpha = .88f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        if (editMode) "TAP STICKER TO PAINT · DRAG TO ORBIT"
                        else if (guide != null) "HIGHLIGHT + GHOST = NEXT PHYSICAL TURN"
                        else "DRAG TO ORBIT · PINCH TO ZOOM",
                        color = if (editMode || guide != null) Accent else Muted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        when {
            editMode -> {
                Spacer(Modifier.height(8.dp))
                PaintPalette(palette, paintColor) { paintColor = it }
            }
            solving -> {
                Spacer(Modifier.height(8.dp))
                AnalyzingCard()
            }
            solution.isNotEmpty() -> {
                Spacer(Modifier.height(8.dp))
                SolutionTimelineCard(
                    solution = solution,
                    index = solutionIndex,
                    onSeek = onSeek,
                    onPrev = onPrev,
                    onNext = onNext
                )
            }
        }

        message?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                it,
                color = if (it.contains("not bundled") || it.contains("Invalid") || it.contains("exactly") || it.contains("mismatch")) Danger else Muted,
                fontSize = 10.sp,
                maxLines = 3
            )
        }

        Spacer(Modifier.height(7.dp))

        if (scanned3x3) {
            SolverActions(
                solving = solving,
                hasSolution = solution.isNotEmpty(),
                editMode = editMode,
                onStart = { onSeek(0) },
                onReturnScan = onReturnScan,
                onAnalyze = onSolve
            )
        } else {
            ManualControls(
                size = size,
                width = width,
                turns = turns,
                editMode = editMode,
                canReturnToScan = canReturnToScan,
                onWidth = { width = it },
                onTurns = { turns = it },
                onMove = onMove,
                onUndo = onUndo,
                onRedo = onRedo,
                onReturnScan = onReturnScan,
                onReset = onReset,
                onSolve = onSolve,
                solving = solving
            )
        }

        if (!scanned3x3 && history.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(history.takeLast(12).joinToString(" ") { it.notation() }, color = Muted, fontSize = 9.sp, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AnalyzingCard() {
    Surface(color = AccentSoft, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Accent.copy(alpha=.22f))) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("ANALYZING 3×3", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Black)
                Text("Searching for a short verified sequence…", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SolutionTimelineCard(
    solution: List<Move>,
    index: Int,
    onSeek: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val total = solution.size
    val atEnd = index >= total
    val move = solution.getOrNull(index)
    val sliderMax = total.toFloat().coerceAtLeast(1f)

    Surface(color = Panel, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Outline)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (atEnd) "SOLUTION COMPLETE" else "STEP ${index + 1} / $total",
                        color = if (atEnd) Success else Accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                    if (move != null) {
                        Text(move.notation(), color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Black)
                        Text(moveInstruction(move), color = InkSoft, fontSize = 12.sp, lineHeight = 16.sp)
                    } else {
                        Text("SOLVED", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
                        Text("All $total moves are applied in the digital preview.", color = InkSoft, fontSize = 12.sp)
                    }
                }
                OutlinedButton(onClick = onPrev, enabled = index > 0, contentPadding = PaddingValues(9.dp)) {
                    Text("‹")
                }
                Spacer(Modifier.width(5.dp))
                Button(onClick = onNext, enabled = index < total, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(if (index + 1 >= total) "DONE" else "NEXT", fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }

            Spacer(Modifier.height(4.dp))
            Slider(
                value = index.toFloat().coerceIn(0f, sliderMax),
                onValueChange = { onSeek(it.roundToInt()) },
                valueRange = 0f..sliderMax,
                steps = (total - 1).coerceAtLeast(0)
            )
            Row(Modifier.fillMaxWidth()) {
                Text("START", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("SOLVED", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            if (move != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Direction is defined while looking straight at the named face.",
                    color = Muted,
                    fontSize = 8.sp
                )
            }
        }
    }
}

@Composable
private fun SolverActions(
    solving: Boolean,
    hasSolution: Boolean,
    editMode: Boolean,
    onStart: () -> Unit,
    onReturnScan: () -> Unit,
    onAnalyze: () -> Unit
) {
    Surface(color = Panel, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Outline)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onStart, enabled = hasSolution && !editMode, modifier = Modifier.weight(1f)) {
                Text("START", fontSize = 9.sp)
            }
            OutlinedButton(onClick = onReturnScan, enabled = !editMode, modifier = Modifier.weight(1f)) {
                Text("SCAN STATE", fontSize = 9.sp)
            }
            Button(onClick = onAnalyze, enabled = !solving && !editMode, modifier = Modifier.weight(1.25f)) {
                Text(if (solving) "ANALYZING…" else "ANALYZE", fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
        }
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
private fun ManualControls(
    size: Int,
    width: Int,
    turns: Int,
    editMode: Boolean,
    canReturnToScan: Boolean,
    onWidth: (Int) -> Unit,
    onTurns: (Int) -> Unit,
    onMove: (Move) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReturnScan: () -> Unit,
    onReset: () -> Unit,
    onSolve: () -> Unit,
    solving: Boolean
) {
    Surface(color = Panel, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Outline)) {
        Column(Modifier.padding(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(1 to "CW", 3 to "CCW", 2 to "180°").forEach { (v,label) ->
                    if (turns == v) Button(
                        onClick = { onTurns(v) }, enabled = !editMode,
                        modifier = Modifier.weight(1f), contentPadding = PaddingValues(5.dp)
                    ) { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    else OutlinedButton(
                        onClick = { onTurns(v) }, enabled = !editMode,
                        modifier = Modifier.weight(1f), contentPadding = PaddingValues(5.dp)
                    ) { Text(label, fontSize = 10.sp, color = InkSoft) }
                }
                if (size == 5) OutlinedButton(
                    onClick = { onWidth(if (width == 1) 2 else 1) }, enabled = !editMode,
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
}

private fun moveInstruction(m: Move): String {
    val face = when (m.face) {
        Face.U -> "UPPER"
        Face.D -> "BOTTOM"
        Face.L -> "LEFT"
        Face.R -> "RIGHT"
        Face.F -> "FRONT"
        Face.B -> "BACK"
    }
    val layer = if (m.width > 1) "two layers" else "face"
    val direction = when (m.quarterTurns) {
        1 -> "clockwise ↻"
        2 -> "180°"
        else -> "counter-clockwise ↺"
    }
    return "Turn the $face $layer $direction"
}
