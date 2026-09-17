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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.scanner.RgbColor
import com.cubecraft.solver.solver.ValidationReport

/**
 * Final six-face review.
 *
 * The base screen deliberately contains only the unfolded cube net. A face editor is an overlay,
 * never a second permanently visible/cropped grid. Tapping any face opens that face large above
 * the net; the user taps one sticker and then chooses its real color from the palette below.
 */
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

    Column(
        Modifier
            .fillMaxSize()
            .background(AppBg)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onBack,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 7.dp)
            ) {
                Text("‹  BACK", color = InkSoft, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Text("SCAN REVIEW", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }

        Text("Check the cube net", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Black)
        Text(
            "Compare the net with the real cube. Tap a face only if you need to correct it.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )

        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            color = Panel,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Outline)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                CubeNet(
                    size = size,
                    faces = faces,
                    palette = palette,
                    onFace = { editingFace = it }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        CompactReviewStatus(report = report, message = message)
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onRescan,
                modifier = Modifier.weight(.8f).height(50.dp),
                shape = RoundedCornerShape(15.dp)
            ) {
                Text("RESCAN", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onAccept,
                enabled = report?.ok == true,
                modifier = Modifier.weight(1.35f).height(50.dp),
                shape = RoundedCornerShape(15.dp)
            ) {
                Text("OPEN 3D CUBE", fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(10.dp))
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

/** A single modal overlay: large face first, explicit color palette directly underneath. */
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
    var selectedCell by remember(face, size) { mutableIntStateOf(0) }
    val selectedColor = colors.getOrElse(selectedCell) { face }
    val centerSelected = selectedCell == center

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .28f))
                .padding(horizontal = 14.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                color = Panel,
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 18.dp,
                border = BorderStroke(1.dp, Outline)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("EDIT FACE ${face.symbol}", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            Text("Tap the wrong sticker", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Black)
                        }
                        TextButton(onClick = onDismiss) {
                            Text("DONE", color = Accent, fontWeight = FontWeight.Black)
                        }
                    }

                    Text(
                        "Select one cell, then choose its real color below. The fixed center is locked.",
                        color = Muted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        FaceGrid(
                            gridSize = size,
                            values = colors,
                            palette = palette,
                            modifier = Modifier
                                .fillMaxWidth(if (size == 3) .78f else .88f)
                                .aspectRatio(1f),
                            onTap = { selectedCell = it }
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (centerSelected) "CENTER · LOCKED" else "STICKER ${selectedCell + 1} · ${selectedColor.symbol}",
                            color = if (centerSelected) Muted else Ink,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = onRotate,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("ROTATE ${face.symbol} ↻", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Face.entries.forEach { color ->
                            val active = color == selectedColor
                            Surface(
                                modifier = Modifier.weight(1f).height(46.dp),
                                onClick = { if (!centerSelected) onSetColor(selectedCell, color) },
                                enabled = !centerSelected,
                                color = faceColor(color, palette),
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(
                                    if (active) 3.dp else 1.dp,
                                    if (active) Accent else Color.Black.copy(alpha = .24f)
                                )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        color.symbol.toString(),
                                        color = if (color == Face.U || color == Face.D) Ink else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactReviewStatus(report: ValidationReport?, message: String?) {
    val validating = report == null
    val ok = report?.ok == true
    val accent = when {
        validating -> Muted
        ok -> Success
        else -> Danger
    }
    val text = when {
        validating -> "VALIDATING CUBE…"
        ok -> "STATE VALID · READY"
        else -> report?.messages?.firstOrNull() ?: message ?: "CHECK REQUIRED"
    }

    Surface(
        color = when {
            validating -> Panel2
            ok -> SuccessSoft
            else -> DangerSoft
        },
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = .22f))
    ) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            color = accent,
            fontSize = 9.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CubeNet(
    size: Int,
    faces: Map<Face, List<Face>>,
    palette: Map<Face, RgbColor>,
    onFace: (Face) -> Unit
) {
    val faceSize: Dp = when (size) {
        3 -> 78.dp
        else -> 82.dp
    }
    val gap = 4.dp

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
        Modifier
            .size(side)
            .border(1.dp, Outline, RoundedCornerShape(8.dp))
            .clickable { onFace(face) }
            .padding(2.dp),
        contentAlignment = Alignment.Center
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
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(Panel.copy(alpha = .88f), RoundedCornerShape(4.dp))
                .padding(horizontal = 3.dp),
            color = Ink,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black
        )
    }
}
