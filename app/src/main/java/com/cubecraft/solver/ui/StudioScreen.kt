package com.cubecraft.solver.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cubecraft.solver.model.*
import com.cubecraft.solver.scanner.RgbColor
import com.cubecraft.solver.scanner.StickerGuess
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun StudioScreen(
    size: Int, cube: CubeState, revision: Int, palette: Map<Face, RgbColor>, colorFaces: Map<StickerGuess, Face>,
    history: List<Move>, solution: List<Move>, solutionIndex: Int, guide: Move?, solving: Boolean,
    message: String?, canReturnToScan: Boolean, onBack: () -> Unit, onMove: (Move) -> Unit,
    onUndo: () -> Unit, onRedo: () -> Unit, onReturnScan: () -> Unit, onReset: () -> Unit,
    onSolve: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit, onSeek: (Int) -> Unit, onPaintSticker: (StickerKey, Face) -> Unit
) {
    var width by remember(size) { mutableIntStateOf(1) }
    var turns by remember { mutableIntStateOf(1) }
    var editMode by remember { mutableStateOf(false) }
    var paintGuess by remember { mutableStateOf(StickerGuess.GREEN) }
    val paintColor = colorFaces[paintGuess] ?: Face.F
    val guided = canReturnToScan
    Column(Modifier.fillMaxSize().padding(horizontal = CubeDesign.Gutter)) {
        ScreenHeader(if (guided) "Your solution" else "Cube studio", "$size × $size · ${if (editMode) "Color correction" else "Drag to rotate"}", onBack) {
            if (canReturnToScan) {
                AppIconButton(if (editMode) CubeIcon.Check else CubeIcon.Edit, if (editMode) "Finish editing" else "Edit cube colors", { editMode = !editMode }, enabled = !solving)
            }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val cubeHeight = (maxHeight * .50f).coerceIn(228.dp, 420.dp)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().height(cubeHeight).clip(CubeDesign.PanelShape).background(Viewport)) {
                    Cube3D(cube, revision, palette, if (editMode || solving) null else guide,
                        Modifier.fillMaxSize(), if (editMode) paintColor else null, if (editMode) onPaintSticker else null)
                    Text(if (editMode) "Tap a sticker to paint" else "Pinch to zoom", Modifier.align(Alignment.BottomCenter).padding(12.dp), color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                when {
                    editMode -> AppPanel {
                        Text("Make it match your cube", style = MaterialTheme.typography.titleMedium)
                        ColorPalette(paintGuess) { paintGuess = it }
                        Text("Rotate the cube, then tap a sticker to change its color.", color = InkSoft, style = MaterialTheme.typography.bodySmall)
                    }
                    solving -> AppPanel {
                        Text("Finding your next moves…", style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(Modifier.fillMaxWidth(), trackColor = Panel2)
                        Text("Your solution is checked before it appears.", color = InkSoft, style = MaterialTheme.typography.bodySmall)
                    }
                    solution.isNotEmpty() -> SolutionPanel(solution, solutionIndex, onSeek, onPrev, onNext)
                    message == "SOLVED" -> StatusNote("Everything in place", "Your cube is already solved.", success = true)
                    !guided -> StatusNote("Try a few turns", "Move a face below, then ask for a solution.")
                }
                if (!message.isNullOrBlank() && message != "SOLVED" && !solving && !message.startsWith("Analyzing") && !message.startsWith("Reducing")) {
                    StatusNote("Check your cube", message, error = true)
                }
                if (!guided && !editMode) {
                    ManualControls(size, width, turns, !solving, { width = it }, { turns = it }, onMove, onUndo, onRedo)
                    if (history.isNotEmpty()) Text(history.takeLast(14).joinToString(" ") { it.notation() }, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = InkSoft)
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppButton(if (guided) "Scan review" else "Reset cube", if (guided) onReturnScan else onReset, Modifier.weight(1f), primary = false, enabled = !editMode)
            AppButton(if (solving) "Solving…" else "Solve cube", onSolve, Modifier.weight(1.15f), enabled = !solving && !editMode)
        }
    }
}

@Composable
private fun SolutionPanel(solution: List<Move>, index: Int, onSeek: (Int) -> Unit, onPrev: () -> Unit, onNext: () -> Unit) {
    var playing by remember(solution) { mutableStateOf(false) }
    var scrub by remember(solution) { mutableStateOf<Float?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val next by rememberUpdatedState(onNext)
    val total = solution.size
    val move = solution.getOrNull(index)
    val atEnd = index >= total
    // Autoplay pauses when the app is backgrounded. Leaving this panel cancels its coroutine.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) playing = false }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(playing, index, solution) {
        if (atEnd) playing = false
        else if (playing) { delay(1900L); next() }
    }
    AppPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(if (atEnd) "Complete" else "Next turn", Modifier.weight(1f))
            Text("$index / $total", color = InkSoft, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AnimatedContent(targetState = move?.notation() ?: "✓", transitionSpec = {
                fadeIn(tween(CubeDesign.ChangeMillis)) togetherWith fadeOut(tween(CubeDesign.PressMillis))
            }, label = "currentMove") { notation ->
                Text(notation, color = if (atEnd) Success else Accent, fontSize = 38.sp, lineHeight = 44.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
            }
            Column(Modifier.weight(1f)) {
                Text(if (atEnd) "Cube solved" else moveTitle(move!!), style = MaterialTheme.typography.titleSmall)
                Text(if (atEnd) "Nicely done." else moveDirection(move!!), style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
        }
        if (!atEnd) Text("Then  " + solution.drop(index + 1).take(5).joinToString("   ") { it.notation() }.ifEmpty { "finished" }, color = InkSoft, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        Slider(
            value = scrub ?: index.toFloat(), onValueChange = { playing = false; scrub = it },
            onValueChangeFinished = { scrub?.let { onSeek(it.roundToInt()) }; scrub = null },
            valueRange = 0f..total.toFloat(), modifier = Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = "Solution progress" },
            colors = SliderDefaults.colors(inactiveTrackColor = Outline)
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIconButton(CubeIcon.Previous, "Previous move", { playing = false; onPrev() }, enabled = index > 0)
            AppButton(if (playing) "Pause" else if (atEnd) "Replay" else "Play", {
                if (atEnd) onSeek(0)
                playing = !playing
            }, Modifier.weight(1f), icon = if (playing) CubeIcon.Pause else CubeIcon.Play)
            AppIconButton(CubeIcon.Next, "Next move", { playing = false; onNext() }, enabled = !atEnd)
        }
    }
}

@Composable
private fun ManualControls(size: Int, width: Int, turns: Int, enabled: Boolean, onWidth: (Int) -> Unit, onTurns: (Int) -> Unit, onMove: (Move) -> Unit, onUndo: () -> Unit, onRedo: () -> Unit) {
    AppPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Manual turns", Modifier.weight(1f))
            AppIconButton(CubeIcon.Undo, "Undo turn", onUndo, enabled)
            AppIconButton(CubeIcon.Redo, "Redo turn", onRedo, enabled)
        }
        ChoiceBar(listOf("90°", "−90°", "180°"), listOf(1, 3, 2).indexOf(turns), { if (enabled) onTurns(listOf(1, 3, 2)[it]) }, Modifier.fillMaxWidth())
        if (size == 5) ChoiceBar(listOf("Outer layer", "Wide turn"), width - 1, { if (enabled) onWidth(it + 1) }, Modifier.fillMaxWidth())
        // Two rows keep six face buttons comfortably tappable even on narrow screens.
        Face.entries.toList().chunked(3).forEach { faces ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                faces.forEach { face -> AppButton(face.symbol.toString(), { onMove(Move(face, width, turns)) }, Modifier.weight(1f).semantics { contentDescription = "Turn ${faceName(face).lowercase()} face" }, primary = false, enabled = enabled) }
            }
        }
    }
}

private fun moveTitle(move: Move): String {
    val face = faceName(move.face)
    return when {
        move.depth > 1 && move.width == 1 -> "$face · layer ${move.depth}"
        move.depth > 1 -> "$face · layers ${move.depth}–${move.depth + move.width - 1}"
        move.width > 1 -> "$face · ${move.width} layers"
        else -> "$face face"
    }
}
private fun moveDirection(move: Move) = when (move.quarterTurns) {
    1 -> "Clockwise, facing this side"
    2 -> "Half turn · 180°"
    else -> "Counterclockwise, facing this side"
}
