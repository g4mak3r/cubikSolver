package com.cubecraft.solver.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt


class FaceAnalyzer(
    private val gridSize: Int,
    private val onResult: (FaceObservation?) -> Unit
) : ImageAnalysis.Analyzer {
    private var lastRun = 0L
    private var trackedCorners: Array<Point>? = null
    private var lastTrackSeenMs = 0L

    override fun analyze(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastRun < 90) return
            lastRun = now

            val rgba = yuv420ToRgba(image)
            val rotated = rotate(rgba, image.imageInfo.rotationDegrees)
            if (rotated !== rgba) rgba.release()

            val found = findFace(rotated)
            val observation = when {
                found != null -> {
                    val smooth = smoothCorners(found.first)
                    trackedCorners = smooth
                    lastTrackSeenMs = now


                    sampleGuideSquare(
                        rgba = rotated,
                        n = gridSize,
                        now = now,
                        trackedCorners = smooth,
                        contourQuality = found.second,
                        detected = true,
                        tracked = true
                    )
                }
                trackedCorners != null && now - lastTrackSeenMs <= TRACK_GRACE_MS -> {


                    val decay = (1f - (now - lastTrackSeenMs).toFloat() / TRACK_GRACE_MS).coerceIn(0f, 1f)
                    sampleGuideSquare(
                        rgba = rotated,
                        n = gridSize,
                        now = now,
                        trackedCorners = trackedCorners!!,
                        contourQuality = .30f + .25f * decay,
                        detected = false,
                        tracked = true
                    )
                }
                else -> {
                    trackedCorners = null
                    sampleGuideSquare(rotated, gridSize, now)
                }
            }
            rotated.release()
            onResult(observation)
        } catch (_: Throwable) {

            onResult(null)
        } finally {
            image.close()
        }
    }

    private fun findFace(rgba: Mat): Pair<Array<Point>, Float>? {
        val gray = Mat(); val blur = Mat(); val edges = Mat(); val hierarchy = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, blur, Size(5.0, 5.0), 0.0)
        Imgproc.Canny(blur, edges, 36.0, 132.0)
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        var best: Array<Point>? = null
        var bestScore = 0.0
        val frameArea = rgba.cols().toDouble() * rgba.rows().toDouble()
        val frameCx = rgba.cols() / 2.0
        val frameCy = rgba.rows() / 2.0
        val frameDiag = sqrt(frameCx * frameCx + frameCy * frameCy)
        val minSide = min(rgba.cols(), rgba.rows()).toDouble()
        val guideSide = minSide * GUIDE_FRACTION
        val guideArea = guideSide * guideSide

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)


            val areaVsGuide = area / max(1.0, guideArea)
            if (areaVsGuide < MIN_FACE_AREA_VS_GUIDE || areaVsGuide > MAX_FACE_AREA_VS_GUIDE) {
                contour.release(); continue
            }
            val c2 = MatOfPoint2f(*contour.toArray())
            val approx = MatOfPoint2f()
            val peri = Imgproc.arcLength(c2, true)
            Imgproc.approxPolyDP(c2, approx, peri * 0.032, true)
            val pts = approx.toArray()
            if (pts.size == 4) {
                val poly = MatOfPoint(*pts)
                val convex = Imgproc.isContourConvex(poly)
                poly.release()
                if (convex) {
                    val ordered = orderCorners(pts)
                    val sides = listOf(
                        dist(ordered[0], ordered[1]), dist(ordered[1], ordered[2]),
                        dist(ordered[2], ordered[3]), dist(ordered[3], ordered[0])
                    )
                    val minQuadSide = sides.minOrNull() ?: 0.0
                    val maxQuadSide = sides.maxOrNull() ?: 1.0
                    val squareness = minQuadSide / max(1.0, maxQuadSide)


                    if (maxQuadSide < guideSide * MIN_FACE_SIDE_VS_GUIDE || minQuadSide < guideSide * MIN_SHORT_SIDE_VS_GUIDE) {
                        c2.release(); approx.release(); contour.release(); continue
                    }
                    val cx = ordered.map { it.x }.average(); val cy = ordered.map { it.y }.average()
                    val centerDistance = sqrt((cx-frameCx)*(cx-frameCx)+(cy-frameCy)*(cy-frameCy))

                    if (centerDistance > minSide * MAX_CENTER_OFFSET_VS_MIN_SIDE) {
                        c2.release(); approx.release(); contour.release(); continue
                    }
                    val centerScore = 1.0 - (centerDistance / frameDiag).coerceIn(0.0,1.0)
                    val scaleScore = (1.0 - abs(areaVsGuide - TARGET_FACE_AREA_VS_GUIDE) / TARGET_FACE_AREA_VS_GUIDE).coerceIn(0.0, 1.0)
                    val score = squareness * .34 + scaleScore * .38 + centerScore * .28
                    if (score > bestScore) { bestScore = score; best = ordered }
                }
            }
            c2.release(); approx.release(); contour.release()
        }
        gray.release(); blur.release(); edges.release(); hierarchy.release()

        val corners = best ?: return null
        if (bestScore < .30) return null
        return corners to bestScore.toFloat().coerceIn(0f, 1f)
    }

    private fun smoothCorners(next: Array<Point>): Array<Point> {
        val prev = trackedCorners ?: return next.map { Point(it.x, it.y) }.toTypedArray()

        val keep = .68
        return Array(4) { i ->
            Point(
                prev[i].x * keep + next[i].x * (1.0 - keep),
                prev[i].y * keep + next[i].y * (1.0 - keep)
            )
        }
    }

    
    private fun sampleGuideSquare(
        rgba: Mat,
        n: Int,
        now: Long,
        trackedCorners: Array<Point>? = null,
        contourQuality: Float = 0f,
        detected: Boolean = false,
        tracked: Boolean = false
    ): FaceObservation {
        val minSide = min(rgba.cols(), rgba.rows())
        val side = (minSide * GUIDE_FRACTION).toInt().coerceAtLeast(n * 10)
        val x0 = ((rgba.cols() - side) / 2).coerceAtLeast(0)
        val y0 = ((rgba.rows() - side) / 2).coerceAtLeast(0)
        val roi = rgba.submat(y0, y0 + side, x0, x0 + side)
        val normalized = Mat()
        Imgproc.resize(roi, normalized, Size(540.0, 540.0), 0.0, 0.0, Imgproc.INTER_AREA)
        val batch = sampleSquare(normalized, n)
        val meanConfidence = if (batch.confidences.isNotEmpty()) {
            batch.confidences.average()
        } else {
            .42
        }
        val visualQuality = (meanConfidence * .82).toFloat().coerceIn(.12f, .82f)
        val quality = if (tracked) {
            (.22f * contourQuality + .78f * visualQuality).coerceIn(.12f, .92f)
        } else {
            visualQuality
        }
        roi.release(); normalized.release()
        return FaceObservation(
            samples = batch.samples,
            quality = quality,
            detected = detected,
            tracked = tracked,
            corners = trackedCorners?.map {
                NormalizedPoint(
                    (it.x / rgba.cols()).toFloat().coerceIn(0f, 1f),
                    (it.y / rgba.rows()).toFloat().coerceIn(0f, 1f)
                )
            },
            frameWidth = rgba.cols(),
            frameHeight = rgba.rows(),
            stickers = batch.live,
            timestampMs = now
        )
    }

    private data class SampleBatch(
        val samples: List<ColorSample>,
        val confidences: List<Float>,
        val live: List<LiveSticker>
    )

    private fun sampleSquare(square: Mat, n: Int): SampleBatch {
        val rgb = Mat()
        Imgproc.cvtColor(square, rgb, Imgproc.COLOR_RGBA2RGB)
        val cell = square.cols().toDouble() / n
        val samples = ArrayList<ColorSample>(n*n)
        val confidences = ArrayList<Float>(n*n)
        val live = ArrayList<LiveSticker>(n*n)

        for (r in 0 until n) for (c in 0 until n) {
            val margin = cell * .24
            val x0 = (c * cell + margin).toInt().coerceIn(0, rgb.cols()-2)
            val x1 = ((c + 1) * cell - margin).toInt().coerceIn(x0+1, rgb.cols())
            val y0 = (r * cell + margin).toInt().coerceIn(0, rgb.rows()-2)
            val y1 = ((r + 1) * cell - margin).toInt().coerceIn(y0+1, rgb.rows())
            val roiRgb = rgb.submat(y0, y1, x0, x1)
            val mean = MatOfDouble()
            val std = MatOfDouble()
            Core.meanStdDev(roiRgb, mean, std)
            val sd = std.toArray()
            val avgStd = if (sd.isNotEmpty()) sd.average() else 50.0
            mean.release()
            std.release()

            val rgbColor = robustRgb(roiRgb)
            val sample = colorSample(rgbColor)
            val uniform = (1.0 - avgStd / 72.0).coerceIn(0.0, 1.0)
            val lum = (rgbColor.r + rgbColor.g + rgbColor.b) / 3.0
            val exposure = (1.0 - abs(lum - 138.0) / 175.0).coerceIn(0.0, 1.0)
            val guess = provisionalGuess(rgbColor)
            val confidence = (.55 * uniform + .18 * exposure + .27 * guess.second).toFloat().coerceIn(.05f, 1f)

            samples += sample
            confidences += confidence
            live += LiveSticker(rgbColor, guess.first, confidence)
            roiRgb.release()
        }
        rgb.release()
        return SampleBatch(samples, confidences, live)
    }

    
    private fun provisionalGuess(rgb: RgbColor): Pair<StickerGuess, Double> =
        recognitionGuess(rgb)

    private fun robustRgb(roi: Mat): RgbColor {
        val small = Mat()
        Imgproc.resize(roi, small, Size(12.0, 12.0), 0.0, 0.0, Imgproc.INTER_AREA)
        val bytes = ByteArray((small.total() * small.channels()).toInt())
        small.get(0, 0, bytes)
        small.release()

        val pixels = bytes.size / 3
        val r = IntArray(pixels)
        val g = IntArray(pixels)
        val b = IntArray(pixels)

        var p = 0
        var i = 0
        while (i + 2 < bytes.size) {
            r[p] = bytes[i].toInt() and 0xFF
            g[p] = bytes[i + 1].toInt() and 0xFF
            b[p] = bytes[i + 2].toInt() and 0xFF
            p++
            i += 3
        }

        return RgbColor(trimmedMean(r), trimmedMean(g), trimmedMean(b))
    }

    private fun trimmedMean(values: IntArray): Int {
        values.sort()
        val from = values.size / 5
        val to = values.size - from
        var total = 0L
        for (i in from until to) total += values[i]
        return (total / (to - from).coerceAtLeast(1)).toInt()
    }

    private fun yuv420ToRgba(image: ImageProxy): Mat {
        val w = image.width; val h = image.height
        val y = image.planes[0]; val u = image.planes[1]; val v = image.planes[2]
        val nv21 = ByteArray(w * h + w * h / 2)
        copyPlane(y.buffer, y.rowStride, y.pixelStride, w, h, nv21, 0, 1)
        var offset = w * h
        val ub = u.buffer; val vb = v.buffer
        for (row in 0 until h / 2) for (col in 0 until w / 2) {
            nv21[offset++] = vb.get(row * v.rowStride + col * v.pixelStride)
            nv21[offset++] = ub.get(row * u.rowStride + col * u.pixelStride)
        }
        val yuv = Mat(h + h / 2, w, CvType.CV_8UC1)
        yuv.put(0, 0, nv21)
        val rgba = Mat()
        Imgproc.cvtColor(yuv, rgba, Imgproc.COLOR_YUV2RGBA_NV21)
        yuv.release()
        return rgba
    }

    private fun copyPlane(
        buffer: ByteBuffer, rowStride: Int, pixelStride: Int, w: Int, h: Int,
        out: ByteArray, start: Int, outPixelStride: Int
    ) {
        var dst = start
        for (r in 0 until h) for (c in 0 until w) {
            out[dst] = buffer.get(r * rowStride + c * pixelStride)
            dst += outPixelStride
        }
    }

    private fun rotate(src: Mat, degrees: Int): Mat {
        if (degrees % 360 == 0) return src
        val dst = Mat()
        when ((degrees % 360 + 360) % 360) {
            90 -> Core.rotate(src, dst, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(src, dst, Core.ROTATE_180)
            270 -> Core.rotate(src, dst, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> src.copyTo(dst)
        }
        return dst
    }

    private fun orderCorners(p: Array<Point>): Array<Point> {
        val tl = p.minBy { it.x + it.y }; val br = p.maxBy { it.x + it.y }
        val tr = p.maxBy { it.x - it.y }; val bl = p.minBy { it.x - it.y }
        return arrayOf(tl, tr, br, bl)
    }

    private fun dist(a: Point, b: Point) = sqrt((a.x-b.x)*(a.x-b.x) + (a.y-b.y)*(a.y-b.y))

    companion object {
        private const val TRACK_GRACE_MS = 420L
        private const val GUIDE_FRACTION = .78


        private const val MIN_FACE_AREA_VS_GUIDE = .26
        private const val MAX_FACE_AREA_VS_GUIDE = 1.45
        private const val TARGET_FACE_AREA_VS_GUIDE = .82
        private const val MIN_FACE_SIDE_VS_GUIDE = .56
        private const val MIN_SHORT_SIDE_VS_GUIDE = .40
        private const val MAX_CENTER_OFFSET_VS_MIN_SIDE = .24
    }
}
