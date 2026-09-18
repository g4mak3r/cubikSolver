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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.model.Move
import com.cubecraft.solver.model.StickerKey
import com.cubecraft.solver.scanner.RgbColor
import com.cubecraft.solver.scanner.StickerGuess
import com.cubecraft.solver.scanner.canonicalStickerGuesses
import kotlin.math.roundToInt

@Composable
fun StudioScreen(
    size: Int,
    cube: CubeState,
    revision: Int,
    palette: Map<Face, RgbColor>,
    colorFaces: Map<StickerGuess, Face>,
    history: List<Move>,
    solution: List<Move>,
    solutionIndex: Int,
    guide: Move?,
    solving: Boolean,
    message: String?,
    canReturnToScan: Boolean,
    onBack: () -> Unit,
    onMove: (Move) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReturnScan: () -> Unit,
    onReset: () -> Unit,
    onSolve: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Int) -> Unit,
    onPaintSticker: (StickerKey, Face) -> Unit
) {
    var width by remember(size) { mutableIntStateOf(1) }
    var turns by remember { mutableIntStateOf(1) }
    var editMode by remember { mutableStateOf(false) }
    var paintGuess by remember { mutableStateOf(StickerGuess.GREEN) }
    val paintColor = colorFaces[paintGuess] ?: Face.F
    val guided = canReturnToScan && (size == 3 || size == 5)

    Column(Modifier.fillMaxSize().background(AppBg).padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(7.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("←", color = Ink, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "$size×$size",
                color = InkSoft,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            if (canReturnToScan) {
                Spacer(Modifier.width(10.dp))
                TextButton(
                    onClick = { editMode = !editMode },
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        if (editMode) "DONE" else "EDIT",
                        color = if (editMode) Accent else Muted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                }
            }
        }

        Box(
            Modifier.fillMaxWidth().weight(1f)
                .background(Viewport, RoundedCornerShape(7.dp))
                .border(1.dp, Outline, RoundedCornerShape(7.dp))
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
        }

        Spacer(Modifier.height(8.dp))

        when {
            editMode -> PaintPalette(paintGuess) { paintGuess = it }
            solving -> SolveProgress(size)
            solution.isNotEmpty() -> SolutionPanel(
                solution,
                solutionIndex,
                onSeek,
                onPrev,
                onNext
            )
        }

        if (isErrorMessage(message)) {
            Spacer(Modifier.height(6.dp))
            Text(
                message.orEmpty(),
                color = Danger,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 12.sp,
                maxLines = 3
            )
        }

        Spacer(Modifier.height(7.dp))

        if (guided) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onReturnScan,
                    enabled = !editMode,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(3.dp),
                    border = BorderStroke(1.dp, Outline)
                ) {
                    Text("SCAN", color = InkSoft, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                }
                Button(
                    onClick = onSolve,
                    enabled = !solving && !editMode,
                    modifier = Modifier.weight(1.35f).height(46.dp),
                    shape = RoundedCornerShape(3.dp)
                ) {
                    Text(
                        if (solving) "…" else "ANALYZE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
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

        if (!guided && history.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                history.takeLast(12).joinToString(" ") { it.notation() },
                color = Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                maxLines = 1
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SolveProgress(size: Int) {
    Column(Modifier.fillMaxWidth()) {
        LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        Spacer(Modifier.height(7.dp))
        Text(
            if (size == 5) "REDUCING 5×5" else "SOLVING",
            color = Accent,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SolutionPanel(
    solution: List<Move>,
    index: Int,
    onSeek: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val total = solution.size
    val move = solution.getOrNull(index)
    val atEnd = index >= total
    val max = total.toFloat().coerceAtLeast(1f)

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    move?.notation() ?: "SOLVED",
                    color = if (atEnd) Success else Ink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 30.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Black
                )
                if (move != null) {
                    Text(
                        moveInstruction(move),
                        color = InkSoft,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                }
            }
            Text(
                if (atEnd) total.toString() else (index + 1).toString() + "/" + total,
                color = Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
        }

        Slider(
            value = index.toFloat().coerceIn(0f, max),
            onValueChange = { onSeek(it.roundToInt()) },
            valueRange = 0f..max,
            steps = 0,
            modifier = Modifier.fillMaxWidth().height(36.dp)
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = onPrev,
                enabled = index > 0,
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(3.dp),
                border = BorderStroke(1.dp, Outline)
            ) {
                Text("←", color = Ink, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
            }
            Button(
                onClick = onNext,
                enabled = index < total,
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(3.dp)
            ) {
                Text("→", fontFamily = FontFamily.Monospace, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun PaintPalette(
    selected: StickerGuess,
    onSelect: (StickerGuess) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        canonicalStickerGuesses.forEach { guess ->
            val rgb = idealRgbForGuess(guess)
            val color = Color(rgb.argb())
            val active = selected == guess
            Surface(
                modifier = Modifier.weight(1f).height(42.dp),
                onClick = { onSelect(guess) },
                color = color,
                shape = RoundedCornerShape(3.dp),
                border = BorderStroke(
                    if (active) 3.dp else 1.dp,
                    if (active) Accent else Color.Black.copy(alpha = .3f)
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        guess.label,
                        color = if (color.luminance() > .52f) Color.Black else Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
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
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(1 to "CW", 3 to "CCW", 2 to "180").forEach { (value, label) ->
                OutlinedButton(
                    onClick = { onTurns(value) },
                    enabled = !editMode,
                    modifier = Modifier.weight(1f).height(38.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(3.dp),
                    border = BorderStroke(
                        if (turns == value) 2.dp else 1.dp,
                        if (turns == value) Accent else Outline
                    )
                ) {
                    Text(label, color = InkSoft, fontFamily = FontFamily.Monospace, fontSize = 8.sp)
                }
            }
            if (size == 5) {
                OutlinedButton(
                    onClick = { onWidth(if (width == 1) 2 else 1) },
                    enabled = !editMode,
                    modifier = Modifier.weight(1f).height(38.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(3.dp),
                    border = BorderStroke(1.dp, Outline)
                ) {
                    Text(
                        if (width == 1) "OUT" else "WIDE",
                        color = InkSoft,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Face.entries.forEach { face ->
                OutlinedButton(
                    onClick = { onMove(Move(face, width, turns)) },
                    enabled = !editMode,
                    modifier = Modifier.weight(1f).height(40.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(3.dp),
                    border = BorderStroke(1.dp, Outline)
                ) {
                    Text(
                        face.symbol.toString(),
                        color = Ink,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(
                onClick = onUndo,
                enabled = !editMode,
                modifier = Modifier.weight(1f).height(40.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(3.dp),
                border = BorderStroke(1.dp, Outline)
            ) {
                Text("UNDO", color = InkSoft, fontFamily = FontFamily.Monospace, fontSize = 8.sp)
            }
            OutlinedButton(
                onClick = onRedo,
                enabled = !editMode,
                modifier = Modifier.weight(1f).height(40.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(3.dp),
                border = BorderStroke(1.dp, Outline)
            ) {
                Text("REDO", color = InkSoft, fontFamily = FontFamily.Monospace, fontSize = 8.sp)
            }
            OutlinedButton(
                onClick = if (canReturnToScan) onReturnScan else onReset,
                enabled = !editMode,
                modifier = Modifier.weight(1f).height(40.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(3.dp),
                border = BorderStroke(1.dp, Outline)
            ) {
                Text(
                    if (canReturnToScan) "SCAN" else "RESET",
                    color = InkSoft,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp
                )
            }
            Button(
                onClick = onSolve,
                enabled = !solving && !editMode,
                modifier = Modifier.weight(1.1f).height(40.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(3.dp)
            ) {
                Text(
                    if (solving) "…" else "SOLVE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp
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
        1 -> ""
        2 -> " · 2 LAYERS"
        else -> " · " + move.width + " LAYERS"
    }
    val direction = when (move.quarterTurns) {
        1 -> "CLOCKWISE"
        2 -> "180°"
        else -> "COUNTER-CLOCKWISE"
    }
    return face + layer + " · " + direction
}

private fun isErrorMessage(message: String?): Boolean {
    if (message.isNullOrBlank()) return false
    return listOf(
        "could",
        "invalid",
        "failed",
        "reduction",
        "error",
        "mismatch",
        "unavailable",
        "retry"
    ).any { message.contains(it, ignoreCase = true) }
}
