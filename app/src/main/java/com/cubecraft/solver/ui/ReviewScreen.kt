package com.cubecraft.solver.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.scanner.RgbColor
import com.cubecraft.solver.solver.ValidationReport

@Composable
fun ReviewScreen(
    size: Int,
    faces: Map<Face, List<Face>>,
    palette: Map<Face, RgbColor>,
    report: ValidationReport?,
    message: String?,
    onRotate: (Face) -> Unit,
    onSetColor: (Face, Int, Face) -> Unit,
    onAccept: () -> Unit,
    onRescan: () -> Unit,
    onBack: () -> Unit
) {
    var editingFace by remember { mutableStateOf<Face?>(null) }

    Column(Modifier.fillMaxSize().background(AppBg).padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("← HOME", color = Ink, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("SCAN / REVIEW", color = Muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "CUBE NET",
            modifier = Modifier.fillMaxWidth(),
            color = Ink,
            fontFamily = FontFamily.Monospace,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            "TAP A FACE TO EDIT",
            color = Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = .5.sp
        )

        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            CubeNet(size, faces, palette) { editingFace = it }
        }

        ReviewStatusLine(report, message)
        Spacer(Modifier.height(10.dp))
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
                onClick = onAccept,
                enabled = report?.ok == true,
                modifier = Modifier.weight(1.55f).height(50.dp),
                shape = RoundedCornerShape(5.dp)
            ) {
                Text("OPEN 3D →", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    editingFace?.let { face ->
        FaceEditorOverlay(
            size = size,
            face = face,
            colors = faces.getValue(face),
            palette = palette,
            onSetColor = { index, color -> onSetColor(face, index, color) },
            onRotate = { onRotate(face) },
            onDismiss = { editingFace = null }
        )
    }
}

@Composable
private fun FaceEditorOverlay(
    size: Int,
    face: Face,
    colors: List<Face>,
    palette: Map<Face, RgbColor>,
    onSetColor: (Int, Face) -> Unit,
    onRotate: () -> Unit,
    onDismiss: () -> Unit
) {
    val center = size * size / 2
    var brush by remember(face) { mutableStateOf<Face?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .46f)).padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Panel,
                shape = RoundedCornerShape(7.dp),
                border = BorderStroke(1.dp, Ink)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "FACE " + face.symbol,
                            color = Ink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onRotate) {
                            Text("ROTATE ↻", color = InkSoft, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                        }
                        TextButton(onClick = onDismiss) {
                            Text("DONE", color = Accent, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        if (brush == null) "SELECT A COLOR, THEN TAP STICKERS" else "BRUSH / " + brush!!.symbol,
                        color = if (brush == null) Muted else Accent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )

                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        FaceGrid(
                            gridSize = size,
                            values = colors,
                            palette = palette,
                            modifier = Modifier.fillMaxWidth(if (size == 3) .78f else .9f).aspectRatio(1f),
                            onTap = { idx ->
                                if (idx != center) brush?.let { onSetColor(idx, it) }
                            }
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Face.entries.forEach { color ->
                            val active = brush == color
                            Surface(
                                modifier = Modifier.weight(1f).height(44.dp),
                                onClick = { brush = color },
                                color = faceColor(color, palette),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(if (active) 3.dp else 1.dp, if (active) Ink else Color.Black.copy(alpha = .25f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        color.symbol.toString(),
                                        color = stickerTextColor(faceColor(color, palette)),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "CENTER LOCKED  ·  changes validate immediately",
                        modifier = Modifier.fillMaxWidth(),
                        color = Muted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewStatusLine(report: ValidationReport?, message: String?) {
    val text = when {
        report == null -> "… VALIDATING"
        report.ok -> "● STATE VALID"
        else -> "× " + (report.messages.firstOrNull() ?: message ?: "CHECK REQUIRED")
    }
    val color = when {
        report == null -> Muted
        report.ok -> Success
        else -> Danger
    }
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        maxLines = 2,
        lineHeight = 12.sp
    )
}

@Composable
private fun CubeNet(
    size: Int,
    faces: Map<Face, List<Face>>,
    palette: Map<Face, RgbColor>,
    onFace: (Face) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 4.dp
        val faceSize = ((maxWidth - gap * 3) / 4).coerceAtMost(if (size == 3) 88.dp else 92.dp)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Spacer(Modifier.size(faceSize))
                NetFace(Face.U, faceSize, size, faces, palette, onFace)
                Spacer(Modifier.size(faceSize))
                Spacer(Modifier.size(faceSize))
            }
            Spacer(Modifier.height(gap))
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                NetFace(Face.L, faceSize, size, faces, palette, onFace)
                NetFace(Face.F, faceSize, size, faces, palette, onFace)
                NetFace(Face.R, faceSize, size, faces, palette, onFace)
                NetFace(Face.B, faceSize, size, faces, palette, onFace)
            }
            Spacer(Modifier.height(gap))
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Spacer(Modifier.size(faceSize))
                NetFace(Face.D, faceSize, size, faces, palette, onFace)
                Spacer(Modifier.size(faceSize))
                Spacer(Modifier.size(faceSize))
            }
        }
    }
}

@Composable
private fun NetFace(
    face: Face,
    side: Dp,
    size: Int,
    faces: Map<Face, List<Face>>,
    palette: Map<Face, RgbColor>,
    onFace: (Face) -> Unit
) {
    Box(
        Modifier.size(side).border(1.dp, InkSoft, RoundedCornerShape(4.dp)).clickable { onFace(face) }.padding(2.dp)
    ) {
        FaceGrid(
            gridSize = size,
            values = faces.getValue(face),
            palette = palette,
            modifier = Modifier.fillMaxSize(),
            onTap = { onFace(face) }
        )
        Text(
            face.symbol.toString(),
            modifier = Modifier.align(Alignment.TopStart).background(Ink).padding(horizontal = 3.dp, vertical = 1.dp),
            color = Panel,
            fontFamily = FontFamily.Monospace,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun stickerTextColor(background: Color): Color =
    if (background.luminance() > .5f) Ink else Color.White
