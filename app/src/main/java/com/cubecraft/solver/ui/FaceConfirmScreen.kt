package com.cubecraft.solver.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubecraft.solver.scanner.FaceObservation
import com.cubecraft.solver.scanner.LiveSticker
import com.cubecraft.solver.scanner.RgbColor
import com.cubecraft.solver.scanner.ScanPose
import com.cubecraft.solver.scanner.StickerGuess

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
    var selectedCell by remember(index, size) { mutableIntStateOf(expected / 2) }
    val centerGuess = overrides[expected / 2] ?: stickers.getOrNull(expected / 2)?.guess
    val uncertain = stickers.indices.count { idx ->
        idx !in overrides && (stickers.getOrNull(idx)?.confidence ?: 0f) < .56f
    }

    Column(
        Modifier.fillMaxSize().background(AppBg).padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onRescan, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
                Text("‹  CAMERA", color = InkSoft, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Surface(color = AccentSoft, shape = RoundedCornerShape(999.dp)) {
                Text(
                    "FACE ${index + 1} / 6",
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = Accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Text("Check ${pose.title.lowercase()}", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap a wrong square and choose its real color. If the whole read is bad, rescan this face.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        Spacer(Modifier.height(10.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            color = Panel,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Outline)
        ) {
            Column(Modifier.padding(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("CAMERA READ", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        Text(
                            centerGuess?.let { "Center: ${it.label}" } ?: "Center: ?",
                            color = Ink,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        "${(observation.quality * 100).toInt()}%",
                        color = if (uncertain == 0) Success else Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(Modifier.height(10.dp))
                PreviewGrid(
                    size = size,
                    stickers = stickers,
                    overrides = overrides,
                    selected = selectedCell,
                    onSelect = { selectedCell = it }
                )

                Spacer(Modifier.height(10.dp))
                val selectedGuess = overrides[selectedCell]
                    ?: stickers.getOrNull(selectedCell)?.guess
                    ?: StickerGuess.UNKNOWN
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "CELL ${selectedCell + 1}: ${selectedGuess.label}",
                        color = Ink,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.weight(1f))
                    if (selectedCell in overrides) {
                        TextButton(
                            onClick = { onClearColor(selectedCell) },
                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text("CAMERA VALUE", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                ColorPickerRow(selectedGuess) { guess -> onSetColor(selectedCell, guess) }

                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        overrides.isNotEmpty() -> "${overrides.size} manual correction${if (overrides.size == 1) "" else "s"} will override camera classification."
                        uncertain == 0 -> "All sampled cells look confident. Still compare them with the physical face."
                        else -> "$uncertain sampled cell${if (uncertain == 1) "" else "s"} look uncertain. Check those cells closely."
                    },
                    color = if (overrides.isNotEmpty()) Accent else if (uncertain == 0) Muted else Danger,
                    fontSize = 9.sp,
                    lineHeight = 13.sp
                )
            }
        }

        message?.let {
            Spacer(Modifier.height(7.dp))
            Surface(color = DangerSoft, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Danger.copy(alpha=.2f))) {
                Text(
                    it,
                    Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp),
                    color = Danger,
                    fontSize = 9.sp,
                    lineHeight = 13.sp
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onRescan,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("RESCAN", color = InkSoft, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1.45f).height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    if (index == 5) "USE & BUILD CUBE" else "USE FACE · NEXT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PreviewGrid(
    size: Int,
    stickers: List<LiveSticker>,
    overrides: Map<Int, StickerGuess>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().aspectRatio(1f)
            .background(Color(0xFF0E1520), RoundedCornerShape(18.dp))
            .padding(if (size == 3) 8.dp else 5.dp)
    ) {
        for (r in 0 until size) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (c in 0 until size) {
                    val idx = r * size + c
                    val sticker = stickers.getOrNull(idx)
                    val effectiveGuess = overrides[idx] ?: sticker?.guess ?: StickerGuess.UNKNOWN
                    val rgb = if (effectiveGuess == StickerGuess.UNKNOWN) {
                        sticker?.rgb ?: RgbColor(45, 49, 57)
                    } else {
                        guessRgb(effectiveGuess)
                    }
                    val fill = Color(rgb.argb())
                    val textColor = readableTextColor(rgb)
                    val corrected = idx in overrides
                    val lowConfidence = !corrected && (sticker?.confidence ?: 0f) < .56f
                    val isSelected = idx == selected

                    Surface(
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(2.dp)
                            .clickable { onSelect(idx) },
                        color = fill,
                        shape = RoundedCornerShape(if (size == 3) 12.dp else 7.dp),
                        border = BorderStroke(
                            when {
                                isSelected -> 3.dp
                                lowConfidence -> 2.dp
                                else -> 1.dp
                            },
                            when {
                                isSelected -> Accent
                                corrected -> Color.White.copy(alpha = .9f)
                                lowConfidence -> Danger
                                else -> Color.Black.copy(alpha = .28f)
                            }
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                effectiveGuess.label,
                                color = textColor,
                                fontSize = if (size == 3) 18.sp else 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPickerRow(selected: StickerGuess, onPick: (StickerGuess) -> Unit) {
    val colors = listOf(
        StickerGuess.WHITE,
        StickerGuess.YELLOW,
        StickerGuess.RED,
        StickerGuess.ORANGE,
        StickerGuess.GREEN,
        StickerGuess.BLUE
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        colors.forEach { guess ->
            val rgb = guessRgb(guess)
            val active = selected == guess
            Surface(
                modifier = Modifier.weight(1f).height(40.dp).clickable { onPick(guess) },
                color = Color(rgb.argb()),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(if (active) 3.dp else 1.dp, if (active) Accent else Color.Black.copy(alpha=.24f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        guess.label,
                        color = readableTextColor(rgb),
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private fun guessRgb(guess: StickerGuess): RgbColor = when (guess) {
    StickerGuess.WHITE -> RgbColor(244, 244, 238)
    StickerGuess.YELLOW -> RgbColor(255, 213, 0)
    StickerGuess.RED -> RgbColor(195, 28, 37)
    StickerGuess.ORANGE -> RgbColor(255, 45, 0)
    StickerGuess.GREEN -> RgbColor(0, 155, 72)
    StickerGuess.BLUE -> RgbColor(0, 70, 173)
    StickerGuess.UNKNOWN -> RgbColor(65, 72, 84)
}

private fun readableTextColor(rgb: RgbColor): Color {
    val luminance = (0.299 * rgb.r + 0.587 * rgb.g + 0.114 * rgb.b) / 255.0
    return if (luminance > .58) Color(0xFF101318) else Color.White
}
