package com.cubecraft.solver.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
    val guided = canReturnToScan && (size == 3 || size == 5)

    Column(Modifier.fillMaxSize().background(AppBg).padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 6.dp)) {
                Text("← HOME", color = Ink, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "%02dX%02d / %s".format(size, size, if (guided) "GUIDE" else "LAB"),
                color = Muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp
            )
            if (canReturnToScan) {
                Spacer(Modifier.width(10.dp))
                TextButton(onClick = { editMode = !editMode }, contentPadding = PaddingValues(0.dp)) {
                    Text(
                        if (editMode) "DONE" else "EDIT",
                        color = if (editMode) Accent else Ink,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        HorizontalDivider(color = Outline)
        Spacer(Modifier.height(8.dp))

        Box(
            Modifier.fillMaxWidth().weight(1f)
                .background(Viewport, RoundedCornerShape(6.dp))
                .border(1.dp, InkSoft, RoundedCornerShape(6.dp))
        ) {
            Cube3D(
                cube = cube,
                revision = revision,
                palette = palette,
                highlight = if (editMode) null else guide,
                modifier = Modifier.fillMaxSize(),
                paintColor = if (editMode) paintColor else null,
                onPaintSticker = if (editMode) onPaintSticker else null
            )
            Text(
                when {
                    editMode -> "PAINT / DRAG TO ORBIT"
                    guide != null -> "NEXT / " + guide.notation()
                    else -> "DRAG ORBIT  ·  PINCH ZOOM"
                },
                modifier = Modifier.align(Alignment.BottomStart).padding(9.dp)
                    .background(CameraChrome, RoundedCornerShape(2.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp
            )
        }

        Spacer(Modifier.height(8.dp))
        when {
            editMode -> MinimalPaintPalette(palette, paintColor) { paintColor = it }
            solving -> AnalyzeLine(size)
            solution.isNotEmpty() -> GuidePanel(solution, solutionIndex, onSeek, onPrev, onNext)
        }

        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(
                message,
                modifier = Modifier.fillMaxWidth(),
                color = if (
                    message.contains("could", true) ||
                    message.contains("invalid", true) ||
                    message.contains("failed", true)
                ) Danger else Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                lineHeight = 11.sp,
                maxLines = 2
            )
        }

        Spacer(Modifier.height(7.dp))
        if (guided) {
            GuideActions(
                solving = solving,
                hasSolution = solution.isNotEmpty(),
                editMode = editMode,
                onStart = { onSeek(0) },
                onReturnScan = onReturnScan,
                onAnalyze = onSolve
            )
        } else {
            ManualControlsMinimal(
                size, width, turns, editMode, canReturnToScan,
                onWidth, onTurns, onMove, onUndo, onRedo, onReturnScan, onReset, onSolve, solving
            )
        }

        if (!guided && history.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                history.takeLast(14).joinToString(" ") { it.notation() },
                color = Muted, fontFamily = FontFamily.Monospace, fontSize = 8.sp, maxLines = 1
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AnalyzeLine(size: Int) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "ANALYZING " + size + "×" + size,
                color = Accent, fontFamily = FontFamily.Monospace,
                fontSize = 9.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (size == 5) "CENTER → EDGE → 3×3" else "MIN2PHASE",
                color = Muted, fontFamily = FontFamily.Monospace, fontSize = 8.sp
            )
        }
        Spacer(Modifier.height(5.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
    }
}

@Composable
private fun GuidePanel(
    solution: List<Move>, index: Int, onSeek: (Int) -> Unit, onPrev: () -> Unit, onNext: () -> Unit
) {
    val total = solution.size
    val move = solution.getOrNull(index)
    val atEnd = index >= total
    val max = total.toFloat().coerceAtLeast(1f)

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (atEnd) "SOLVED" else "%03d / %03d".format(index + 1, total),
                    color = if (atEnd) Success else Accent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    move?.notation() ?: "DONE",
                    color = Ink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    move?.let(::moveInstruction) ?: "Sequence verified on the digital cube.",
                    color = InkSoft,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    lineHeight = 12.sp
                )
            }
            OutlinedButton(
                onClick = onPrev, enabled = index > 0,
                modifier = Modifier.size(width = 48.dp, height = 42.dp),
                contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(3.dp)
            ) { Text("←", fontFamily = FontFamily.Monospace) }
            Spacer(Modifier.width(5.dp))
            Button(
                onClick = onNext, enabled = index < total,
                modifier = Modifier.size(width = 62.dp, height = 42.dp),
                contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(3.dp)
            ) {
                Text(
                    if (index + 1 >= total) "END" else "NEXT",
                    fontFamily = FontFamily.Monospace, fontSize = 9.sp
                )
            }
        }
        Slider(
            value = index.toFloat().coerceIn(0f, max),
            onValueChange = { onSeek(it.roundToInt()) },
            valueRange = 0f..max,
            steps = 0,
            modifier = Modifier.fillMaxWidth().height(32.dp)
        )
    }
}

@Composable
private fun GuideActions(
    solving: Boolean, hasSolution: Boolean, editMode: Boolean,
    onStart: () -> Unit, onReturnScan: () -> Unit, onAnalyze: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
            onClick = onStart, enabled = hasSolution && !editMode,
            modifier = Modifier.weight(.75f).height(44.dp), shape = RoundedCornerShape(3.dp)
        ) { Text("|<", fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
        OutlinedButton(
            onClick = onReturnScan, enabled = !editMode,
            modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(3.dp)
        ) { Text("SCAN", fontFamily = FontFamily.Monospace, fontSize = 9.sp) }
        Button(
            onClick = onAnalyze, enabled = !solving && !editMode,
            modifier = Modifier.weight(1.3f).height(44.dp), shape = RoundedCornerShape(3.dp)
        ) {
            Text(
                if (solving) "…" else "ANALYZE",
                fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MinimalPaintPalette(
    palette: Map<Face,RgbColor>, selected: Face, onSelect: (Face) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Face.entries.forEach { face ->
            val active = face == selected
            val background = faceColor(face, palette)
            Surface(
                modifier = Modifier.weight(1f).height(40.dp),
                onClick = { onSelect(face) },
                color = background,
                shape = RoundedCornerShape(3.dp),
                border = BorderStroke(
                    if (active) 3.dp else 1.dp,
                    if (active) Ink else Color.Black.copy(alpha = .28f)
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        face.symbol.toString(),
                        color = if (background.luminance() > .5f) Ink else Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualControlsMinimal(
    size: Int, width: Int, turns: Int, editMode: Boolean, canReturnToScan: Boolean,
    onWidth: (Int) -> Unit, onTurns: (Int) -> Unit, onMove: (Move) -> Unit,
    onUndo: () -> Unit, onRedo: () -> Unit, onReturnScan: () -> Unit, onReset: () -> Unit,
    onSolve: () -> Unit, solving: Boolean
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(1 to "CW", 3 to "CCW", 2 to "180").forEach { pair ->
                val value = pair.first
                val label = pair.second
                OutlinedButton(
                    onClick = { onTurns(value) }, enabled = !editMode,
                    modifier = Modifier.weight(1f).height(38.dp),
                    contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(3.dp),
                    border = BorderStroke(
                        if (turns == value) 2.dp else 1.dp,
                        if (turns == value) Accent else Outline
                    )
                ) { Text(label, fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = Ink) }
            }
            if (size == 5) OutlinedButton(
                onClick = { onWidth(if (width == 1) 2 else 1) }, enabled = !editMode,
                modifier = Modifier.weight(1f).height(38.dp),
                contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(3.dp)
            ) { Text(if (width == 1) "OUT" else "WIDE", fontFamily = FontFamily.Monospace, fontSize = 8.sp) }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Face.entries.forEach { face ->
                OutlinedButton(
                    onClick = { onMove(Move(face, width, turns)) }, enabled = !editMode,
                    modifier = Modifier.weight(1f).height(40.dp),
                    contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(3.dp)
                ) {
                    Text(
                        face.symbol.toString(), color = Ink,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(
                onClick = onUndo, enabled = !editMode,
                modifier = Modifier.weight(1f).height(40.dp),
                contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(3.dp)
            ) { Text("UNDO", fontFamily = FontFamily.Monospace, fontSize = 8.sp) }
            OutlinedButton(
                onClick = onRedo, enabled = !editMode,
                modifier = Modifier.weight(1f).height(40.dp),
                contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(3.dp)
            ) { Text("REDO", fontFamily = FontFamily.Monospace, fontSize = 8.sp) }
            OutlinedButton(
                onClick = if (canReturnToScan) onReturnScan else onReset,
                enabled = !editMode,
                modifier = Modifier.weight(1f).height(40.dp),
                contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(3.dp)
            ) {
                Text(
                    if (canReturnToScan) "SCAN" else "RESET",
                    fontFamily = FontFamily.Monospace, fontSize = 8.sp
                )
            }
            Button(
                onClick = onSolve, enabled = !solving && !editMode,
                modifier = Modifier.weight(1.1f).height(40.dp),
                contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(3.dp)
            ) {
                Text(
                    if (solving) "…" else "SOLVE",
                    fontFamily = FontFamily.Monospace, fontSize = 8.sp
                )
            }
        }
    }
}

private fun moveInstruction(move: Move): String {
    val face = when (move.face) {
        Face.U -> "UP"
        Face.D -> "DOWN"
        Face.L -> "LEFT"
        Face.R -> "RIGHT"
        Face.F -> "FRONT"
        Face.B -> "BACK"
    }
    val layer = when (move.width) {
        1 -> "FACE"
        2 -> "2 LAYERS"
        else -> move.width.toString() + " LAYERS"
    }
    val direction = when (move.quarterTurns) {
        1 -> "CLOCKWISE ↻"
        2 -> "180°"
        else -> "COUNTER-CLOCKWISE ↺"
    }
    return face + " / " + layer + " / " + direction
}
