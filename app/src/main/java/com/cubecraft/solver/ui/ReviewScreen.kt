package com.cubecraft.solver.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
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
    onCell: (Face,Int) -> Unit,
    onAccept: () -> Unit,
    onRescan: () -> Unit,
    onBack: () -> Unit
) {
    var selected by remember { mutableStateOf(Face.F) }
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
            "Tap any wrong sticker directly on the unfolded cube. The enlarged face below is easier for precise edits.",
            color = Muted, fontSize = 12.sp, lineHeight = 16.sp
        )

        Spacer(Modifier.height(10.dp))
        Surface(
            Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, Outline)
        ) {
            CubeNet(size, faces, palette, onCell, Modifier.padding(vertical = 10.dp))
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Face.entries.forEach { f ->
                if (selected == f) Button(
                    onClick = { selected = f }, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) { Text(f.symbol.toString(), fontWeight = FontWeight.Black) }
                else OutlinedButton(
                    onClick = { selected = f }, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) { Text(f.symbol.toString(), fontWeight = FontWeight.Bold, color = InkSoft) }
            }
        }

        Spacer(Modifier.height(8.dp))
        Surface(
            Modifier.fillMaxWidth().weight(1f), color = Panel, shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, Outline)
        ) {
            Box(contentAlignment = Alignment.Center) {
                FaceGrid(
                    size, faces.getValue(selected), palette,
                    Modifier.fillMaxWidth(if (size == 3) .66f else .76f).aspectRatio(1f)
                ) { idx -> onCell(selected, idx) }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onRotate(selected) }, modifier = Modifier.weight(1f)) {
                Text("ROTATE ${selected.symbol} ↻", fontSize = 10.sp)
            }
            OutlinedButton(onClick = onRescan, modifier = Modifier.weight(1f)) { Text("RESCAN", fontSize = 10.sp) }
        }

        Spacer(Modifier.height(8.dp))
        val ok = report?.ok == true
        Surface(
            color = if (ok) SuccessSoft else DangerSoft,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, if (ok) Success.copy(alpha = .25f) else Danger.copy(alpha = .20f))
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp)) {
                Text(if (ok) "STATE VALID" else "CHECK REQUIRED", color = if (ok) Success else Danger, fontWeight = FontWeight.Black, fontSize = 10.sp)
                Text(report?.messages?.joinToString("\n") ?: "Validating…", color = InkSoft, fontSize = 10.sp, lineHeight = 13.sp)
                message?.let { Text(it, color = Muted, fontSize = 9.sp) }
            }
        }
        Spacer(Modifier.height(7.dp))
        Button(onClick = onAccept, enabled = ok, modifier = Modifier.fillMaxWidth().height(51.dp), shape = RoundedCornerShape(15.dp)) {
            Text("OPEN 3D CUBE", fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(9.dp))
    }
}

@Composable
private fun CubeNet(
    size: Int,
    faces: Map<Face,List<Face>>,
    palette: Map<Face,RgbColor>,
    onCell: (Face,Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val faceSize: Dp = if (size == 3) 72.dp else 76.dp
    val gap = 3.dp
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            Spacer(Modifier.size(faceSize))
            NetFace(Face.U, faceSize, size, faces, palette, onCell)
            Spacer(Modifier.size(faceSize))
            Spacer(Modifier.size(faceSize))
        }
        Spacer(Modifier.height(gap))
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            NetFace(Face.L, faceSize, size, faces, palette, onCell)
            NetFace(Face.F, faceSize, size, faces, palette, onCell)
            NetFace(Face.R, faceSize, size, faces, palette, onCell)
            NetFace(Face.B, faceSize, size, faces, palette, onCell)
        }
        Spacer(Modifier.height(gap))
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            Spacer(Modifier.size(faceSize))
            NetFace(Face.D, faceSize, size, faces, palette, onCell)
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
    onCell: (Face,Int) -> Unit
) {
    Box(
        Modifier.size(side).border(1.dp, Outline, RoundedCornerShape(6.dp)).padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        FaceGrid(size, faces.getValue(face), palette, Modifier.fillMaxSize()) { idx -> onCell(face, idx) }
        Text(
            face.symbol.toString(),
            modifier = Modifier.align(Alignment.TopStart).background(Panel.copy(alpha=.82f), RoundedCornerShape(4.dp)).padding(horizontal=3.dp),
            color = Ink, fontSize = 8.sp, fontWeight = FontWeight.Black
        )
    }
}
