package com.cubecraft.solver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
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
    Column(Modifier.fillMaxSize().background(AppBg).padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
                Text("‹  BACK", color = InkSoft, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Text("SCAN REVIEW", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(5.dp))
        Text("Check the digital twin", color = Ink, fontSize = 29.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(5.dp))
        Text("Tap a wrong sticker to change its color. Rotate only if the scanned face orientation is wrong.", color = Muted, fontSize = 13.sp, lineHeight = 18.sp)

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Face.entries.forEach { f ->
                val active = selected == f
                val colors = if (active) ButtonDefaults.buttonColors(containerColor = Accent, contentColor = androidx.compose.ui.graphics.Color.White)
                    else ButtonDefaults.outlinedButtonColors(contentColor = InkSoft)
                if (active) Button(
                    onClick = { selected = f }, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 7.dp), colors = colors
                ) { Text(f.symbol.toString(), fontWeight = FontWeight.Black) }
                else OutlinedButton(
                    onClick = { selected = f }, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 7.dp), colors = colors
                ) { Text(f.symbol.toString(), fontWeight = FontWeight.Bold) }
            }
        }

        Spacer(Modifier.height(14.dp))
        Surface(
            Modifier.fillMaxWidth().weight(1f),
            color = Panel, shape = RoundedCornerShape(26.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Outline)
        ) {
            Box(contentAlignment = Alignment.Center) {
                FaceGrid(size, faces.getValue(selected), palette, Modifier.fillMaxWidth(.82f).aspectRatio(1f)) { idx -> onCell(selected, idx) }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onRotate(selected) }, modifier = Modifier.weight(1f)) { Text("ROTATE ↻", fontSize = 11.sp) }
            OutlinedButton(onClick = onRescan, modifier = Modifier.weight(1f)) { Text("RESCAN", fontSize = 11.sp) }
        }

        Spacer(Modifier.height(10.dp))
        val ok = report?.ok == true
        Surface(
            color = if (ok) SuccessSoft else DangerSoft,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (ok) Success.copy(alpha = .25f) else Danger.copy(alpha = .20f))
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text(if (ok) "STATE VALID" else "CHECK REQUIRED", color = if (ok) Success else Danger, fontWeight = FontWeight.Black, fontSize = 11.sp)
                Spacer(Modifier.height(3.dp))
                Text(report?.messages?.joinToString("\n") ?: "Validating…", color = InkSoft, fontSize = 11.sp, lineHeight = 15.sp)
                message?.let { Text(it, color = Muted, fontSize = 10.sp) }
            }
        }
        Spacer(Modifier.height(9.dp))
        Button(onClick = onAccept, enabled = ok, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
            Text("OPEN 3D CUBE", fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(12.dp))
    }
}
