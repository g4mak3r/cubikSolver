package com.cubecraft.solver.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
        Modifier.fillMaxSize().background(AppBg).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Text(
                "CUBIK/SOLVER",
                color = Ink,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp
            )
            Spacer(Modifier.weight(1f))
            androidx.compose.material3.Text(
                BuildConfig.VERSION_NAME,
                color = Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
        }

        Spacer(Modifier.weight(1f))

        Box(
            Modifier.size(148.dp)
                .border(1.dp, Outline, RoundedCornerShape(10.dp))
                .background(Panel, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.Text(
                    "CUBIK",
                    color = Ink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 34.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                androidx.compose.material3.Text(
                    "SOLVER",
                    color = Accent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )
            }
        }

        Spacer(Modifier.height(34.dp))

        Row(
            Modifier.fillMaxWidth()
                .border(1.dp, Outline, RoundedCornerShape(4.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            listOf(3, 5).forEach { n ->
                val active = selected == n
                Box(
                    Modifier.weight(1f).height(48.dp)
                        .background(if (active) Panel2 else AppBg, RoundedCornerShape(2.dp))
                        .clickable { selected = n },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        "$n×$n",
                        color = if (active) Accent else InkSoft,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = { onScan(selected) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(3.dp)
        ) {
            androidx.compose.material3.Text(
                "SCAN",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Spacer(Modifier.height(7.dp))

        OutlinedButton(
            onClick = { onVirtual(selected) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(3.dp),
            border = BorderStroke(1.dp, Outline)
        ) {
            androidx.compose.material3.Text(
                "VIRTUAL",
                color = InkSoft,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .8.sp
            )
        }

        Spacer(Modifier.weight(1.2f))
    }
}
