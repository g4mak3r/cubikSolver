package com.cubecraft.solver

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cubecraft.solver.solver.Min2PhaseSolver
import com.cubecraft.solver.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CubecraftApp(vm: CubecraftViewModel = viewModel()) {
    // min2phase builds pruning tables once. Doing that in the background at app start hides most
    // first-solve latency behind the time the user spends scanning and reviewing the cube.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            runCatching { Min2PhaseSolver.warmUp() }
        }
    }

    val colors = lightColorScheme(
        background = AppBg,
        onBackground = Ink,
        surface = Panel,
        onSurface = Ink,
        surfaceVariant = Panel2,
        onSurfaceVariant = InkSoft,
        primary = Accent,
        onPrimary = AppBg,
        secondary = InkSoft,
        onSecondary = AppBg,
        outline = Outline,
        error = Danger,
        onError = AppBg
    )

    val typography = Typography(
        bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
        bodySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
        labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
        labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
        titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 22.sp),
        titleMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp)
    )
    val shapes = Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(5.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(7.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(9.dp)
    )

    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes) {
        Surface(Modifier.fillMaxSize(), color = AppBg, contentColor = Ink) {
            when (vm.screen) {
                AppScreen.HOME -> HomeScreen(vm::beginScan, vm::openVirtual)
                AppScreen.SCAN -> ScannerScreen(
                    vm.cubeSize, vm.currentPose, vm.scanIndex, vm.frontCenterGuess,
                    vm::updateScanQuality, vm::captureFace, vm::home
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
