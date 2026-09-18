package com.cubecraft.solver.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import com.cubecraft.solver.scanner.ScanPose
import com.cubecraft.solver.scanner.StickerGuess

/**
 * Per-face confirmation is a tiny paint tool:
 * 1) choose a color once;
 * 2) tap or drag across every wrong cell;
 * 3) accept the face.
 *
 * The preview intentionally uses ideal digital cube colors, never the photographed RGB values.
 * Camera RGB remains calibration data inside the scanner/classifier.
 */
@Composable
fun FaceConfirmScreen(
    size: Int,
    pose: ScanPose,
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
    val uncertain = stickers.indices.count { idx ->
        idx !in overrides && (stickers.getOrNull(idx)?.confidence ?: 0f) < .56f
    }

    var brush by remember(index, size) { mutableStateOf<StickerGuess?>(null) }
    var cameraBrush by remember(index, size) { mutableStateOf(false) }
    val paintEnabled = brush != null || cameraBrush

    Column(
        Modifier.fillMaxSize().background(AppBg).padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onRescan,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)
            ) {
                Text("← CAMERA", fontFamily = FontFamily.Monospace, color = Ink, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(
                (index + 1).toString().padStart(2, '0') + " / 06",
                fontFamily = FontFamily.Monospace,
                color = Muted,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(2.dp))
        Text(
            pose.title.uppercase(),
            modifier = Modifier.fillMaxWidth(),
            color = Ink,
            fontFamily = FontFamily.Monospace,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            "SELECT COLOR  →  TAP / DRAG WRONG CELLS",
            modifier = Modifier.fillMaxWidth(),
            color = Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = .45.sp
        )

        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            PaintableScanGrid(
                gridSize = size,
                stickers = stickers,
                overrides = overrides,
                enabled = paintEnabled,
                onPaint = { cell ->
                    if (cameraBrush) onClearColor(cell)
                    else brush?.let { onSetColor(cell, it) }
                }
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    brush != null -> "BRUSH / " + brush!!.label
                    cameraBrush -> "BRUSH / CAMERA"
                    else -> "CHOOSE BRUSH"
                },
                color = if (paintEnabled) Accent else Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Q " + (observation.quality * 100).toInt() + "  ·  ? " + uncertain + "  ·  EDIT " + overrides.size,
                color = Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
        }

        Spacer(Modifier.height(7.dp))
        BrushPalette(
            selected = brush,
            cameraSelected = cameraBrush,
            onColor = {
                brush = it
                cameraBrush = false
            },
            onCamera = {
                brush = null
                cameraBrush = true
            }
        )

        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Danger, RoundedCornerShape(5.dp))
                    .padding(9.dp),
                color = Danger,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 13.sp
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onRescan,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(5.dp),
                border = BorderStroke(1.dp, InkSoft)
            ) {
                Text("RESCAN", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1.55f).height(50.dp),
                shape = RoundedCornerShape(5.dp)
            ) {
                Text(
                    if (index == 5) "BUILD CUBE →" else "ACCEPT →",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
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
    enabled: Boolean,
    onPaint: (Int) -> Unit
) {
    val paintCell by rememberUpdatedState(onPaint)
    val interaction = Modifier.pointerInput(gridSize, enabled) {
        if (!enabled) return@pointerInput

        fun cellAt(position: Offset): Int? {
            if (position.x < 0f || position.y < 0f ||
                position.x >= size.width || position.y >= size.height
            ) return null
            val col = (position.x / (size.width.toFloat() / gridSize)).toInt().coerceIn(0, gridSize - 1)
            val row = (position.y / (size.height.toFloat() / gridSize)).toInt().coerceIn(0, gridSize - 1)
            return row * gridSize + col
        }

        awaitEachGesture {
            var last = -1
            val down = awaitFirstDown(requireUnconsumed = false)
            cellAt(down.position)?.let {
                last = it
                paintCell(it)
            }
            do {
                val event = awaitPointerEvent()
                event.changes.forEach { change ->
                    if (change.pressed) {
                        val cell = cellAt(change.position)
                        if (cell != null && cell != last) {
                            last = cell
                            paintCell(cell)
                        }
                        change.consume()
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }

    Column(
        Modifier
            .fillMaxWidth(if (gridSize == 3) .78f else .9f)
            .aspectRatio(1f)
            .background(Ink, RoundedCornerShape(7.dp))
            .padding(if (gridSize == 3) 5.dp else 3.dp)
            .then(interaction)
    ) {
        for (r in 0 until gridSize) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (c in 0 until gridSize) {
                    val idx = r * gridSize + c
                    val sticker = stickers.getOrNull(idx)
                    val guess = overrides[idx] ?: sticker?.guess ?: StickerGuess.UNKNOWN
                    val corrected = idx in overrides
                    val uncertain = !corrected && (sticker?.confidence ?: 0f) < .56f
                    val rgb = idealRgbForGuess(guess)
                    val fill = Color(rgb.argb())

                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(1.5.dp)
                            .background(fill, RoundedCornerShape(if (gridSize == 3) 5.dp else 3.dp))
                            .border(
                                if (corrected || uncertain) 1.5.dp else .5.dp,
                                when {
                                    corrected -> Ink
                                    uncertain -> Danger
                                    else -> Color.Black.copy(alpha = .22f)
                                },
                                RoundedCornerShape(if (gridSize == 3) 5.dp else 3.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            guess.label,
                            color = readableTextColor(rgb),
                            fontFamily = FontFamily.Monospace,
                            fontSize = if (gridSize == 3) 17.sp else 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrushPalette(
    selected: StickerGuess?,
    cameraSelected: Boolean,
    onColor: (StickerGuess) -> Unit,
    onCamera: () -> Unit
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
            val active = selected == guess && !cameraSelected
            Surface(
                modifier = Modifier.weight(1f).height(43.dp),
                onClick = { onColor(guess) },
                color = Color(rgb.argb()),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(
                    if (active) 3.dp else 1.dp,
                    if (active) Ink else Color.Black.copy(alpha = .28f)
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        guess.label,
                        color = readableTextColor(rgb),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.weight(1.25f).height(43.dp),
            onClick = onCamera,
            color = if (cameraSelected) Ink else Panel,
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, InkSoft)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "AUTO",
                    color = if (cameraSelected) Panel else Ink,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
            }
        }
    }
}

private fun readableTextColor(rgb: RgbColor): Color {
    val luminance = (0.299 * rgb.r + 0.587 * rgb.g + 0.114 * rgb.b) / 255.0
    return if (luminance > .58) Color(0xFF11120F) else Color.White
}
