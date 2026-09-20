package com.cubecraft.solver.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.scanner.RgbColor
import com.cubecraft.solver.scanner.StickerGuess
import com.cubecraft.solver.solver.ValidationReport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    size: Int, faces: Map<Face, List<Face>>, palette: Map<Face, RgbColor>,
    colorFaces: Map<StickerGuess, Face>, report: ValidationReport?, message: String?,
    onRotate: (Face) -> Unit, onSetColor: (Face, Int, Face) -> Unit,
    onAccept: () -> Unit, onRescan: () -> Unit, onBack: () -> Unit
) {
    var editingFace by remember { mutableStateOf<Face?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = CubeDesign.Gutter)) {
        ScreenHeader("Your cube", "All six faces captured", onBack)
        ScanSteps(5, Modifier.padding(bottom = 20.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("One final look.", style = MaterialTheme.typography.headlineMedium)
            Text("Tap a face to adjust its colors or orientation before solving.", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
            AppPanel {
                Eyebrow("$size × $size · unfolded view")
                CubeNet(size, faces, palette) { editingFace = it }
            }
            when {
                report == null -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent, trackColor = Panel2)
                    Text("Checking your cube…", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                }
                report.ok -> StatusNote("Ready to solve", "All pieces and colors are consistent.", success = true)
                else -> StatusNote("A detail needs attention", report.messages.joinToString("\n").ifBlank { message.orEmpty() }, error = true)
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppButton("Scan again", onRescan, Modifier.weight(1f), primary = false)
            AppButton("Solve cube", onAccept, Modifier.weight(1.2f), enabled = report?.ok == true)
        }
    }
    editingFace?.let { face ->
        var brush by remember(face) { mutableStateOf<StickerGuess?>(null) }
        ModalBottomSheet(
            onDismissRequest = { editingFace = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AppBg, contentColor = Ink
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = CubeDesign.Gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ScreenHeader("${faceName(face)} face", "The center stays fixed") {
                    AppIconButton(CubeIcon.Rotate, "Rotate face clockwise", { onRotate(face) })
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val colors = faces.getValue(face)
                    StickerEditor(size, colors.map { faceColor(it, palette) }, colors.map { faceName(it) }, brush != null,
                        { index -> brush?.let(colorFaces::get)?.let { onSetColor(face, index, it) } },
                        Modifier.widthIn(max = 340.dp).fillMaxWidth(), locked = size * size / 2)
                }
                Eyebrow("Choose a color, then paint")
                ColorPalette(brush) { brush = it }
                AppButton("Done", { editingFace = null }, Modifier.fillMaxWidth(), icon = CubeIcon.Check)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

internal fun faceName(face: Face) = when (face) {
    Face.U -> "Top"; Face.D -> "Bottom"; Face.F -> "Front"
    Face.B -> "Back"; Face.L -> "Left"; Face.R -> "Right"
}

@Composable
private fun CubeNet(n: Int, faces: Map<Face, List<Face>>, palette: Map<Face, RgbColor>, onFace: (Face) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 6.dp
        val side = (maxWidth - gap * 3) / 4
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) {
            listOf(listOf(null, Face.U, null, null), listOf(Face.L, Face.F, Face.R, Face.B), listOf(null, Face.D, null, null)).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { face ->
                        if (face == null) Spacer(Modifier.width(side).height(side + 22.dp))
                        else NetFace(face, side, n, faces, palette, onFace)
                    }
                }
            }
        }
    }
}

@Composable
private fun NetFace(face: Face, side: Dp, n: Int, faces: Map<Face, List<Face>>, palette: Map<Face, RgbColor>, onFace: (Face) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(side)) {
        Surface(
            onClick = { onFace(face) }, modifier = Modifier.size(side).semantics { contentDescription = "Edit ${faceName(face).lowercase()} face" },
            color = MetalDark, shape = CubeDesign.SmallShape, border = BorderStroke(1.dp, Outline)
        ) { FaceGrid(n, faces.getValue(face), palette, Modifier.padding(3.dp).fillMaxSize()) }
        Text(face.symbol.toString(), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = InkSoft)
    }
}
