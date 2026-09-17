package com.cubecraft.solver.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    val center = stickers.getOrNull(expected / 2)
    val uncertain = stickers.count { it.confidence < .56f }

    Column(
        Modifier.fillMaxSize().background(AppBg).padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(12.dp))
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

        Spacer(Modifier.height(8.dp))
        Text("Check ${pose.title.lowercase()}", color = Ink, fontSize = 29.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(5.dp))
        Text(
            "Compare every square with the real cube. If even one color is wrong, rescan this face now.",
            color = Muted,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        Spacer(Modifier.height(16.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Panel,
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, Outline)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("CAMERA READ", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        Text(
                            center?.guess?.let { "Center: ${it.label}" } ?: "Center: ?",
                            color = Ink,
                            fontSize = 18.sp,
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

                Spacer(Modifier.height(14.dp))
                PreviewGrid(size = size, stickers = stickers)

                Spacer(Modifier.height(12.dp))
                Text(
                    if (uncertain == 0) {
                        "All sampled cells look confident. Still compare them with the physical face."
                    } else {
                        "$uncertain sampled cell${if (uncertain == 1) "" else "s"} look uncertain. If the displayed colors do not match the cube, rescan this face."
                    },
                    color = if (uncertain == 0) Muted else Danger,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }

        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = onRescan,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("RESCAN THIS FACE", color = InkSoft, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                if (index == 5) "USE FACE & REVIEW CUBE" else "USE FACE · NEXT",
                fontWeight = FontWeight.Black
            )
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun PreviewGrid(size: Int, stickers: List<LiveSticker>) {
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
                    val rgb = sticker?.rgb ?: RgbColor(45, 49, 57)
                    val fill = Color(rgb.argb())
                    val textColor = readableTextColor(rgb)
                    val lowConfidence = (sticker?.confidence ?: 0f) < .56f

                    Surface(
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(2.dp),
                        color = fill,
                        shape = RoundedCornerShape(if (size == 3) 12.dp else 7.dp),
                        border = BorderStroke(
                            if (lowConfidence) 2.dp else 1.dp,
                            if (lowConfidence) Danger else Color.Black.copy(alpha = .28f)
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                sticker?.guess?.label ?: "?",
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

private fun readableTextColor(rgb: RgbColor): Color {
    val luminance = (0.299 * rgb.r + 0.587 * rgb.g + 0.114 * rgb.b) / 255.0
    return if (luminance > .58) Color(0xFF101318) else Color.White
}
