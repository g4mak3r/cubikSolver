package com.cubecraft.solver

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cubecraft.solver.solver.Min2PhaseSolver
import com.cubecraft.solver.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CubecraftApp(vm: CubecraftViewModel = viewModel()) {


    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            runCatching { Min2PhaseSolver.warmUp() }
        }
    }

    androidx.activity.compose.BackHandler(enabled = vm.screen != AppScreen.HOME) {
        if (vm.screen == AppScreen.FACE_CONFIRM) vm.rescanCurrentFace() else vm.home()
    }
    CubecraftTheme {
        Surface(Modifier.fillMaxSize(), color = AppBg, contentColor = Ink) {
            androidx.compose.animation.Crossfade(
                targetState = vm.screen,
                animationSpec = androidx.compose.animation.core.tween(CubeDesign.ScreenMillis),
                modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                label = "screenTransition"
            ) { screen ->
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
                        when (screen) {
                            AppScreen.HOME -> HomeScreen(vm::beginScan, vm::openVirtual)
                            AppScreen.SCAN -> ScannerScreen(
                                vm.cubeSize, vm.currentPose, vm.scanIndex, vm.expectedCenterGuess,
                                vm.scanGuideFaces,
                                { if (vm.screen == AppScreen.SCAN) vm.updateScanQuality(it) },
                                { if (vm.screen == AppScreen.SCAN) vm.captureFace(it) }, vm::home
                            )
                            AppScreen.FACE_CONFIRM -> vm.pendingFaceObservation?.let { observation ->
                                FaceConfirmScreen(
                                    size = vm.cubeSize,
                                    index = vm.scanIndex,
                                    observation = observation,
                                    overrides = vm.pendingFaceOverrides,
                                    message = vm.message,
                                    onSetColor = vm::setPendingFaceColor,
                                    onClearColor = vm::clearPendingFaceColor,
                                    onRescan = vm::rescanCurrentFace,
                                    onConfirm = vm::confirmCurrentFace
                                )
                            }
                            AppScreen.REVIEW -> vm.reviewFaces?.let { faces ->
                                ReviewScreen(
                                    size = vm.cubeSize,
                                    faces = faces,
                                    palette = vm.palette,
                                    colorFaces = vm.colorFaces,
                                    report = vm.validation,
                                    message = vm.message,
                                    onRotate = vm::rotateReviewFace,
                                    onSetColor = vm::setReviewStickerColor,
                                    onAccept = vm::acceptReview,
                                    onRescan = vm::restartScan,
                                    onBack = vm::home
                                )
                            }
                            AppScreen.STUDIO -> StudioScreen(
                                vm.cubeSize, vm.cube, vm.revision, vm.palette, vm.colorFaces, vm.moveHistory,
                                vm.solution, vm.solutionIndex, vm.currentGuideMove, vm.solving,
                                vm.message, vm.hasBaseline, vm::home, vm::applyMove, vm::undo,
                                vm::redo, vm::returnToScan, vm::resetSolved, vm::solve,
                                vm::solutionNext, vm::solutionPrevious, vm::solutionSeek, vm::paintSticker
                            )
                        }
                    }
                }
            }
        }
    }
}
