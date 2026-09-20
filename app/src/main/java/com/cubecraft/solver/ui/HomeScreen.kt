package com.cubecraft.solver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Move

@Composable
fun HomeScreen(onScan: (Int) -> Unit, onVirtual: (Int) -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(3) }
    val cube = remember(selected) {
        CubeState(selected).also { it.applyAll(Move.parseAlgorithm("R U F'")) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = CubeDesign.Gutter)) {
        ScreenHeader("cubik", "Your next move, made clear") {
            AppIcon(CubeIcon.Cube, tint = Accent)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Eyebrow("A fresh perspective")
            Spacer(Modifier.height(12.dp))
            Text("Find your\nnext move.", style = MaterialTheme.typography.displaySmall, color = Ink, textAlign = TextAlign.Center)
            Cube3D(cube, selected, defaultPalette, null, Modifier.fillMaxWidth().height(228.dp))
            Text("Scan your cube. Follow each turn.\nSee everything fall into place.", style = MaterialTheme.typography.bodyMedium, color = InkSoft, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 12.dp)) {
            ChoiceBar(listOf("3 × 3", "5 × 5"), if (selected == 3) 0 else 1, { selected = if (it == 0) 3 else 5 }, Modifier.fillMaxWidth())
            AppButton("Scan a cube", { onScan(selected) }, Modifier.fillMaxWidth(), icon = CubeIcon.Scan)
            AppButton("Explore in 3D", { onVirtual(selected) }, Modifier.fillMaxWidth(), primary = false, icon = CubeIcon.Cube)
            Text("On your device. Always offline.", Modifier.fillMaxWidth().padding(top = 2.dp), color = Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}
