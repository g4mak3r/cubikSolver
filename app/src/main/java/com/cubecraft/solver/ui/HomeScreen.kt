package com.cubecraft.solver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(onScan: (Int) -> Unit, onVirtual: (Int) -> Unit) {
    var selected by remember { mutableIntStateOf(3) }

    Column(
        Modifier.fillMaxSize().background(AppBg).padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.background(AccentSoft, RoundedCornerShape(999.dp))
                    .padding(horizontal = 11.dp, vertical = 7.dp)
            ) {
                Text("OFFLINE · CAMERA SOLVER", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Text("0.5.1", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(28.dp))
        Text("cubikSolver", fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, color = InkSoft)
        Spacer(Modifier.height(8.dp))
        Text("Your cube,\nreconstructed.", fontSize = 38.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black, color = Ink)
        Spacer(Modifier.height(10.dp))
        Text(
            "Scan six faces, inspect the digital twin, then solve with visual guidance.",
            color = Muted, fontSize = 14.sp, lineHeight = 20.sp
        )

        Spacer(Modifier.height(26.dp))
        Text("CUBE SIZE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().background(Panel2, RoundedCornerShape(16.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(3, 5).forEach { n ->
                val active = selected == n
                Surface(
                    modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(13.dp)).clickable { selected = n },
                    color = if (active) Panel else androidx.compose.ui.graphics.Color.Transparent,
                    shadowElevation = if (active) 2.dp else 0.dp,
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("$n × $n", color = if (active) Ink else Muted, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        PrimaryActionCard(
            title = "Scan a real cube",
            body = "Guided camera grid. Auto-detection when possible, manual capture when it isn't.",
            action = "START 6-FACE SCAN",
            onClick = { onScan(selected) }
        )
        Spacer(Modifier.height(10.dp))
        SecondaryActionCard(
            title = "Virtual lab",
            body = "Rotate the model, test legal moves, undo, redo and preview solver guidance.",
            action = "OPEN DIGITAL CUBE",
            onClick = { onVirtual(selected) }
        )

        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("ANDROID 10+", color = Muted, fontSize = 10.sp)
            Text("NO GOOGLE SERVICES", color = Muted, fontSize = 10.sp)
            Text("OFFLINE", color = Success, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PrimaryActionCard(title: String, body: String, action: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Accent)
            .clickable(onClick = onClick).padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(title, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(6.dp))
                Text(body, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .82f), fontSize = 13.sp, lineHeight = 18.sp)
            }
            Text("↗", color = androidx.compose.ui.graphics.Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        Text(action, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = .8.sp)
    }
}

@Composable
private fun SecondaryActionCard(title: String, body: String, action: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Panel)
            .border(1.dp, Outline, RoundedCornerShape(24.dp)).clickable(onClick = onClick).padding(20.dp)
    ) {
        Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 19.sp)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(16.dp))
        Text(action, color = Accent, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = .8.sp)
    }
}
