package com.cubecraft.solver.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.scanner.RgbColor
import com.cubecraft.solver.solver.ValidationReport

@Composable
fun ReviewScreen(
    size: Int,
    faces: Map<Face,List<Face>>,
    palette: Map<Face,RgbColor>,
    report: ValidationReport?,
    message: String?,
    onRotate: (Face) -> Unit,
    onSetColor: (Face,Int,Face) -> Unit,
    onAccept: () -> Unit,
    onRescan: () -> Unit,
    onBack: () -> Unit
) {
    var editingFace by remember { mutableStateOf<Face?>(null) }

    if (editingFace != null) {
        FaceEditScreen(
            size = size,
            face = editingFace!!,
            colors = faces.getValue(editingFace!!),
            palette = palette,
            onSetColor = { index, color -> onSetColor(editingFace!!, index, color) },
            onRotate = { onRotate(editingFace!!) },
            onDone = { editingFace = null }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(AppBg).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 7.dp)) {
                Text("‹  BACK", color = InkSoft, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Text("SCAN REVIEW", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }

        Text("Check the cube net", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Black)
        Text(
            "If everything matches the real cube, continue. Tap a face only when you need to correct it.",
            color = Muted, fontSize = 12.sp, lineHeight = 16.sp
        )

        Spacer(Modifier.height(14.dp))
        Surface(
            Modifier.fillMaxWidth().weight(1f),
            color = Panel,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Outline)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CubeNet(
                    size = size,
                    faces = faces,
                    palette = palette,
                    onFace = { editingFace = it },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 18.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        ReviewStatus(report = report, message = message)

        Spacer(Modifier.height(9.dp))
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
}

@Composable
private fun FaceEditScreen(
    size: Int,
    face: Face,
    colors: List<Face>,
    palette: Map<Face,RgbColor>,
    onSetColor: (Int,Face) -> Unit,
    onRotate: () -> Unit,
    onDone: () -> Unit
) {
    val center = size * size / 2
    var selectedCell by remember(face, size) { mutableIntStateOf(0) }
    val selectedColor = colors.getOrElse(selectedCell) { face }
    val centerSelected = selectedCell == center

    Column(Modifier.fillMaxSize().background(AppBg).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDone, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 7.dp)) {
                Text("‹  CUBE NET", color = InkSoft, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Text("EDIT ${face.symbol}", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }

        Text("Correct ${face.symbol} face", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Black)
        Text(
            "Select a wrong sticker, then choose its real color below. The fixed center stays locked.",
            color = Muted, fontSize = 12.sp, lineHeight = 16.sp
        )

        Spacer(Modifier.height(12.dp))
        Surface(
            Modifier.fillMaxWidth().weight(1f),
            color = Panel,
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, Outline)
        ) {
            Box(contentAlignment = Alignment.Center) {
                FaceGrid(
                    gridSize = size,
                    values = colors,
                    palette = palette,
                    modifier = Modifier
                        .fillMaxWidth(if (size == 3) .82f else .90f)
                        .aspectRatio(1f),
                    onTap = { selectedCell = it }
                )
            }
        }

        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (centerSelected) "CENTER · LOCKED" else "STICKER ${selectedCell + 1} · ${selectedColor.symbol}",
                color = if (centerSelected) Muted else Ink,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.weight(1f))
            Text("CHOOSE COLOR", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Face.entries.forEach { color ->
                val active = color == selectedColor
                Surface(
                    modifier = Modifier.weight(1f).height(44.dp),
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

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onRotate,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(15.dp)
            ) {
                Text("ROTATE ${face.symbol} ↻", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onDone,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(15.dp)
            ) {
                Text("DONE", fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ReviewStatus(report: ValidationReport?, message: String?) {
    val ok = report?.ok == true
    val validating = report == null
    val bg = when {
        validating -> Panel2
        ok -> SuccessSoft
        else -> DangerSoft
    }
    val accent = when {
        validating -> Muted
        ok -> Success
        else -> Danger
    }
    val title = when {
        validating -> "VALIDATING…"
        ok -> "STATE VALID"
        else -> "CHECK REQUIRED"
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = .22f))
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 9.dp)) {
            Text(title, color = accent, fontWeight = FontWeight.Black, fontSize = 10.sp)
            if (!ok && !validating) {
                Text(report?.messages?.joinToString("\n").orEmpty(), color = InkSoft, fontSize = 10.sp, lineHeight = 13.sp)
            }
            message?.takeIf { !ok }?.let { Text(it, color = Muted, fontSize = 9.sp) }
        }
    }
}

@Composable
private fun CubeNet(
    size: Int,
    faces: Map<Face,List<Face>>,
    palette: Map<Face,RgbColor>,
    onFace: (Face) -> Unit,
    modifier: Modifier = Modifier
) {
    val faceSize: Dp = if (size == 3) 78.dp else 80.dp
    val gap = 4.dp
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
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
    faces: Map<Face,List<Face>>,
    palette: Map<Face,RgbColor>,
    onFace: (Face) -> Unit
) {
    Box(
        Modifier
            .size(side)
            .border(1.dp, Outline, RoundedCornerShape(7.dp))
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
                .background(Panel.copy(alpha = .86f), RoundedCornerShape(4.dp))
                .padding(horizontal = 3.dp),
            color = Ink,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black
        )
    }
}
