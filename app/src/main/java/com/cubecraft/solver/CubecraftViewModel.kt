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

enum class AppScreen { HOME, SCAN, REVIEW, STUDIO }

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
    var reviewFaces by mutableStateOf<Map<Face,List<Face>>?>(null); private set
    var validation by mutableStateOf<ValidationReport?>(null); private set
    var message by mutableStateOf<String?>(null); private set
    var solving by mutableStateOf(false); private set
    var solution by mutableStateOf<List<Move>>(emptyList()); private set
    var solutionIndex by mutableIntStateOf(0); private set

    private val captures=mutableListOf<CapturedFace>()
    private val history=mutableListOf<Move>()
    private val redo=mutableListOf<Move>()
    private var baseline: Map<Face,List<Face>>? = null
    private var originSolved=true
    private val three=Min2PhaseSolver(); private val five=FiveByFiveSolver(); private val validator=CubeValidator(three)

    val currentPose: ScanPose get() {
        val base = scanSequence[scanIndex.coerceIn(0,5)]
        val label = frontCenterGuess?.label?.takeIf { it != "?" } ?: "saved FRONT"
        return base.copy(instruction = base.instruction.replace("{FRONT_CENTER}", label))
    }
    val currentGuideMove: Move? get() = solution.getOrNull(solutionIndex)
    val moveHistory: List<Move> get() = history.toList()
    val hasBaseline: Boolean get() = baseline != null

    fun home() { screen=AppScreen.HOME; message=null }
    fun openVirtual(size:Int) {
        cubeSize=size; cube=CubeState(size); revision++; palette=emptyMap(); baseline=null; originSolved=true
        history.clear(); redo.clear(); clearSolution(); screen=AppScreen.STUDIO; message=null
    }
    fun beginScan(size:Int) {
        cubeSize=size; captures.clear(); scanIndex=0; scanQuality=0f; frontCenterGuess=null; frontCenterRgb=null; reviewFaces=null; validation=null
        clearSolution(); message=null; screen=AppScreen.SCAN
    }
    fun updateScanQuality(q:Float){ scanQuality=q }
    fun captureFace(observation:FaceObservation) {
        if(observation.samples.size != cubeSize*cubeSize) return
        if (scanIndex == 0) {
            val center = cubeSize * cubeSize / 2
            frontCenterGuess = observation.stickers.getOrNull(center)?.guess
            frontCenterRgb = observation.samples.getOrNull(center)?.rgb
        }
        captures.removeAll { it.face==currentPose.face }
        captures += CapturedFace(currentPose.face, observation.samples, observation.quality)
        if(scanIndex<5) { scanIndex++; scanQuality=0f } else finishClassification()
    }
    fun restartScan(){ beginScan(cubeSize) }

    private fun finishClassification() {
        try {
            val classified=BalancedClassifier.classify(captures,cubeSize)
            reviewFaces=classified.faces; palette=classified.palette
            cube=CubeState(cubeSize).also { it.loadFaces(classified.faces) }; revision++
            validation=validator.validate(cube)
            message="Mean center distance: %.1f".format(classified.meanDistance)
            screen=AppScreen.REVIEW
        } catch(t:Throwable) { message="Classification failed: ${t.message}"; restartScan() }
    }

    fun rotateReviewFace(face:Face) {
        val all=reviewFaces ?: return; val src=all.getValue(face); val n=cubeSize
        val rotated=List(n*n){ idx -> val r=idx/n; val c=idx%n; src[(n-1-c)*n+r] }
        reviewFaces=all.toMutableMap().apply{ put(face,rotated) }
        refreshReviewValidation()
    }
    fun cycleReviewSticker(face:Face,index:Int) {
        val n=cubeSize; if(index==n*n/2) return
        val all=reviewFaces ?: return; val list=all.getValue(face).toMutableList()
        val cur=list[index]; list[index]=Face.entries[(cur.ordinal+1)%Face.entries.size]
        reviewFaces=all.toMutableMap().apply{ put(face,list) }
        refreshReviewValidation()
    }
    private fun refreshReviewValidation(){
        reviewFaces?.let { cube=CubeState(cubeSize).also { c->c.loadFaces(it) }; revision++; validation=validator.validate(cube) }
    }
    fun acceptReview() {
        val report=validation ?: return
        if(!report.ok){ message="Fix the highlighted scan/orientation problem first."; return }
        baseline=cube.snapshot(); originSolved=false; history.clear(); redo.clear(); clearSolution(); screen=AppScreen.STUDIO; message=null
    }

    fun applyMove(move:Move) {
        clearSolution(); cube.apply(move); history += move; redo.clear(); revision++
    }
    fun undo() {
        clearSolution(); val m=history.removeLastOrNull()?:return; cube.apply(m.inverse()); redo += m; revision++
    }
    fun redo() {
        clearSolution(); val m=redo.removeLastOrNull()?:return; cube.apply(m); history += m; revision++
    }
    fun returnToScan() {
        val b=baseline ?: return
        cube.loadFaces(b); history.clear(); redo.clear(); clearSolution(); revision++
    }
    fun resetSolved() {
        cube.resetSolved(); history.clear(); redo.clear(); clearSolution(); originSolved=true; baseline=null; palette=emptyMap(); revision++
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
        if (!cube.setStickerColor(key, color)) return
        history.clear(); redo.clear(); clearSolution(); revision++
        if (baseline != null) baseline = cube.snapshot()
        val target = cubeSize * cubeSize
        val bad = cube.colorCounts().filterValues { it != target }
        message = if (bad.isEmpty()) {
            "Color edit saved. Sticker counts are balanced."
        } else {
            "Color edit saved. Keep exactly $target stickers of each color before solving."
        }
    }

    fun solve() {
        if(solving) return
        solving=true; message=null
        val state=cube.deepCopy(); val h=history.toList(); val wasSolvedOrigin=originSolved
        viewModelScope.launch {
            val result=withContext(Dispatchers.Default){
                if(state.size==3) three.solve(state)
                else if(wasSolvedOrigin) five.solveKnownHistory(state,h) else five.solve(state)
            }
            solving=false
            when(result){
                is SolverResult.Success -> { solution=result.moves; solutionIndex=0; message=if(result.moves.isEmpty()) "Cube is already solved." else "Verified solution: ${result.moves.size} moves" }
                is SolverResult.Invalid -> message=result.reason
                is SolverResult.Unavailable -> message=result.reason
            }
        }
    }
    fun solutionNext() {
        val m=currentGuideMove?:return; cube.apply(m); history += m; redo.clear(); solutionIndex++; revision++
        if(solutionIndex>=solution.size && cube.isSolved()) message="Solved - replay verified."
    }
    fun solutionPrevious() {
        if(solutionIndex<=0) return; val m=solution[solutionIndex-1]; cube.apply(m.inverse()); solutionIndex--
        if(history.lastOrNull()==m) history.removeAt(history.lastIndex); revision++
    }
    fun clearMessage(){ message=null }
    private fun clearSolution(){ solution=emptyList(); solutionIndex=0 }
}
