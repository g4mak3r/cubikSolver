package com.cubecraft.solver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** One shared editor for scan confirmation and correction. Drag painting and TalkBack both work. */
@Composable
fun StickerEditor(
    n: Int, colors: List<Color>, labels: List<String>, enabled: Boolean,
    onPaint: (Int) -> Unit, modifier: Modifier = Modifier,
    locked: Int = -1, corrected: Set<Int> = emptySet(), uncertain: Set<Int> = emptySet()
) {
    val paint by rememberUpdatedState(onPaint)
    Column(
        modifier.aspectRatio(1f).background(MetalDark, CubeDesign.ControlShape)
            .border(1.dp, Outline, CubeDesign.ControlShape).padding(6.dp)
            .pointerInput(n, enabled, locked) {
                if (!enabled) return@pointerInput
                fun cellAt(p: Offset): Int? {
                    if (p.x < 0 || p.y < 0 || p.x >= size.width || p.y >= size.height) return null
                    return ((p.y / size.height * n).toInt() * n + (p.x / size.width * n).toInt()).takeIf { it != locked }
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var last = cellAt(down.position)
                    last?.let(paint)
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (change.pressed) {
                                val next = cellAt(change.position)
                                if (next != null && next != last) { paint(next); last = next }
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        repeat(n) { row ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                repeat(n) { col ->
                    val index = row * n + col
                    val color = colors.getOrElse(index) { Panel2 }
                    val shape = RoundedCornerShape(if (n == 3) 10.dp else 6.dp)
                    Box(
                        Modifier.weight(1f).fillMaxHeight().padding(2.dp).background(color, shape)
                            .border(if (index in corrected) 2.dp else 0.dp, if (index in corrected) Ink else Color.Transparent, shape)
                            .semantics {
                                contentDescription = "Row ${row + 1}, column ${col + 1}, ${labels.getOrElse(index) { "unknown" }}" + if (index == locked) ", fixed center" else ""
                                if (enabled && index != locked) {
                                    role = Role.Button
                                    onClick("Paint sticker") { paint(index); true }
                                }
                            }, contentAlignment = Alignment.Center
                    ) {
                        if (index in uncertain && index !in corrected) {
                            Text("?", color = if (color.luminance() > .45f) Color.Black else Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                        if (index == locked) Box(Modifier.size(5.dp).background(if (color.luminance() > .45f) Color.Black else Color.White, RoundedCornerShape(3.dp)))
                    }
                }
            }
        }
    }
}
