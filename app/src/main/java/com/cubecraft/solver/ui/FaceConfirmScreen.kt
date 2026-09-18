package com.cubecraft.solver.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubecraft.solver.scanner.FaceObservation
import com.cubecraft.solver.scanner.LiveSticker
import com.cubecraft.solver.scanner.RgbColor
import com.cubecraft.solver.scanner.StickerGuess

@Composable
fun FaceConfirmScreen(
    size: Int,
    index: Int,
    observation: FaceObservation,
    overrides: Map<Int, StickerGuess>,
    message: String?,
    onSetColor: (Int, StickerGuess) -> Unit,
    onClearColor: (Int) -> Unit,
    onRescan: () -> Unit,
    onConfirm: () -> Unit
) {
    val expected = size * size
    val stickers = if (observation.stickers.size == expected) {
        observation.stickers
    } else {
        observation.samples.take(expected).map {
            LiveSticker(it.rgb, StickerGuess.UNKNOWN, 0f)
        }
    }
    var brush by remember(index, size) { mutableStateOf<StickerGuess?>(null) }

    Column(
        Modifier.fillMaxSize().background(AppBg).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onRescan, contentPadding = PaddingValues(0.dp)) {
                Text("←", color = Ink, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(
                (index + 1).toString() + "/6",
                color = Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }

        Spacer(Modifier.weight(.45f))

        PaintableScanGrid(
            gridSize = size,
            stickers = stickers,
            overrides = overrides,
            brush = brush,
            onPaint = { cell ->
                brush?.let { onSetColor(cell, it) }
            }
        )

        Spacer(Modifier.weight(.45f))

        ColorBrush(
            selected = brush,
            onSelect = { brush = it }
        )

        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                color = Danger,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 12.sp,
                maxLines = 2
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = {
                    repeat(expected) { onClearColor(it) }
                    brush = null
                },
                modifier = Modifier.weight(.8f).height(48.dp),
                shape = RoundedCornerShape(3.dp),
                border = BorderStroke(1.dp, Outline)
            ) {
                Text("AUTO", color = InkSoft, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            }
            OutlinedButton(
                onClick = onRescan,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(3.dp),
                border = BorderStroke(1.dp, Outline)
            ) {
                Text("RESCAN", color = InkSoft, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1.35f).height(48.dp),
                shape = RoundedCornerShape(3.dp)
            ) {
                Text(
                    "CONFIRM",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .7.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PaintableScanGrid(
    gridSize: Int,
    stickers: List<LiveSticker>,
    overrides: Map<Int, StickerGuess>,
    brush: StickerGuess?,
    onPaint: (Int) -> Unit
) {
    val latestPaint by rememberUpdatedState(onPaint)
    val enabled = brush != null

    Column(
        Modifier
            .fillMaxWidth(if (gridSize == 3) .76f else .9f)
            .aspectRatio(1f)
            .background(MetalDark, RoundedCornerShape(7.dp))
            .border(1.dp, MetalLight, RoundedCornerShape(7.dp))
            .padding(if (gridSize == 3) 5.dp else 3.dp)
            .pointerInput(gridSize, enabled) {
                if (!enabled) return@pointerInput

                fun cellAt(position: Offset): Int? {
                    if (position.x !in 0f..size.width.toFloat() ||
                        position.y !in 0f..size.height.toFloat()
                    ) return null
                    val col = (position.x / (size.width.toFloat() / gridSize))
                        .toInt().coerceIn(0, gridSize - 1)
                    val row = (position.y / (size.height.toFloat() / gridSize))
                        .toInt().coerceIn(0, gridSize - 1)
                    return row * gridSize + col
                }

                awaitEachGesture {
                    var last = -1
                    val down = awaitFirstDown(requireUnconsumed = false)
                    cellAt(down.position)?.let {
                        last = it
                        latestPaint(it)
                    }
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (change.pressed) {
                                val cell = cellAt(change.position)
                                if (cell != null && cell != last) {
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
        for (row in 0 until gridSize) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0 until gridSize) {
                    val idx = row * gridSize + col
                    val sticker = stickers.getOrNull(idx)
                    val guess = overrides[idx] ?: sticker?.guess ?: StickerGuess.UNKNOWN
                    val rgb = idealRgbForGuess(guess)
                    val corrected = idx in overrides
                    Box(
                        Modifier.weight(1f).fillMaxHeight().padding(1.5.dp)
                            .background(
                                Color(rgb.argb()),
                                RoundedCornerShape(if (gridSize == 3) 5.dp else 3.dp)
                            )
                            .border(
                                if (corrected) 2.dp else .6.dp,
                                if (corrected) Accent else Color.Black.copy(alpha = .32f),
                                RoundedCornerShape(if (gridSize == 3) 5.dp else 3.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorBrush(
    selected: StickerGuess?,
    onSelect: (StickerGuess) -> Unit
) {
    val colors = listOf(
        StickerGuess.WHITE,
        StickerGuess.YELLOW,
        StickerGuess.RED,
        StickerGuess.ORANGE,
        StickerGuess.GREEN,
        StickerGuess.BLUE
    )

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        colors.forEach { guess ->
            val rgb = idealRgbForGuess(guess)
            val active = selected == guess
            Surface(
                modifier = Modifier.weight(1f).height(46.dp),
                onClick = { onSelect(guess) },
                color = Color(rgb.argb()),
                shape = RoundedCornerShape(3.dp),
                border = BorderStroke(
                    if (active) 3.dp else 1.dp,
                    if (active) Accent else Color.Black.copy(alpha = .32f)
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        guess.label,
                        color = readableTextColor(rgb),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

private fun readableTextColor(rgb: RgbColor): Color {
    val value = (0.299 * rgb.r + 0.587 * rgb.g + 0.114 * rgb.b) / 255.0
    return if (value > .58) Color.Black else Color.White
}
