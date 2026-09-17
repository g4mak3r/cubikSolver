package com.cubecraft.solver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
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
    onNext: () -> Unit, onPrev: () -> Unit
) {
    var width by remember(size) { mutableIntStateOf(1) }
    var turns by remember { mutableIntStateOf(1) }

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
            Box(
                Modifier.background(if (cube.isSolved()) SuccessSoft else AccentSoft, RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(if (cube.isSolved()) "SOLVED" else "LIVE", color = if (cube.isSolved()) Success else Accent, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }

        Spacer(Modifier.height(7.dp))
        Surface(
            Modifier.fillMaxWidth().weight(1f), color = Viewport,
            shape = RoundedCornerShape(28.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Outline)
        ) {
            Box {
                Cube3D(cube, revision, palette, guide, Modifier.fillMaxSize())
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(12.dp)
                        .background(Panel.copy(alpha = .82f), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("DRAG TO ORBIT · PINCH TO ZOOM", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (solution.isNotEmpty()) {
            Spacer(Modifier.height(8.dp)); GuideCard(guide, solutionIndex, solution.size, onPrev, onNext)
        }
        message?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = if (it.contains("not bundled") || it.contains("Invalid")) Danger else Muted, fontSize = 10.sp, maxLines = 3)
        }

        Spacer(Modifier.height(7.dp))
        Surface(color = Panel, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Outline)) {
            Column(Modifier.padding(9.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(1 to "CW", 3 to "CCW", 2 to "180°").forEach { (v,label) ->
                        if (turns == v) Button(
                            onClick = { turns = v }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(5.dp)
                        ) { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        else OutlinedButton(
                            onClick = { turns = v }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(5.dp)
                        ) { Text(label, fontSize = 10.sp, color = InkSoft) }
                    }
                    if (size == 5) OutlinedButton(
                        onClick = { width = if (width == 1) 2 else 1 }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(5.dp)
                    ) { Text(if (width == 1) "OUTER" else "WIDE", fontSize = 10.sp, color = InkSoft) }
                }
                Spacer(Modifier.height(5.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Face.entries.forEach { f ->
                        OutlinedButton(
                            onClick = { onMove(Move(f, width, turns)) }, modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 7.dp)
                        ) { Text(f.symbol.toString(), color = Ink, fontWeight = FontWeight.Black) }
                    }
                }
                Spacer(Modifier.height(5.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    OutlinedButton(onClick = onUndo, modifier = Modifier.weight(1f)) { Text("UNDO", fontSize = 9.sp, color = InkSoft) }
                    OutlinedButton(onClick = onRedo, modifier = Modifier.weight(1f)) { Text("REDO", fontSize = 9.sp, color = InkSoft) }
                    OutlinedButton(
                        onClick = if (canReturnToScan) onReturnScan else onReset,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (canReturnToScan) "TO SCAN" else "RESET", fontSize = 9.sp, color = InkSoft) }
                    Button(onClick = onSolve, enabled = !solving, modifier = Modifier.weight(1.15f)) {
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
