package com.cubecraft.solver.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cubecraft.solver.scanner.FaceObservation
import com.cubecraft.solver.scanner.StickerGuess

@Composable
fun FaceConfirmScreen(
    size: Int, index: Int, observation: FaceObservation, overrides: Map<Int, StickerGuess>, message: String?,
    onSetColor: (Int, StickerGuess) -> Unit, onClearColor: (Int) -> Unit, onRescan: () -> Unit, onConfirm: () -> Unit
) {
    var brush by remember(index, size) { mutableStateOf<StickerGuess?>(null) }
    val guesses = List(size * size) { overrides[it] ?: observation.stickers.getOrNull(it)?.guess ?: StickerGuess.UNKNOWN }
    val uncertain = (0 until size * size).filter { it !in overrides && (observation.stickers.getOrNull(it)?.confidence ?: 0f) < .56f }.toSet()
    Column(Modifier.fillMaxSize().padding(horizontal = CubeDesign.Gutter)) {
        ScreenHeader("Check this face", "Face ${index + 1} of 6", onRescan)
        ScanSteps(index, Modifier.padding(bottom = 20.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Does it match?", style = MaterialTheme.typography.headlineMedium)
            Text("Choose a color, then tap or brush across any sticker to correct it.", color = InkSoft, style = MaterialTheme.typography.bodyMedium)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                StickerEditor(size, guesses.map { Color(idealRgbForGuess(it).argb()) }, guesses.map { it.displayName.lowercase() }, brush != null,
                    { cell -> brush?.let { onSetColor(cell, it) } }, Modifier.widthIn(max = 360.dp).fillMaxWidth(), corrected = overrides.keys, uncertain = uncertain)
            }
            if (uncertain.isNotEmpty()) Text("${uncertain.size} uncertain ${if (uncertain.size == 1) "color" else "colors"} marked with ?", color = SignalAmber, style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(if (brush == null) "Choose a paint color" else "${brush!!.displayName} selected", Modifier.weight(1f))
                TextButton(onClick = { repeat(size * size) { onClearColor(it) }; brush = null }) { Text("Reset colors") }
            }
            ColorPalette(brush) { brush = it }
            if (!message.isNullOrBlank()) StatusNote("Check the colors", message, error = true)
            Spacer(Modifier.height(4.dp))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppButton("Retake", onRescan, Modifier.weight(1f), primary = false)
            AppButton("Confirm", onConfirm, Modifier.weight(1.2f), icon = CubeIcon.Check)
        }
    }
}
