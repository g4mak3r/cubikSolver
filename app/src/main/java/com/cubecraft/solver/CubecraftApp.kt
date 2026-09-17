package com.cubecraft.solver

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cubecraft.solver.ui.*

@Composable
fun CubecraftApp(vm: CubecraftViewModel = viewModel()) {
    val colors = lightColorScheme(
        background = AppBg,
        onBackground = Ink,
        surface = Panel,
        onSurface = Ink,
        surfaceVariant = Panel2,
        onSurfaceVariant = InkSoft,
        primary = Accent,
        onPrimary = androidx.compose.ui.graphics.Color.White,
        secondary = InkSoft,
        onSecondary = androidx.compose.ui.graphics.Color.White,
        outline = Outline,
        error = Danger,
        onError = androidx.compose.ui.graphics.Color.White
    )

    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = AppBg, contentColor = Ink) {
            when (vm.screen) {
                AppScreen.HOME -> HomeScreen(vm::beginScan, vm::openVirtual)
                AppScreen.SCAN -> ScannerScreen(
                    vm.cubeSize, vm.currentPose, vm.scanIndex,
                    vm::updateScanQuality, vm::captureFace, vm::home
                )
                AppScreen.FACE_CONFIRM -> vm.pendingFaceObservation?.let { observation ->
                    FaceConfirmScreen(
                        size = vm.cubeSize,
                        pose = vm.currentPose,
                        index = vm.scanIndex,
                        observation = observation,
                        onRescan = vm::rescanCurrentFace,
                        onConfirm = vm::confirmCurrentFace
                    )
                }
                AppScreen.REVIEW -> vm.reviewFaces?.let { faces ->
                    ReviewScreen(
                        vm.cubeSize, faces, vm.palette, vm.validation, vm.message,
                        vm::rotateReviewFace, vm::cycleReviewSticker, vm::acceptReview,
                        vm::restartScan, vm::home
                    )
                }
                AppScreen.STUDIO -> StudioScreen(
                    vm.cubeSize, vm.cube, vm.revision, vm.palette, vm.moveHistory,
                    vm.solution, vm.solutionIndex, vm.currentGuideMove, vm.solving,
                    vm.message, vm.hasBaseline, vm::home, vm::applyMove, vm::undo,
                    vm::redo, vm::returnToScan, vm::resetSolved, vm::solve,
                    vm::solutionNext, vm::solutionPrevious, vm::solutionSeek, vm::paintSticker
                )
            }
        }
    }
}
