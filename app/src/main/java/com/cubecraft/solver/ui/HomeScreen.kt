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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubecraft.solver.BuildConfig

@Composable
fun HomeScreen(onScan: (Int) -> Unit, onVirtual: (Int) -> Unit) {
    var selected by remember { mutableIntStateOf(3) }

    Column(
        Modifier.fillMaxSize().background(AppBg).padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "CUBIK/SOLVER",
                color = Ink,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .8.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                "v" + BuildConfig.VERSION_NAME + "  //  OFFLINE",
                color = Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Outline, thickness = 1.dp)

        Spacer(Modifier.weight(.72f))
        Text(
            "CUBIK",
            color = Ink,
            fontFamily = FontFamily.Monospace,
            fontSize = 45.sp,
            lineHeight = 45.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Text(
            "SOLVER",
            color = Accent,
            fontFamily = FontFamily.Monospace,
            fontSize = 45.sp,
            lineHeight = 45.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "CAMERA  /  RECONSTRUCT  /  SOLVE",
            color = Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = .65.sp
        )

        Spacer(Modifier.height(36.dp))
        Row(
            Modifier.fillMaxWidth().border(1.dp, InkSoft, RoundedCornerShape(5.dp)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            listOf(3, 5).forEach { n ->
                val active = selected == n
                Box(
                    Modifier.weight(1f).height(50.dp)
                        .background(if (active) Ink else AppBg, RoundedCornerShape(3.dp))
                        .clickable { selected = n },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        n.toString() + " × " + n,
                        color = if (active) Panel else Ink,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onScan(selected) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(5.dp)
        ) {
            Text(
                "SCAN REAL CUBE  →",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .5.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { onVirtual(selected) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(5.dp),
            border = BorderStroke(1.dp, InkSoft)
        ) {
            Text(
                "OPEN VIRTUAL CUBE",
                color = Ink,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.weight(1f))
        Text(
            "LOCAL CV  //  NO CLOUD  //  ANDROID 10+",
            color = Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            letterSpacing = .35.sp
        )
        Spacer(Modifier.height(20.dp))
    }
}
