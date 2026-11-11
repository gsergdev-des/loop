package dev.lupa.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lupa.camera.CameraCapabilities
import dev.lupa.camera.CameraStatus
import dev.lupa.camera.CaptureException
import dev.lupa.camera.FakeCameraController
import dev.lupa.snapshot.FrozenViewport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Стоп-кадр на экране лупы: экран 360×800 dp при xxhdpi — 1080×2400 px. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MagnifierFreezeUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val camera = FakeCameraController()

    /** Левая половина снимка красная, правая синяя. */
    private val shot: Bitmap = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until width) for (y in 0 until height) setPixel(x, y, if (x < width / 2) Color.RED else Color.BLUE)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun show(override: ((MagnifierUiState) -> MagnifierUiState)? = null) {
        camera.captureBitmap = shot
        val viewModel = MagnifierViewModel(camera, testSettingsRepository(tmp.root, scope), FakeSnapshotSaver())
        compose.setContent {
            LupaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                MagnifierScreen(state = override?.invoke(state) ?: state, viewModel = viewModel, bindPreview = {})
            }
        }
        camera.emit(CameraStatus.Ready(CameraCapabilities(1f, 10f, hasFlash = true)))
        compose.waitForIdle()
    }

    /** Снимок окна целиком. `captureToImage` в Robolectric не дожидается кадра, поэтому рисуем окно сами. */
    private fun drawScreen(): Bitmap {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private fun busyIndicator() = compose.onAllNodes(
        hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate) and hasAnyAncestor(hasTestTag(TestTags.BTN_FREEZE)),
        useUnmergedTree = true,
    )

    @Test
    fun `freeze shows the shot over the preview and lights the button until the second press`() {
        show()
        compose.onAllNodesWithTag(TestTags.FROZEN_IMAGE).assertCountEquals(0)

        compose.onNodeWithTag(TestTags.BTN_FREEZE).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(TestTags.FROZEN_IMAGE).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.BTN_FREEZE).assertIsSelected()
        compose.onNodeWithTag(TestTags.ZOOM_INDICATOR).assertTextEquals("×2.0")

        compose.onNodeWithTag(TestTags.BTN_FREEZE).performClick()
        compose.waitForIdle()

        compose.onAllNodesWithTag(TestTags.FROZEN_IMAGE).assertCountEquals(0)
        compose.onNodeWithTag(TestTags.BTN_FREEZE).assertIsNotSelected()
    }

    @Test
    fun `freeze button shows it is busy while the shot is being taken`() {
        val gate = CompletableDeferred<Unit>()
        camera.captureGate = gate
        show()
        busyIndicator().assertCountEquals(0)

        compose.onNodeWithTag(TestTags.BTN_FREEZE).performClick()
        compose.waitForIdle()
        busyIndicator().assertCountEquals(1)

        gate.complete(Unit)
        compose.waitForIdle()
        busyIndicator().assertCountEquals(0)
        compose.onNodeWithTag(TestTags.FROZEN_IMAGE).assertIsDisplayed()
    }

    @Test
    fun `failed shot shows the message for about a second and the live picture stays`() {
        camera.captureError = CaptureException("нет снимка")
        show()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithTag(TestTags.BTN_FREEZE).performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag(TestTags.MESSAGE).assertIsDisplayed().assertTextEquals("Не удалось сделать снимок")
        compose.onAllNodesWithTag(TestTags.FROZEN_IMAGE).assertCountEquals(0)
        compose.onNodeWithTag(TestTags.BTN_FREEZE).assertIsNotSelected()

        compose.mainClock.advanceTimeBy(700)
        compose.onAllNodesWithTag(TestTags.MESSAGE).assertCountEquals(0)
    }

    @Test
    fun `frozen picture is drawn with the viewport scale and offset and the controls follow it`() {
        // ×2 и сдвиг вправо до упора (1080 / 2 px): на весь экран — левая, красная половина снимка.
        show(override = { it.copy(frozen = FrozenFrame(shot, baseZoom = 2f, viewport = FrozenViewport(2f, 540f, 0f))) })

        val screen = drawScreen()
        assertEquals(Color.RED, screen.getPixel(screen.width * 3 / 4, screen.height / 2))
        assertEquals(Color.RED, screen.getPixel(screen.width / 4, screen.height / 2))

        // Индикатор: зум камеры ×2 × масштаб ×2; ползунок — масштаб ×2 на шкале ×1…×8 (ln 2 / ln 8 = 1/3).
        compose.onNodeWithTag(TestTags.ZOOM_INDICATOR).assertTextEquals("×4.0")
        val slider = compose.onNodeWithTag(TestTags.ZOOM_SLIDER).fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(1f / 3f, slider.current, 1e-3f)
    }
}
