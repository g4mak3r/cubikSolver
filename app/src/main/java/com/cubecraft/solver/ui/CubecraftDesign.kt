package com.cubecraft.solver.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubecraft.solver.scanner.StickerGuess
import com.cubecraft.solver.scanner.canonicalStickerGuesses

/** Shared visual and motion tokens. Layouts use a 4dp grid and controls are at least 48dp. */
object CubeDesign {
    val Gutter = 20.dp
    val Gap = 12.dp
    val PanelShape = RoundedCornerShape(24.dp)
    val ControlShape = RoundedCornerShape(16.dp)
    val SmallShape = RoundedCornerShape(12.dp)
    const val PressMillis = 120
    const val ChangeMillis = 220
    const val ScreenMillis = 260
    const val GuideMillis = 1400
}

private fun type(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontSize = size.sp, lineHeight = height.sp, fontWeight = weight
)

@Composable
fun CubecraftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = AppBg, onBackground = Ink,
            surface = Panel, onSurface = Ink, surfaceVariant = Panel2, onSurfaceVariant = InkSoft,
            primary = Accent, onPrimary = AppBg, primaryContainer = AccentSoft, onPrimaryContainer = Accent,
            secondary = InkSoft, onSecondary = AppBg, secondaryContainer = Panel2,
            onSecondaryContainer = Ink, outline = Outline, outlineVariant = Outline,
            error = Danger, onError = AppBg, errorContainer = DangerSoft, onErrorContainer = Danger,
            surfaceContainer = Panel, surfaceContainerHigh = Panel2, surfaceContainerHighest = Panel2
        ),
        typography = Typography(
            displaySmall = type(38, 44, FontWeight.SemiBold),
            headlineLarge = type(32, 38, FontWeight.SemiBold),
            headlineMedium = type(28, 34, FontWeight.SemiBold),
            headlineSmall = type(24, 30, FontWeight.SemiBold),
            titleLarge = type(21, 28, FontWeight.SemiBold),
            titleMedium = type(17, 24, FontWeight.Medium),
            titleSmall = type(15, 22, FontWeight.Medium),
            bodyLarge = type(16, 24), bodyMedium = type(14, 21), bodySmall = type(12, 18),
            labelLarge = type(15, 20, FontWeight.SemiBold),
            labelMedium = type(12, 16, FontWeight.Medium), labelSmall = type(11, 16, FontWeight.Medium)
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = CubeDesign.SmallShape,
            medium = CubeDesign.ControlShape, large = CubeDesign.PanelShape,
            extraLarge = RoundedCornerShape(32.dp)
        ), content = content
    )
}

enum class CubeIcon { Back, Scan, Cube, Play, Pause, Previous, Next, Check, Edit, Rotate, Undo, Redo, Close }

@Composable
fun AppIcon(icon: CubeIcon, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier.size(22.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            fun line(x: Float, y: Float, xx: Float, yy: Float) =
                drawLine(tint, Offset(x, y), Offset(xx, yy), 1.8f, StrokeCap.Round)
            fun path(vararg points: Float) {
                val p = Path().apply {
                    moveTo(points[0], points[1])
                    for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
                }
                drawPath(p, tint, style = Stroke(1.8f, cap = StrokeCap.Round))
            }
            when (icon) {
                CubeIcon.Back -> { path(10f, 5f, 3f, 12f, 10f, 19f); line(3f, 12f, 21f, 12f) }
                CubeIcon.Scan -> {
                    path(8f, 3f, 3f, 3f, 3f, 8f); path(16f, 3f, 21f, 3f, 21f, 8f)
                    path(3f, 16f, 3f, 21f, 8f, 21f); path(16f, 21f, 21f, 21f, 21f, 16f)
                    line(7f, 12f, 17f, 12f)
                }
                CubeIcon.Cube -> {
                    path(12f, 2f, 3f, 7f, 3f, 17f, 12f, 22f, 21f, 17f, 21f, 7f, 12f, 2f)
                    path(3f, 7f, 12f, 12f, 21f, 7f); line(12f, 12f, 12f, 22f)
                }
                CubeIcon.Play -> path(8f, 4f, 20f, 12f, 8f, 20f, 8f, 4f)
                CubeIcon.Pause -> { line(8f, 5f, 8f, 19f); line(16f, 5f, 16f, 19f) }
                CubeIcon.Previous -> { path(16f, 5f, 7f, 12f, 16f, 19f); line(5f, 5f, 5f, 19f) }
                CubeIcon.Next -> { path(8f, 5f, 17f, 12f, 8f, 19f); line(19f, 5f, 19f, 19f) }
                CubeIcon.Check -> path(4f, 12f, 10f, 18f, 21f, 6f)
                CubeIcon.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
                CubeIcon.Edit -> { path(4f, 16f, 16f, 4f, 20f, 8f, 8f, 20f, 4f, 20f, 4f, 16f); line(13f, 7f, 17f, 11f) }
                CubeIcon.Rotate -> { path(17f, 3f, 21f, 7f, 17f, 11f); path(21f, 7f, 10f, 7f, 5f, 11f, 5f, 17f, 10f, 21f, 17f, 21f) }
                CubeIcon.Undo -> { path(8f, 3f, 3f, 8f, 8f, 13f); path(3f, 8f, 14f, 8f, 20f, 13f, 20f, 18f) }
                CubeIcon.Redo -> { path(16f, 3f, 21f, 8f, 16f, 13f); path(21f, 8f, 10f, 8f, 4f, 13f, 4f, 18f) }
            }
        }
    }
}

@Composable
fun AppButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    primary: Boolean = true, enabled: Boolean = true, icon: CubeIcon? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) .975f else 1f, tween(CubeDesign.PressMillis), label = "buttonPress")
    Button(
        onClick = onClick, enabled = enabled, interactionSource = interaction,
        modifier = modifier.heightIn(min = 52.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        shape = CubeDesign.ControlShape,
        border = if (primary) null else BorderStroke(1.dp, Outline),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) Accent else Panel2,
            contentColor = if (primary) AppBg else Ink,
            disabledContainerColor = Panel2, disabledContentColor = Muted
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (icon != null) { AppIcon(icon); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun AppIconButton(icon: CubeIcon, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp).semantics { contentDescription = label }) {
        AppIcon(icon, tint = if (enabled) Ink else Muted)
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, action: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) { AppIconButton(CubeIcon.Back, "Back", onBack); Spacer(Modifier.width(4.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
        action()
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp), color = InkSoft)
}

@Composable
fun AppPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = CubeDesign.PanelShape, color = Panel, border = BorderStroke(1.dp, Outline)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun StatusNote(title: String, detail: String? = null, error: Boolean = false, success: Boolean = false) {
    val tone = if (error) Danger else if (success) Success else Accent
    Surface(color = if (error) DangerSoft else if (success) SuccessSoft else Panel2, shape = CubeDesign.ControlShape) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppIcon(if (success) CubeIcon.Check else if (error) CubeIcon.Edit else CubeIcon.Cube, tint = tone)
            Column(Modifier.weight(1f)) {
                Text(title, color = tone, style = MaterialTheme.typography.titleSmall)
                if (!detail.isNullOrBlank()) Text(detail, color = InkSoft, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ChoiceBar(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(CubeDesign.ControlShape).background(Panel).padding(4.dp).selectableGroup()) {
        options.forEachIndexed { i, label ->
            val active = selected == i
            val color by animateColorAsState(if (active) Panel2 else Panel, tween(CubeDesign.ChangeMillis), label = "selection")
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(CubeDesign.SmallShape).background(color)
                    .selectable(active, role = Role.Tab, onClick = { onSelect(i) }).padding(horizontal = 6.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text(label, color = if (active) Accent else InkSoft, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
fun ScanSteps(index: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().semantics { contentDescription = "Face ${index + 1} of 6" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(6) { step ->
            val color by animateColorAsState(if (step <= index) Accent else Outline, tween(CubeDesign.ChangeMillis), label = "scanStep")
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

@Composable
fun ColorPalette(selected: StickerGuess?, onSelect: (StickerGuess) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        canonicalStickerGuesses.forEach { guess ->
            val color = Color(idealRgbForGuess(guess).argb())
            val active = selected == guess
            val ink = if (color.luminance() > .45f) Color.Black else Color.White
            Surface(
                Modifier.weight(1f).heightIn(min = 52.dp).semantics { contentDescription = "${guess.displayName.lowercase()} paint" }
                    .selectable(active, role = Role.RadioButton, onClick = { onSelect(guess) }),
                color = if (active) Ink else Panel2, shape = CubeDesign.SmallShape
            ) {
                Box(Modifier.padding(4.dp).clip(RoundedCornerShape(9.dp)).background(color), contentAlignment = Alignment.Center) {
                    if (active) AppIcon(CubeIcon.Check, tint = ink)
                    else Text(guess.label, color = ink, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
