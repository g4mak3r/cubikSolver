package com.cubecraft.solver.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.scanner.ScanPose
import com.cubecraft.solver.scanner.StickerGuess
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val BASE_YAW = -0.55f
private const val BASE_PITCH = 0.42f
private const val TOP_PITCH = 1.05f
private const val BOTTOM_PITCH = -1.05f

private data class GuideV3(val x: Float, val y: Float, val z: Float)
private data class GuideCam(val x: Float, val y: Float, val z: Float)
private data class GuidePoly(
    val points: List<Offset>,
    val depth: Float,
    val fill: Color,
    val stroke: Color,
    val strokeWidth: Float
)

@Composable
fun ScanOrientationGuide(
    gridSize: Int,
    index: Int,
    pose: ScanPose,
    capturedFaces: Map<Face, List<StickerGuess>>,
    modifier: Modifier = Modifier
) {
    val yaw = androidx.compose.runtime.remember(index) {
        Animatable(previousYaw(index))
    }
    val pitch = androidx.compose.runtime.remember(index) {
        Animatable(previousPitch(index))
    }

    LaunchedEffect(index) {
        while (true) {
            when (index) {
                0 -> {
                    yaw.snapTo(BASE_YAW)
                    pitch.snapTo(BASE_PITCH)
                    delay(2_800L)
                }

                1, 2, 3 -> {
                    yaw.snapTo(sideYaw(index - 1))
                    pitch.snapTo(BASE_PITCH)
                    delay(700L)
                    yaw.animateTo(
                        targetValue = sideYaw(index),
                        animationSpec = tween(1_150, easing = FastOutSlowInEasing)
                    )
                    delay(1_350L)
                    delay(350L)
                }

                4 -> {
                    yaw.snapTo(sideYaw(3))
                    pitch.snapTo(BASE_PITCH)
                    delay(700L)
                    yaw.animateTo(
                        targetValue = BASE_YAW,
                        animationSpec = tween(1_050, easing = FastOutSlowInEasing)
                    )
                    delay(220L)
                    pitch.animateTo(
                        targetValue = TOP_PITCH,
                        animationSpec = tween(900, easing = FastOutSlowInEasing)
                    )
                    delay(1_350L)
                    delay(350L)
                }

                5 -> {
                    yaw.snapTo(BASE_YAW)
                    pitch.snapTo(TOP_PITCH)
                    delay(700L)
                    pitch.animateTo(
                        targetValue = BASE_PITCH,
                        animationSpec = tween(650, easing = FastOutSlowInEasing)
                    )
                    delay(180L)
                    pitch.animateTo(
                        targetValue = BOTTOM_PITCH,
                        animationSpec = tween(950, easing = FastOutSlowInEasing)
                    )
                    delay(1_350L)
                    delay(350L)
                }
            }
        }
    }

    val pulse = rememberInfiniteTransition(label = "scanTargetPulse")
    val targetAlpha by pulse.animateFloat(
        initialValue = .48f,
        targetValue = .95f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanTargetAlpha"
    )

    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val base = size.minDimension * .74f

        fun camera(v: GuideV3): GuideCam {
            val cy = cos(yaw.value)
            val sy = sin(yaw.value)
            val x1 = v.x * cy + v.z * sy
            val z1 = -v.x * sy + v.z * cy

            val cp = cos(pitch.value)
            val sp = sin(pitch.value)
            val y2 = v.y * cp - z1 * sp
            val z2 = v.y * sp + z1 * cp
            return GuideCam(x1, y2, z2)
        }

        fun project(v: GuideV3): Offset {
            val c = camera(v)
            val focal = 2.4f
            val scale = focal / (focal - c.z)
            return Offset(
                center.x + c.x * base * scale,
                center.y - c.y * base * scale
            )
        }

        fun visible(face: Face): Boolean = camera(normal(face)).z > .015f

        val polys = mutableListOf<GuidePoly>()
        for (face in Face.entries) {
            if (!visible(face)) continue

            val shell = faceCorners(face, .505f).map(::project)
            val shellDepth = faceCorners(face, .505f)
                .map { camera(it).z }
                .average()
                .toFloat()
            polys += GuidePoly(
                points = shell,
                depth = shellDepth,
                fill = MetalDark.copy(alpha = .82f),
                stroke = if (face == pose.face) {
                    Accent.copy(alpha = targetAlpha)
                } else {
                    MetalLight.copy(alpha = .72f)
                },
                strokeWidth = if (face == pose.face) 2.8f else 1.2f
            )

            val guesses = capturedFaces[face]
            val cell = 1f / gridSize
            val inset = cell * .085f

            for (row in 0 until gridSize) {
                for (col in 0 until gridSize) {
                    val indexInFace = row * gridSize + col
                    val left = -.5f + col * cell + inset
                    val right = -.5f + (col + 1) * cell - inset
                    val top = .5f - row * cell - inset
                    val bottom = .5f - (row + 1) * cell + inset

                    val corners = listOf(
                        facePoint(face, left, top, .514f),
                        facePoint(face, right, top, .514f),
                        facePoint(face, right, bottom, .514f),
                        facePoint(face, left, bottom, .514f)
                    )
                    val points = corners.map(::project)
                    val depth = corners.map { camera(it).z }.average().toFloat()
                    val guess = guesses?.getOrNull(indexInFace)
                    val isKnown = guess != null && guess != StickerGuess.UNKNOWN
                    val fill = if (isKnown) {
                        Color(idealRgbForGuess(guess!!).argb()).copy(alpha = .96f)
                    } else {
                        MetalLight.copy(alpha = .30f)
                    }
                    val stroke = when {
                        face == pose.face -> Accent.copy(alpha = targetAlpha * .72f)
                        isKnown -> Color.White.copy(alpha = .20f)
                        else -> Color.White.copy(alpha = .12f)
                    }

                    polys += GuidePoly(
                        points = points,
                        depth = depth,
                        fill = fill,
                        stroke = stroke,
                        strokeWidth = if (face == pose.face) 1.4f else .7f
                    )
                }
            }
        }

        polys.sortedBy { it.depth }.forEach { poly ->
            val path = Path().apply {
                moveTo(poly.points[0].x, poly.points[0].y)
                poly.points.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(path, poly.fill)
            drawPath(path, poly.stroke, style = Stroke(poly.strokeWidth))
        }
    }
}

private fun sideYaw(index: Int): Float =
    BASE_YAW - index.coerceIn(0, 3) * (PI.toFloat() / 2f)

private fun previousYaw(index: Int): Float = when (index) {
    0 -> BASE_YAW
    in 1..3 -> sideYaw(index - 1)
    4 -> sideYaw(3)
    else -> BASE_YAW
}

private fun previousPitch(index: Int): Float = when (index) {
    5 -> TOP_PITCH
    else -> BASE_PITCH
}

private fun normal(face: Face): GuideV3 = when (face) {
    Face.F -> GuideV3(0f, 0f, 1f)
    Face.B -> GuideV3(0f, 0f, -1f)
    Face.R -> GuideV3(1f, 0f, 0f)
    Face.L -> GuideV3(-1f, 0f, 0f)
    Face.U -> GuideV3(0f, 1f, 0f)
    Face.D -> GuideV3(0f, -1f, 0f)
}

private fun faceCorners(face: Face, outward: Float): List<GuideV3> = listOf(
    facePoint(face, -.5f, .5f, outward),
    facePoint(face, .5f, .5f, outward),
    facePoint(face, .5f, -.5f, outward),
    facePoint(face, -.5f, -.5f, outward)
)

private fun facePoint(
    face: Face,
    horizontal: Float,
    vertical: Float,
    outward: Float
): GuideV3 = when (face) {
    Face.F -> GuideV3(horizontal, vertical, outward)
    Face.R -> GuideV3(outward, vertical, -horizontal)
    Face.B -> GuideV3(-horizontal, vertical, -outward)
    Face.L -> GuideV3(-outward, vertical, horizontal)
    Face.U -> GuideV3(horizontal, outward, -vertical)
    Face.D -> GuideV3(horizontal, -outward, vertical)
}
