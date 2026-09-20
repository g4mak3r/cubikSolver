package com.cubecraft.solver.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.cubecraft.solver.model.*
import com.cubecraft.solver.scanner.*
import com.cubecraft.solver.solver.ValidationReport
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Native Android captures plus interaction checks at 360dp; not browser mockups. */
class DesignFlowTest {
    @get:Rule val ui = createComposeRule()
    private val colorFaces = mapOf(StickerGuess.WHITE to Face.U, StickerGuess.RED to Face.R,
        StickerGuess.GREEN to Face.F, StickerGuess.YELLOW to Face.D, StickerGuess.ORANGE to Face.L, StickerGuess.BLUE to Face.B)

    private fun screen(largeText: Boolean = false, content: @Composable () -> Unit) {
        ui.mainClock.autoAdvance = false
        ui.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 1.3f else 1f)) {
                CubecraftTheme { Surface(Modifier.fillMaxSize(), color = AppBg, contentColor = Ink, content = content) }
            }
        }
        ui.mainClock.advanceTimeBy(400)
    }

    private fun capture(name: String) {
        ui.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir("ui-captures"), "$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun homeSelectsFiveAndStartsScan() {
        var selected = 0
        screen { HomeScreen({ selected = it }, {}) }
        ui.onNodeWithText("5 × 5").performClick()
        ui.mainClock.advanceTimeBy(300)
        capture("01-home")
        ui.onNodeWithText("Scan a cube").assertIsDisplayed().performClick()
        assertEquals(5, selected)
    }

    @Test fun scannerPermissionStateFitsLargeText() {
        var permission = false
        screen(largeText = true) {
            ScannerLayout(5, scanSequence[4], 4, StickerGuess.WHITE, null, true, false, false, null, {}, {}, { permission = true }) {}
        }
        ui.onNodeWithText("Capture face").assertIsNotEnabled().assertIsDisplayed()
        ui.onNodeWithText("Allow camera").performScrollTo().assertIsDisplayed()
        capture("02-scanner-large-text")
        ui.onNodeWithText("Allow camera").performClick()
        assertTrue(permission)
    }

    @Test fun confirmationPaintsAndResetsColors() {
        val observation = FaceObservation(emptyList(), .8f, true,
            stickers = List(25) { LiveSticker(idealRgbForGuess(StickerGuess.GREEN), StickerGuess.GREEN, if (it == 0) .2f else .9f) })
        val overrides = mutableStateOf<Map<Int, StickerGuess>>(emptyMap())
        screen {
            FaceConfirmScreen(5, 0, observation, overrides.value, null,
                { i, c -> overrides.value = overrides.value + (i to c) },
                { i -> overrides.value = overrides.value - i }, {}, {})
        }
        ui.onNodeWithContentDescription("blue paint").performScrollTo().performClick()
        ui.onNodeWithContentDescription("Row 1, column 1, green").performScrollTo().performClick()
        ui.runOnIdle { assertEquals(StickerGuess.BLUE, overrides.value[0]) }
        capture("03-confirmation")
        ui.onNodeWithText("Reset colors").performScrollTo().performClick()
        ui.runOnIdle { assertTrue(overrides.value.isEmpty()) }
    }

    @Test fun reviewAndFaceEditorRemainUsable() {
        var rotations = 0
        screen {
            ReviewScreen(5, CubeState(5).snapshot(), defaultPalette, colorFaces, ValidationReport(true, emptyList()), null,
                { rotations++ }, { _, _, _ -> }, {}, {}, {})
        }
        ui.onNodeWithText("Solve cube").assertIsEnabled().assertIsDisplayed()
        capture("04-review")
        ui.onNodeWithContentDescription("Edit front face").performScrollTo().performClick()
        ui.mainClock.advanceTimeBy(600)
        ui.onNodeWithContentDescription("Rotate face clockwise").performClick()
        assertEquals(1, rotations)
        ui.onNodeWithText("Done").assertIsDisplayed().performClick()
    }

    @Test fun invalidReviewBlocksContinue() {
        screen(largeText = true) {
            ReviewScreen(5, CubeState(5).snapshot(), defaultPalette, colorFaces,
                ValidationReport(false, listOf("5x5 wing pieces are duplicated or flipped. Check the scan.")), null,
                {}, { _, _, _ -> }, {}, {}, {})
        }
        ui.onNodeWithText("Solve cube").assertIsNotEnabled().assertIsDisplayed()
        ui.onNodeWithText("A detail needs attention").performScrollTo().assertIsDisplayed()
        capture("05-review-error-large-text")
    }

    @Test fun studioStepsThroughSolution() {
        val moves = Move.parseAlgorithm("R U2 F' L D Rw")
        val index = mutableIntStateOf(0)
        val cube = CubeState(5)
        screen {
            StudioScreen(5, cube, index.intValue, defaultPalette, colorFaces, emptyList(), moves, index.intValue,
                moves.getOrNull(index.intValue), false, null, true, {}, {}, {}, {}, {}, {}, {},
                { index.intValue++ }, { index.intValue-- }, { index.intValue = it }, { _, _ -> })
        }
        ui.onNodeWithContentDescription("Next move").performScrollTo().performClick()
        ui.mainClock.advanceTimeBy(300)
        ui.onNodeWithText("U2").assertIsDisplayed()
        capture("06-solution")
        ui.onNodeWithContentDescription("Previous move").performClick()
        ui.mainClock.advanceTimeBy(300)
        assertEquals(0, index.intValue)
    }
}
