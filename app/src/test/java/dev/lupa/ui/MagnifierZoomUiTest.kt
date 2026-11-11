package dev.lupa.ui

import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.width
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lupa.camera.CameraCapabilities
import dev.lupa.camera.CameraStatus
import dev.lupa.camera.FakeCameraController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Зум на экране лупы: индикатор, ползунок, щипок и двойной тап по превью. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MagnifierZoomUiTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val camera = FakeCameraController()

    @After
    fun tearDown() {
        scope.cancel()
    }

    /** Камера ×1…×10 → итоговый зум ×1…×20. */
    private fun showReady() {
        val viewModel = MagnifierViewModel(camera, testSettingsRepository(tmp.root, scope))
        compose.setContent {
            LupaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                MagnifierScreen(state = state, viewModel = viewModel, bindPreview = {})
            }
        }
        camera.emit(CameraStatus.Ready(CameraCapabilities(1f, 10f, true)))
        compose.waitForIdle()
    }

    private fun setSlider(position: Float) {
        compose.onNodeWithTag(TestTags.ZOOM_SLIDER)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(position) }
    }

    @Test
    fun `indicator shows the first-launch zoom in the times format`() {
        showReady()

        compose.onNodeWithTag(TestTags.ZOOM_INDICATOR).assertIsDisplayed().assertTextEquals("×2.0")
    }

    @Test
    fun `indicator is big and bold`() {
        showReady()

        val node = compose.onNodeWithTag(TestTags.ZOOM_INDICATOR).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        val style = results.single().layoutInput.style
        assertTrue("шрифт ≥ 28 sp, а он ${style.fontSize}", style.fontSize >= 28.sp)
        assertTrue("жирный", (style.fontWeight ?: FontWeight.Normal) >= FontWeight.Bold)
    }

    @Test
    fun `slider spans the width minus 16 dp margins and is at least 64 dp tall`() {
        showReady()

        val rootWidth = compose.onRoot().getUnclippedBoundsInRoot().width
        compose.onNodeWithTag(TestTags.ZOOM_SLIDER)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(rootWidth - 32.dp)
            .assertHeightIsAtLeast(64.dp)
    }

    @Test
    fun `slider drives the zoom - the end gives the camera maximum and the soft zoom, the indicator follows`() {
        showReady()

        setSlider(1f)

        compose.onNodeWithTag(TestTags.ZOOM_INDICATOR).assertTextEquals("×20.0")
        assertEquals(10f, camera.zoomRatios.last(), 1e-3f)

        setSlider(0f)
        compose.onNodeWithTag(TestTags.ZOOM_INDICATOR).assertTextEquals("×1.0")
        assertEquals(1f, camera.zoomRatios.last(), 1e-3f)
    }

    @Test
    fun `double tap on the preview returns to the default zoom`() {
        showReady()
        setSlider(1f)
        compose.onNodeWithTag(TestTags.ZOOM_INDICATOR).assertTextEquals("×20.0")

        compose.onRoot().performTouchInput { doubleClick(Offset(width / 2f, height / 3f)) }

        compose.onNodeWithTag(TestTags.ZOOM_INDICATOR).assertTextEquals("×2.0")
        assertEquals(2f, camera.zoomRatios.last(), 1e-3f)
    }

    @Test
    fun `pinching out on the preview zooms in`() {
        showReady()

        compose.onRoot().performTouchInput {
            val y = height / 3f
            pinch(
                start0 = Offset(width / 2f - 20f, y), end0 = Offset(width / 2f - 120f, y),
                start1 = Offset(width / 2f + 20f, y), end1 = Offset(width / 2f + 120f, y),
            )
        }

        assertTrue("зум вырос, а он ${camera.zoomRatios.last()}", camera.zoomRatios.last() > 2.5f)
    }
}
