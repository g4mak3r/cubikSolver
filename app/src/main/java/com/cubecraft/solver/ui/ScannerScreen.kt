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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
    LaunchedEffect(index, granted) {
        if (index == 0 && granted) {
            introSettled = false
            delay(650L)
            introSettled = true
        } else {
            introSettled = index != 0
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

    ScannerLayout(
        gridSize, pose, index, expectedCenter, latest, introSettled, granted,
        pendingCapture, cameraError, ::capture, onBack,
        { launcher.launch(Manifest.permission.CAMERA) },
        capturedFaces = capturedFaces
    ) {
        CameraPreview(
            gridSize = gridSize,
            onObservation = { observation ->
                if (observation != null && observation.timestampMs >= acceptFramesAfter) {
                    latest = observation
                    if (pendingCapture) { pendingCapture = false; onCapture(observation) }
                }
            },
            onError = { cameraError = it }
        )
    }
}

/** Camera-independent chrome also used by instrumented layout checks. */
@Composable
internal fun ScannerLayout(
    gridSize: Int, pose: ScanPose, index: Int, expectedCenter: StickerGuess?, latest: FaceObservation?,
    introSettled: Boolean, granted: Boolean, pendingCapture: Boolean, cameraError: String?,
    onCapture: () -> Unit, onBack: () -> Unit, onPermission: () -> Unit,
    capturedFaces: Map<Face, List<StickerGuess>> = emptyMap(),
    camera: @Composable BoxScope.() -> Unit
) {
    val center = latest?.stickers?.getOrNull(gridSize * gridSize / 2)
    val matched = expectedCenter != null && center?.guess == expectedCenter && center.confidence >= .36f
    val guideHeight by animateDpAsState(if (granted && index == 0 && !introSettled) 172.dp else 112.dp, tween(600, easing = FastOutSlowInEasing), label = "guideDock")
    val cameraAlpha by animateFloatAsState(if (granted && index == 0 && !introSettled) 0f else 1f, tween(CubeDesign.ScreenMillis), label = "cameraReveal")
    Column(Modifier.fillMaxSize().padding(horizontal = CubeDesign.Gutter)) {
        ScreenHeader("Scan your cube", "Face ${index + 1} of 6 · $gridSize × $gridSize", onBack)
        ScanSteps(index, Modifier.padding(bottom = 16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            androidx.compose.material3.Surface(color = Panel, shape = CubeDesign.PanelShape) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ScanOrientationGuide(gridSize, index, pose, capturedFaces, Modifier.width(108.dp).height(guideHeight))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(pose.title.lowercase().replaceFirstChar { it.uppercase() }, color = Ink, style = MaterialTheme.typography.titleSmall)
                        if (expectedCenter != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(Color(idealRgbForGuess(expectedCenter).argb())))
                                Text("${expectedCenter.displayName.lowercase().replaceFirstChar { it.uppercase() }} center", color = if (matched) Success else InkSoft, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().aspectRatio(1f).alpha(cameraAlpha).clip(CubeDesign.PanelShape).background(Viewport).border(1.dp, Outline, CubeDesign.PanelShape)) {
                if (granted) {
                    camera()
                    ScanOverlay(gridSize, latest)
                } else {
                    Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        AppIcon(CubeIcon.Scan, Modifier.size(36.dp), Accent)
                        Text("Bring your cube into view", style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text("Camera access is used only to read the sticker colors.", color = InkSoft, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        AppButton("Allow camera", onPermission)
                    }
                }
            }
            if (cameraError != null) StatusNote("Camera unavailable", cameraError, error = true)
            else Text(if (pendingCapture) "Hold still. Waiting for a clear frame…" else "Keep one face inside the grid. You can correct its colors next.", color = InkSoft, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
        }
        AppButton(if (pendingCapture) "Hold still…" else "Capture face", onCapture,
            Modifier.fillMaxWidth().padding(vertical = 12.dp), enabled = granted && cameraError == null && !pendingCapture, icon = CubeIcon.Scan)
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
    val active = remember { java.util.concurrent.atomic.AtomicBoolean(true) }

    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                val view = this
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    if (!active.get()) return@addListener
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
                            view.post { if (active.get()) observationCallback.value(observation) }
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
            active.set(false)
            provider?.unbindAll()
            executor.shutdownNow()
        }
    }
}

@Composable
private fun ScanOverlay(n: Int, observation: FaceObservation?) {
    val paint = remember {
        AndroidPaint().apply {
            isAntiAlias = true
            textAlign = AndroidPaint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }
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
                paint.apply {
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
