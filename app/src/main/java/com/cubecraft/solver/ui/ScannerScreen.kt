package com.cubecraft.solver.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
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
import com.cubecraft.solver.scanner.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Continuous scanner UI. Capture is never gated by contour recognition.
 * Tapping CAPTURE either uses the latest fresh analysis frame or queues the very next one.
 */
@Composable
fun ScannerScreen(
    gridSize: Int,
    pose: ScanPose,
    index: Int,
    onQuality: (Float) -> Unit,
    onCapture: (FaceObservation) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    // IMPORTANT: this state is not keyed by pose. CameraPreview is intentionally long-lived.
    // We clear it explicitly on step changes, while its callback is updated via rememberUpdatedState.
    var latest by remember { mutableStateOf<FaceObservation?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var pendingCapture by remember { mutableStateOf(false) }
    var acceptFramesAfter by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    LaunchedEffect(index) {
        // Prevent a stale frame from the previous face being captured immediately after advancing.
        latest = null
        pendingCapture = false
        acceptFramesAfter = System.currentTimeMillis() + 280L
        onQuality(0f)
    }
    LaunchedEffect(latest) { onQuality(latest?.quality ?: 0f) }

    fun captureNowOrNextFrame() {
        if (!granted || cameraError != null) return
        val obs = latest
        val fresh = obs != null && System.currentTimeMillis() - obs.timestampMs <= 900L
        if (fresh) {
            pendingCapture = false
            onCapture(obs!!)
        } else {
            // No disabled button and no recognition lock: the next analyzer frame completes the tap.
            pendingCapture = true
        }
    }

    Column(Modifier.fillMaxSize().background(AppBg).padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("← HOME", color = Ink, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "FACE " + (index + 1).toString().padStart(2, '0') + " / 06",
                color = Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            pose.title.uppercase(),
            color = Ink,
            fontFamily = FontFamily.Monospace,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            pose.instruction,
            color = Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            maxLines = 2
        )

        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(7.dp))
                .background(Viewport).border(1.dp, InkSoft, RoundedCornerShape(7.dp))
        ) {
            if (granted) {
                CameraPreview(
                    gridSize = gridSize,
                    onObservation = { obs ->
                        if (obs != null && obs.timestampMs >= acceptFramesAfter) {
                            latest = obs
                            if (pendingCapture) {
                                pendingCapture = false
                                onCapture(obs)
                            }
                        }
                    },
                    onError = { cameraError = it }
                )
                ScanArOverlay(gridSize, latest)

                Text(
                    when {
                        pendingCapture -> "HOLD STILL / QUEUED"
                        latest?.tracked == true -> "FACE LOCK"
                        latest != null -> "GRID LIVE"
                        else -> "CAMERA INIT"
                    },
                    modifier = Modifier.align(Alignment.TopStart)
                        .padding(10.dp)
                        .background(CameraChrome, RoundedCornerShape(3.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Column(
                    Modifier.fillMaxSize().padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("CAMERA PERMISSION", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }, shape = RoundedCornerShape(4.dp)) {
                        Text("ALLOW", fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        val observation = latest
        val uncertain = observation?.uncertainCount ?: 0
        val statusColor = when {
            cameraError != null -> Danger
            observation?.tracked == true -> Success
            observation != null -> Accent
            else -> Muted
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(statusColor, RoundedCornerShape(1.dp)))
            Spacer(Modifier.width(7.dp))
            Text(
                when {
                    cameraError != null -> "CAMERA ERROR / " + cameraError
                    pendingCapture -> "WAITING FOR FRAME"
                    observation?.tracked == true -> "TRACKED"
                    observation != null -> "GRID READY"
                    else -> "STARTING"
                },
                color = if (cameraError != null) Danger else InkSoft,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                maxLines = 1
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Q " + ((observation?.quality ?: 0f) * 100).toInt() + "  /  ? " + uncertain,
                color = Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = { captureNowOrNextFrame() },
            enabled = granted && cameraError == null,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(5.dp)
        ) {
            Text(
                if (pendingCapture) "CAPTURE QUEUED…" else "CAPTURE →",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(12.dp))
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

    // This is the critical multi-step fix. AndroidView.factory runs only once, so directly
    // capturing the first lambda would keep writing into ScannerScreen's FACE 1 state forever.
    val observationCallback = rememberUpdatedState(onObservation)
    val errorCallback = rememberUpdatedState(onError)

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                val view = this
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    try {
                        val p = future.get(); provider = p
                        val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                        val analysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(640, 480))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(executor, FaceAnalyzer(gridSize) { obs ->
                            view.post { observationCallback.value(obs) }
                        })
                        p.unbindAll()
                        val camera = p.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                        errorCallback.value(null)

                        // Center metering is helpful on older Huawei camera stacks but is not a capture gate.
                        view.postDelayed({
                            if (view.width > 0 && view.height > 0) {
                                val point = view.meteringPointFactory.createPoint(view.width / 2f, view.height / 2f)
                                val action = FocusMeteringAction.Builder(
                                    point,
                                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                                ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                                camera.cameraControl.startFocusAndMetering(action)
                            }
                        }, 450)
                    } catch (t: Throwable) {
                        observationCallback.value(null)
                        errorCallback.value(t.message ?: "Unable to open the rear camera.")
                    }
                }, ContextCompat.getMainExecutor(ctx))
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
private fun ScanArOverlay(n: Int, observation: FaceObservation?) {
    Canvas(Modifier.fillMaxSize()) {
        val frameW = observation?.frameWidth?.takeIf { it > 0 }?.toFloat() ?: 1f
        val frameH = observation?.frameHeight?.takeIf { it > 0 }?.toFloat() ?: 1f
        val scale = max(size.width / frameW, size.height / frameH)
        val cropX = (size.width - frameW * scale) / 2f
        val cropY = (size.height - frameH * scale) / 2f

        fun map(p: NormalizedPoint): Offset = Offset(
            cropX + p.x * frameW * scale,
            cropY + p.y * frameH * scale
        )

        // SOURCE OF TRUTH: this fixed square is always the actual capture/sample region.
        // AR tracking may decorate the preview, but it is never allowed to resize this grid.
        val guideSide = size.minDimension * .78f
        val guideLeft = (size.width - guideSide) / 2f
        val guideTop = (size.height - guideSide) / 2f
        val guide = listOf(
            Offset(guideLeft, guideTop), Offset(guideLeft + guideSide, guideTop),
            Offset(guideLeft + guideSide, guideTop + guideSide), Offset(guideLeft, guideTop + guideSide)
        )
        val guideColor = Color.White.copy(alpha = .94f)
        val guidePath = Path().apply {
            moveTo(guide[0].x, guide[0].y)
            lineTo(guide[1].x, guide[1].y)
            lineTo(guide[2].x, guide[2].y)
            lineTo(guide[3].x, guide[3].y)
            close()
        }
        drawPath(guidePath, Color.Black.copy(alpha = .12f))
        drawPath(guidePath, guideColor, style = Stroke(3.5f))

        // Fixed perspective-neutral NxN grid used for BOTH live colors and final Capture.
        for (i in 1 until n) {
            val t = i.toFloat() / n
            val a = lerpOffset(guide[0], guide[1], t)
            val b = lerpOffset(guide[3], guide[2], t)
            drawLine(guideColor.copy(alpha = .48f), a, b, 2f)
            val c = lerpOffset(guide[0], guide[3], t)
            val d = lerpOffset(guide[1], guide[2], t)
            drawLine(guideColor.copy(alpha = .48f), c, d, 2f)
        }

        // Live sticker colors are deliberately placed on the fixed guide, because those are
        // the exact cells sampled by FaceAnalyzer. This prevents visual/capture disagreement.
        val stickers = observation?.stickers.orEmpty()
        if (stickers.size == n * n) {
            for (r in 0 until n) for (c in 0 until n) {
                val idx = r * n + c
                val sticker = stickers[idx]
                val u0 = c.toFloat() / n; val u1 = (c + 1f) / n
                val v0 = r.toFloat() / n; val v1 = (r + 1f) / n
                val p00 = bilerp(guide, u0, v0); val p10 = bilerp(guide, u1, v0)
                val p11 = bilerp(guide, u1, v1); val p01 = bilerp(guide, u0, v1)

                if (sticker.confidence < .56f) {
                    val warn = if (sticker.confidence < .38f) Color(0x66FF4D4F) else Color(0x55FFB020)
                    val cellPath = Path().apply {
                        moveTo(p00.x,p00.y); lineTo(p10.x,p10.y); lineTo(p11.x,p11.y); lineTo(p01.x,p01.y); close()
                    }
                    drawPath(cellPath, warn)
                }

                val center = bilerp(guide, (c + .5f) / n, (r + .5f) / n)
                val cellWidth = (offsetDistance(p10, p00) + offsetDistance(p11, p01)) * .5f
                val cellHeight = (offsetDistance(p01, p00) + offsetDistance(p11, p10)) * .5f
                val radius = minOf(cellWidth, cellHeight) * if (n == 3) .16f else .19f
                val chipColor = Color(sticker.rgb.argb())
                drawCircle(Color.Black.copy(alpha = .42f), radius + 3.5f, center)
                drawCircle(chipColor.copy(alpha = .94f), radius, center)
                if (sticker.confidence < .56f) {
                    drawCircle(
                        if (sticker.confidence < .38f) Color(0xFFFF5C5C) else Color(0xFFFFC247),
                        radius + 5f,
                        center,
                        style = Stroke(3f)
                    )
                }

                val luminance = (.2126 * sticker.rgb.r + .7152 * sticker.rgb.g + .0722 * sticker.rgb.b) / 255.0
                val paint = AndroidPaint().apply {
                    isAntiAlias = true
                    color = if (luminance > .58) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                    textAlign = AndroidPaint.Align.CENTER
                    textSize = (radius * 1.18f).coerceAtLeast(10f)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                drawContext.canvas.nativeCanvas.drawText(
                    sticker.guess.label,
                    center.x,
                    center.y - (paint.ascent() + paint.descent()) / 2f,
                    paint
                )
            }
        }

        // OPTIONAL AR ASSIST: only a macro-sized contour can reach FaceObservation.tracked.
        // We draw an outer teal outline only. It never owns the NxN grid or Capture samples.
        if (observation?.tracked == true && observation.corners?.size == 4) {
            val face = observation.corners.map(::map)
            val arColor = Color(0xFF63E6BE)
            val arPath = Path().apply {
                moveTo(face[0].x, face[0].y)
                lineTo(face[1].x, face[1].y)
                lineTo(face[2].x, face[2].y)
                lineTo(face[3].x, face[3].y)
                close()
            }
            drawPath(arPath, arColor.copy(alpha = .14f))
            drawPath(arPath, arColor, style = Stroke(5f))

            val bracket = size.minDimension * .045f
            face.forEachIndexed { i, p ->
                val towardA = when (i) { 0, 3 -> 1f; else -> -1f }
                val towardB = when (i) { 0, 1 -> 1f; else -> -1f }
                drawLine(arColor, p, Offset(p.x + bracket * towardA, p.y), 6f)
                drawLine(arColor, p, Offset(p.x, p.y + bracket * towardB), 6f)
            }
        }
    }
}

private fun offsetDistance(a: Offset, b: Offset): Float = kotlin.math.hypot(a.x - b.x, a.y - b.y)

private fun lerpOffset(a: Offset, b: Offset, t: Float) = Offset(
    a.x + (b.x - a.x) * t,
    a.y + (b.y - a.y) * t
)

/** quad order: TL, TR, BR, BL */
private fun bilerp(q: List<Offset>, u: Float, v: Float): Offset {
    val top = lerpOffset(q[0], q[1], u)
    val bottom = lerpOffset(q[3], q[2], u)
    return lerpOffset(top, bottom, v)
}
