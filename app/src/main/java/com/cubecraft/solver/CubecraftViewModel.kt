package com.cubecraft.solver

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cubecraft.solver.model.*
import com.cubecraft.solver.scanner.*
import com.cubecraft.solver.solver.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen { HOME, SCAN, FACE_CONFIRM, REVIEW, STUDIO }

class CubecraftViewModel : ViewModel() {
    var screen by mutableStateOf(AppScreen.HOME); private set
    var cubeSize by mutableIntStateOf(3); private set
    var cube by mutableStateOf(CubeState(3)); private set
    var revision by mutableIntStateOf(0); private set
    var palette by mutableStateOf<Map<Face,RgbColor>>(emptyMap()); private set
    var scanIndex by mutableIntStateOf(0); private set
    var scanQuality by mutableFloatStateOf(0f); private set
    var frontCenterGuess by mutableStateOf<StickerGuess?>(null); private set
    var frontCenterRgb by mutableStateOf<RgbColor?>(null); private set
    var pendingFaceObservation by mutableStateOf<FaceObservation?>(null); private set
    var pendingFaceOverrides by mutableStateOf<Map<Int,StickerGuess>>(emptyMap()); private set
    var reviewFaces by mutableStateOf<Map<Face,List<Face>>?>(null); private set
    var validation by mutableStateOf<ValidationReport?>(null); private set
    var message by mutableStateOf<String?>(null); private set
    var solving by mutableStateOf(false); private set
    var solution by mutableStateOf<List<Move>>(emptyList()); private set
    /** Number of solution moves currently replayed in the digital preview. */
    var solutionIndex by mutableIntStateOf(0); private set

    private val captures = mutableListOf<CapturedFace>()
    private val history = mutableListOf<Move>()
    private val redo = mutableListOf<Move>()
    private var baseline: Map<Face,List<Face>>? = null
    /** Exact cube state from which the currently displayed solution was calculated. */
    private var solutionStart: Map<Face,List<Face>>? = null
    private var originSolved = true
    private var solveRequestId = 0L
    private var validationRequestId = 0L
    private val three = Min2PhaseSolver()
    private val five = FiveByFiveSolver()
    private val validator = CubeValidator(three)

    val currentPose: ScanPose get() {
        val base = scanSequence[scanIndex.coerceIn(0,5)]
        val label = frontCenterGuess?.label?.takeIf { it != "?" } ?: "saved FRONT"
        return base.copy(instruction = base.instruction.replace("{FRONT_CENTER}", label))
    }
    /** The next physical turn the user should perform at the current slider position. */
    val currentGuideMove: Move? get() = solution.getOrNull(solutionIndex)
    val moveHistory: List<Move> get() = history.toList()
    val hasBaseline: Boolean get() = baseline != null

    fun home() {
        pendingFaceObservation = null
        pendingFaceOverrides = emptyMap()
        screen = AppScreen.HOME
        message = null
    }

    fun openVirtual(size: Int) {
        cubeSize = size
        cube = CubeState(size)
        revision++
        palette = emptyMap()
        baseline = null
        pendingFaceObservation = null
        pendingFaceOverrides = emptyMap()
        originSolved = true
        history.clear()
        redo.clear()
        clearSolution()
        screen = AppScreen.STUDIO
        message = null
    }

    fun beginScan(size: Int) {
        cubeSize = size
        captures.clear()
        scanIndex = 0
        scanQuality = 0f
        frontCenterGuess = null
        frontCenterRgb = null
        pendingFaceObservation = null
        pendingFaceOverrides = emptyMap()
        reviewFaces = null
        validation = null
        validationRequestId++
        clearSolution()
        message = null
        screen = AppScreen.SCAN
    }

    fun updateScanQuality(q: Float) { scanQuality = q }

    /**
     * Capture never advances directly anymore. Every face first stops on a confirmation screen
     * showing exactly what the camera sampled. Only confirmCurrentFace() commits the face and moves on.
     */
    fun captureFace(observation: FaceObservation) {
        if (observation.samples.size != cubeSize * cubeSize) return
        if (screen != AppScreen.SCAN) return
        pendingFaceObservation = observation
        pendingFaceOverrides = emptyMap()
        scanQuality = observation.quality
        message = null
        screen = AppScreen.FACE_CONFIRM
    }

    fun setPendingFaceColor(index: Int, guess: StickerGuess) {
        if (screen != AppScreen.FACE_CONFIRM) return
        if (guess == StickerGuess.UNKNOWN) return
        if (index !in 0 until cubeSize * cubeSize) return
        pendingFaceOverrides = pendingFaceOverrides.toMutableMap().apply { put(index, guess) }
    }

    fun clearPendingFaceColor(index: Int) {
        if (index !in pendingFaceOverrides) return
        pendingFaceOverrides = pendingFaceOverrides.toMutableMap().apply { remove(index) }
    }

    fun confirmCurrentFace() {
        val observation = pendingFaceObservation ?: return
        if (observation.samples.size != cubeSize * cubeSize) return
        val pose = currentPose
        val center = cubeSize * cubeSize / 2
        val rawGuesses = List(cubeSize * cubeSize) { idx ->
            observation.stickers.getOrNull(idx)?.guess ?: StickerGuess.UNKNOWN
        }
        val effectiveCenterGuess = pendingFaceOverrides[center] ?: rawGuesses[center]

        if (scanIndex == 0) {
            frontCenterGuess = effectiveCenterGuess
            frontCenterRgb = observation.samples.getOrNull(center)?.rgb
        }

        captures.removeAll { it.face == pose.face }
        captures += CapturedFace(
            face = pose.face,
            samples = observation.samples,
            quality = observation.quality,
            guesses = rawGuesses,
            manualGuesses = pendingFaceOverrides
        )
        scanQuality = 0f

        if (scanIndex < 5) {
            pendingFaceObservation = null
            pendingFaceOverrides = emptyMap()
            scanIndex++
            screen = AppScreen.SCAN
        } else {
            // Keep the sixth observation alive until finalization succeeds. If anything goes wrong,
            // the user remains on its confirmation screen and can edit/rescan instead of crashing.
            if (finishClassification()) {
                pendingFaceObservation = null
                pendingFaceOverrides = emptyMap()
            }
        }
    }

    /** Throw away only the just-captured frame and return to the same face. */
    fun rescanCurrentFace() {
        pendingFaceObservation = null
        pendingFaceOverrides = emptyMap()
        scanQuality = 0f
        message = null
        screen = AppScreen.SCAN
    }

    fun restartScan() { beginScan(cubeSize) }

    /**
     * Face six is a hard transition point. Every operation here is guarded: malformed classifier
     * output or cube construction must stay recoverable in the UI instead of terminating Android.
     */
    private fun finishClassification(): Boolean {
        var usedFallback = false

        val classified = try {
            BalancedClassifier.classify(captures, cubeSize)
        } catch (balancedError: Throwable) {
            usedFallback = true
            try {
                BalancedClassifier.classifyNearest(captures, cubeSize)
            } catch (fallbackError: Throwable) {
                message = "Could not classify the cube: ${fallbackError.message ?: balancedError.message ?: "unknown error"}. Edit or rescan this face."
                screen = AppScreen.FACE_CONFIRM
                return false
            }
        }

        val orientationResult = if (cubeSize == 3) {
            try { ThreeByThreeOrientationResolver.resolve(classified.faces) }
            catch (_: Throwable) { null }
        } else null
        val resolvedFaces = orientationResult?.faces ?: classified.faces

        val builtCube = try {
            require(Face.entries.all { resolvedFaces[it]?.size == cubeSize * cubeSize }) {
                "Classifier returned an incomplete cube"
            }
            CubeState(cubeSize).also { it.loadFaces(resolvedFaces) }
        } catch (t: Throwable) {
            message = "Could not build the cube: ${t.message ?: t.javaClass.simpleName}. Edit or rescan this face."
            screen = AppScreen.FACE_CONFIRM
            return false
        }

        reviewFaces = resolvedFaces
        palette = classified.palette
        cube = builtCube
        revision++
        validation = null
        message = when {
            usedFallback -> "Six faces captured. Review the fallback color map before continuing."
            orientationResult?.verified == true && orientationResult.changed ->
                "Six faces captured · scan orientation auto-corrected · validating…"
            else -> "Six faces captured · validating cube state…"
        }
        screen = AppScreen.REVIEW
        validateReviewAsync()
        return true
    }

    fun rotateReviewFace(face: Face) {
        val all = reviewFaces ?: return
        val src = all.getValue(face)
        val n = cubeSize
        val rotated = List(n*n) { idx ->
            val r = idx / n
            val c = idx % n
            src[(n - 1 - c) * n + r]
        }
        reviewFaces = all.toMutableMap().apply { put(face, rotated) }
        refreshReviewValidation()
    }

    fun cycleReviewSticker(face: Face, index: Int) {
        val n = cubeSize
        if (index == n*n/2) return
        val all = reviewFaces ?: return
        val list = all.getValue(face).toMutableList()
        val cur = list[index]
        list[index] = Face.entries[(cur.ordinal + 1) % Face.entries.size]
        reviewFaces = all.toMutableMap().apply { put(face, list) }
        refreshReviewValidation()
    }

    private fun refreshReviewValidation() {
        reviewFaces?.let {
            try {
                cube = CubeState(cubeSize).also { c -> c.loadFaces(it) }
                revision++
                validation = null
                message = "Validating edited scan…"
                validateReviewAsync()
            } catch (t: Throwable) {
                validation = ValidationReport(false, listOf("Could not rebuild edited cube: ${t.message ?: t.javaClass.simpleName}"))
                message = "The edited cube state is incomplete."
            }
        }
    }

    private fun validateReviewAsync() {
        val state = try {
            cube.deepCopy()
        } catch (t: Throwable) {
            validation = ValidationReport(false, listOf("Could not copy cube for validation: ${t.message ?: t.javaClass.simpleName}"))
            message = "Cube validation could not start."
            return
        }
        val requestId = ++validationRequestId
        viewModelScope.launch {
            val report = withContext(Dispatchers.Default) {
                try {
                    validator.validate(state)
                } catch (t: Throwable) {
                    ValidationReport(false, listOf("Validation error: ${t.message ?: t.javaClass.simpleName}"))
                }
            }
            if (requestId != validationRequestId) return@launch
            validation = report
            message = if (report.ok) "Scan state verified." else "Check the scan before continuing."
        }
    }

    fun acceptReview() {
        val report = validation ?: return
        if (!report.ok) {
            message = "Fix the highlighted scan/orientation problem first."
            return
        }
        try {
            baseline = cube.snapshot()
            originSolved = false
            history.clear()
            redo.clear()
            clearSolution()
            screen = AppScreen.STUDIO
            message = if (cubeSize == 3) "Analyzing a short verified 3x3 solution…" else null
            if (cubeSize == 3) solve()
        } catch (t: Throwable) {
            screen = AppScreen.REVIEW
            message = "Could not open the 3D cube: ${t.message ?: t.javaClass.simpleName}"
        }
    }

    fun applyMove(move: Move) {
        clearSolution()
        cube.apply(move)
        history += move
        redo.clear()
        revision++
    }

    fun undo() {
        clearSolution()
        val m = history.removeLastOrNull() ?: return
        cube.apply(m.inverse())
        redo += m
        revision++
    }

    fun redo() {
        clearSolution()
        val m = redo.removeLastOrNull() ?: return
        cube.apply(m)
        history += m
        revision++
    }

    fun returnToScan() {
        val b = baseline ?: return
        cube.loadFaces(b)
        history.clear()
        redo.clear()
        clearSolution()
        revision++
    }

    fun resetSolved() {
        cube.resetSolved()
        history.clear()
        redo.clear()
        clearSolution()
        originSolved = true
        baseline = null
        palette = emptyMap()
        revision++
    }

    fun paintSticker(key: StickerKey, color: Face) {
        if (cube.isFixedCenter(key)) {
            message = "The fixed center defines this scanned color and cannot be repainted."
            return
        }
        if (baseline != null && history.isNotEmpty()) {
            message = "Return to the scanned state before editing sticker colors."
            return
        }
        if (solution.isNotEmpty() && solutionIndex != 0) {
            message = "Move the solution slider back to START before editing sticker colors."
            return
        }
        if (!cube.setStickerColor(key, color)) return
        history.clear()
        redo.clear()
        clearSolution()
        revision++
        if (baseline != null) baseline = cube.snapshot()
        val target = cubeSize * cubeSize
        val bad = cube.colorCounts().filterValues { it != target }
        message = if (bad.isEmpty()) {
            "Color edit saved. Tap ANALYZE to rebuild the solution."
        } else {
            "Color edit saved. Keep exactly $target stickers of each color before solving."
        }
    }

    fun solve() {
        if (solving) return

        val state = try {
            cube.deepCopy()
        } catch (t: Throwable) {
            message = "Could not copy cube for solving: ${t.message ?: t.javaClass.simpleName}"
            return
        }
        val start = state.snapshot()
        val h = history.toList()
        val wasSolvedOrigin = originSolved
        val requestId = ++solveRequestId

        solution = emptyList()
        solutionIndex = 0
        solutionStart = null
        solving = true
        message = if (state.size == 3) "Analyzing a short verified 3x3 solution…" else null

        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                try {
                    if (state.size == 3) three.solve(state)
                    else if (wasSolvedOrigin) five.solveKnownHistory(state, h) else five.solve(state)
                } catch (t: Throwable) {
                    SolverResult.Invalid("Solver error: ${t.message ?: t.javaClass.simpleName}")
                }
            }

            if (requestId != solveRequestId) return@launch

            solving = false
            when (result) {
                is SolverResult.Success -> {
                    solution = result.moves
                    solutionStart = start
                    solutionIndex = 0
                    history.clear()
                    redo.clear()
                    message = if (result.moves.isEmpty()) {
                        "Cube is already solved."
                    } else {
                        "Verified solution: ${result.moves.size} moves"
                    }
                }
                is SolverResult.Invalid -> message = result.reason
                is SolverResult.Unavailable -> message = result.reason
            }
        }
    }

    /**
     * Timeline semantics: index == number of already replayed moves.
     * 0 = the exact scanned state and instruction #1 is highlighted.
     * N = solved preview after all N solution moves.
     */
    fun solutionSeek(appliedMoves: Int) {
        val start = solutionStart ?: return
        if (solution.isEmpty()) return
        val target = appliedMoves.coerceIn(0, solution.size)
        if (target == solutionIndex) return

        cube.loadFaces(start)
        for (i in 0 until target) cube.apply(solution[i])
        solutionIndex = target
        history.clear()
        redo.clear()
        revision++

        message = if (target == solution.size) {
            if (cube.isSolved()) "Solved preview - all ${solution.size} moves applied." else "Replay mismatch - reanalyze the scan."
        } else {
            "Step ${target + 1} of ${solution.size}"
        }
    }

    fun solutionNext() = solutionSeek(solutionIndex + 1)
    fun solutionPrevious() = solutionSeek(solutionIndex - 1)

    fun clearMessage() { message = null }

    private fun clearSolution() {
        solveRequestId++
        solving = false
        solution = emptyList()
        solutionIndex = 0
        solutionStart = null
    }
}
