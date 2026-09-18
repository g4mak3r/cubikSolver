package com.cubecraft.solver.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import android.util.Size
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.scanner.FaceAnalyzer
import com.cubecraft.solver.scanner.FaceObservation
import com.cubecraft.solver.scanner.NormalizedPoint
import com.cubecraft.solver.scanner.ScanPose
import com.cubecraft.solver.scanner.StickerGuess
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import kotlin.math.max

@Composable
fun ScannerScreen(
    gridSize: Int,
    pose: ScanPose,
    index: Int,
    expectedCenter: StickerGuess?,
    capturedFaces: Map<Face, List<StickerGuess>>,
    onQuality: (Float) -> Unit,
    onCapture: (FaceObservation) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    var latest by remember { mutableStateOf<FaceObservation?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var pendingCapture by remember { mutableStateOf(false) }
    var acceptFramesAfter by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var introSettled by remember(index) { mutableStateOf(index != 0) }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(index) {
        if (index == 0) {
            introSettled = false
            delay(650L)
            introSettled = true
        } else {
            introSettled = true
        }
    }
    LaunchedEffect(index) {
        latest = null
        pendingCapture = false
        acceptFramesAfter = System.currentTimeMillis() + 280L
        onQuality(0f)
    }
    LaunchedEffect(latest) {
        onQuality(latest?.quality ?: 0f)
    }

    fun capture() {
        if (!granted || cameraError != null) return
        val observation = latest
        if (observation != null && System.currentTimeMillis() - observation.timestampMs <= 900L) {
            pendingCapture = false
            onCapture(observation)
        } else {
            pendingCapture = true
        }
    }

    Column(
        Modifier.fillMaxSize().background(AppBg).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("←", color = Ink, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(
                (index + 1).toString() + "/6",
                color = Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }

        Spacer(Modifier.height(6.dp))

        val guideHeight by animateDpAsState(
            targetValue = if (index == 0 && !introSettled) 190.dp else 112.dp,
            animationSpec = tween(650, easing = FastOutSlowInEasing),
            label = "scanGuideHeight"
        )
        val cameraAlpha by animateFloatAsState(
            targetValue = if (index == 0 && !introSettled) 0f else 1f,
            animationSpec = tween(420),
            label = "scanCameraAlpha"
        )

        ScanOrientationGuide(
            gridSize = gridSize,
            index = index,
            pose = pose,
            capturedFaces = capturedFaces,
            modifier = Modifier.fillMaxWidth().height(guideHeight)
        )

        val centerSticker = latest?.stickers?.getOrNull(gridSize * gridSize / 2)
        val liveCenter = centerSticker
            ?.takeIf { it.confidence >= .36f }
            ?.guess
        val targetMatched = expectedCenter != null && liveCenter == expectedCenter

        ScanGuidance(
            index = index,
            pose = pose,
            expectedCenter = expectedCenter,
            matched = targetMatched
        )

        Spacer(Modifier.height(10.dp))

        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .alpha(cameraAlpha)
                .clip(RoundedCornerShape(6.dp))
                .background(Viewport)
                .border(1.dp, Outline, RoundedCornerShape(6.dp))
        ) {
            if (granted) {
                CameraPreview(
                    gridSize = gridSize,
                    onObservation = { observation ->
                        if (observation != null && observation.timestampMs >= acceptFramesAfter) {
                            latest = observation
                            if (pendingCapture) {
                                pendingCapture = false
                                onCapture(observation)
                            }
                        }
                    },
                    onError = { cameraError = it }
                )
                ScanOverlay(gridSize, latest)
            } else {
                Button(
                    onClick = { launcher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(3.dp)
                ) {
                    Text("ALLOW CAMERA", fontFamily = FontFamily.Monospace)
                }
            }
        }

        if (cameraError != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                cameraError.orEmpty(),
                color = Danger,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                maxLines = 2
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = ::capture,
            enabled = granted && cameraError == null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(3.dp)
        ) {
            Text(
                if (pendingCapture) "HOLD" else "CAPTURE",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ScanGuidance(
    index: Int,
    pose: ScanPose,
    expectedCenter: StickerGuess?,
    matched: Boolean
) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            pose.title,
            color = Ink,
            fontFamily = FontFamily.Monospace,
            fontSize = if (index == 0) 13.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = .7.sp
        )

        if (expectedCenter != null) {
            Spacer(Modifier.height(5.dp))
            val rgb = idealRgbForGuess(expectedCenter)
            val color = Color(rgb.argb())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(12.dp)
                        .background(color, RoundedCornerShape(2.dp))
                        .border(
                            if (matched) 2.dp else 1.dp,
                            if (matched) Accent else Outline,
                            RoundedCornerShape(2.dp)
                        )
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    expectedCenter.displayName,
                    color = if (matched) Accent else InkSoft,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CameraPreview(
    gridSize: Int,
    onObservation: (FaceObservation?) -> Unit,
    onError: (String?) -> Unit
) {
    val lifecycle = LocalLifecycleOwner.current
    val executor = remember(gridSize) { Executors.newSingleThreadExecutor() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val observationCallback = rememberUpdatedState(onObservation)
    val errorCallback = rememberUpdatedState(onError)

    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                val view = this
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    try {
                        val cameraProvider = future.get()
                        provider = cameraProvider
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = view.surfaceProvider
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(640, 480))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(executor, FaceAnalyzer(gridSize) { observation ->
                            view.post { observationCallback.value(observation) }
                        })
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycle,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                        errorCallback.value(null)
                        view.postDelayed({
                            if (view.width > 0 && view.height > 0) {
                                val point = view.meteringPointFactory.createPoint(
                                    view.width / 2f,
                                    view.height / 2f
                                )
                                val action = FocusMeteringAction.Builder(
                                    point,
                                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                                ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                                camera.cameraControl.startFocusAndMetering(action)
                            }
                        }, 450)
                    } catch (error: Throwable) {
                        observationCallback.value(null)
                        errorCallback.value(error.message ?: "CAMERA ERROR")
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(Unit) {
        onDispose {
            provider?.unbindAll()
            executor.shutdownNow()
        }
    }
}

@Composable
private fun ScanOverlay(n: Int, observation: FaceObservation?) {
    Canvas(Modifier.fillMaxSize()) {
        val frameW = observation?.frameWidth?.takeIf { it > 0 }?.toFloat() ?: 1f
        val frameH = observation?.frameHeight?.takeIf { it > 0 }?.toFloat() ?: 1f
        val scale = max(size.width / frameW, size.height / frameH)
        val cropX = (size.width - frameW * scale) / 2f
        val cropY = (size.height - frameH * scale) / 2f

        fun map(point: NormalizedPoint) = Offset(
            cropX + point.x * frameW * scale,
            cropY + point.y * frameH * scale
        )

        val side = size.minDimension * .78f
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        val quad = listOf(
            Offset(left, top),
            Offset(left + side, top),
            Offset(left + side, top + side),
            Offset(left, top + side)
        )
        val path = Path().apply {
            moveTo(quad[0].x, quad[0].y)
            lineTo(quad[1].x, quad[1].y)
            lineTo(quad[2].x, quad[2].y)
            lineTo(quad[3].x, quad[3].y)
            close()
        }
        drawPath(path, Color.Black.copy(alpha = .14f))
        drawPath(path, Color.White.copy(alpha = .86f), style = Stroke(2.5f))

        for (i in 1 until n) {
            val t = i.toFloat() / n
            drawLine(
                Color.White.copy(alpha = .34f),
                lerp(quad[0], quad[1], t),
                lerp(quad[3], quad[2], t),
                1.4f
            )
            drawLine(
                Color.White.copy(alpha = .34f),
                lerp(quad[0], quad[3], t),
                lerp(quad[1], quad[2], t),
                1.4f
            )
        }

        val stickers = observation?.stickers.orEmpty()
        if (stickers.size == n * n) {
            for (row in 0 until n) for (col in 0 until n) {
                val idx = row * n + col
                val sticker = stickers[idx]
                val center = bilerp(quad, (col + .5f) / n, (row + .5f) / n)
                val radius = side / n * if (n == 3) .11f else .12f
                val display = idealRgbForGuess(sticker.guess)
                val color = Color(display.argb())

                drawCircle(Color.Black.copy(alpha = .54f), radius + 3f, center)
                drawCircle(color, radius, center)

                if (sticker.confidence < .45f) {
                    drawCircle(Danger, radius + 5f, center, style = Stroke(2.5f))
                }

                val luminance =
                    (.2126 * display.r + .7152 * display.g + .0722 * display.b) / 255.0
                val paint = AndroidPaint().apply {
                    isAntiAlias = true
                    this.color = if (luminance > .58) {
                        android.graphics.Color.BLACK
                    } else {
                        android.graphics.Color.WHITE
                    }
                    textAlign = AndroidPaint.Align.CENTER
                    textSize = (radius * 1.12f).coerceAtLeast(9f)
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                }
                drawContext.canvas.nativeCanvas.drawText(
                    sticker.guess.label,
                    center.x,
                    center.y - (paint.ascent() + paint.descent()) / 2f,
                    paint
                )
            }
        }

        if (observation?.tracked == true && observation.corners?.size == 4) {
            val tracked = observation.corners.map(::map)
            val trackedPath = Path().apply {
                moveTo(tracked[0].x, tracked[0].y)
                lineTo(tracked[1].x, tracked[1].y)
                lineTo(tracked[2].x, tracked[2].y)
                lineTo(tracked[3].x, tracked[3].y)
                close()
            }
            drawPath(trackedPath, Accent.copy(alpha = .72f), style = Stroke(2f))
        }
    }
}

private fun lerp(a: Offset, b: Offset, t: Float) =
    Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

private fun bilerp(quad: List<Offset>, u: Float, v: Float): Offset {
    val top = lerp(quad[0], quad[1], u)
    val bottom = lerp(quad[3], quad[2], u)
    return lerp(top, bottom, v)
}
