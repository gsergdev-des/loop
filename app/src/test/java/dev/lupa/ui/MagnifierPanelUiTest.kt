package dev.lupa.ui

import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Нижняя панель из четырёх кнопок и фокус по тапу, на экране шириной 360 dp. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
class MagnifierPanelUiTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val camera = FakeCameraController()
    private val buttons = listOf(TestTags.BTN_TORCH, TestTags.BTN_FREEZE, TestTags.BTN_FILTER, TestTags.BTN_SNAPSHOT)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun show(hasFlash: Boolean = true, override: ((MagnifierUiState) -> MagnifierUiState)? = null) {
        val viewModel = MagnifierViewModel(camera, testSettingsRepository(tmp.root, scope), FakeSnapshotSaver())
        compose.setContent {
            LupaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                MagnifierScreen(state = override?.invoke(state) ?: state, viewModel = viewModel, bindPreview = {})
            }
        }
        camera.emit(CameraStatus.Ready(CameraCapabilities(1f, 10f, hasFlash)))
        compose.waitForIdle()
    }

    @Test
    fun `controls hold exactly four buttons with their labels`() {
        show()

        buttons.forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
        for ((tag, label) in buttons.zip(listOf("Фонарик", "Стоп-кадр", "Фильтр", "Снимок"))) {
            compose.onNodeWithTag(tag).assertTextContains(label)
        }
        compose.onAllNodesWithTag(TestTags.BTN_SETTINGS).assertCountEquals(0)
    }

    @Test
    fun `every button is 80 dp tall, at least 80 dp wide, and the row splits the width equally`() {
        show()

        val bounds = buttons.map { compose.onNodeWithTag(it).assertHeightIsEqualTo(80.dp).getUnclippedBoundsInRoot() }
        bounds.forEach { assertTrue("ширина ≥ 80 dp, а она ${it.width}", it.width >= 80.dp) }
        assertEquals(1, bounds.map { it.width.value.toInt() }.distinct().size)
    }

    @Test
    fun `labels are at least 16 sp and icons at least 36 dp`() {
        show()

        for ((tag, label) in buttons.zip(listOf("Фонарик", "Стоп-кадр", "Фильтр", "Снимок"))) {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
                .fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            val size = results.single().layoutInput.style.fontSize
            assertTrue("подпись ≥ 16 sp, а она $size", size >= 16.sp)
            val icon = compose.onNodeWithTag(TestTags.iconOf(tag), useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue("иконка ≥ 36 dp, а она ${icon.width}", icon.width >= 36.dp && icon.height >= 36.dp)
        }
    }

    @Test
    fun `slider and buttons together sit in the bottom third of the screen`() {
        show()

        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val controls = compose.onNodeWithTag(TestTags.CONTROLS).getUnclippedBoundsInRoot()
        assertTrue("блок управления выше нижней трети: top=${controls.top}", controls.top >= root.height * 2 / 3)
        assertEquals(root.bottom.value, controls.bottom.value, 0.5f)
        val slider = compose.onNodeWithTag(TestTags.ZOOM_SLIDER).getUnclippedBoundsInRoot()
        val torch = compose.onNodeWithTag(TestTags.BTN_TORCH).getUnclippedBoundsInRoot()
        assertTrue("ползунок над кнопками", slider.bottom <= torch.top)
    }

    @Test
    fun `no flash means no torch button but the other three stay`() {
        show(hasFlash = false)

        compose.onAllNodesWithTag(TestTags.BTN_TORCH).assertCountEquals(0)
        listOf(TestTags.BTN_FREEZE, TestTags.BTN_FILTER, TestTags.BTN_SNAPSHOT)
            .forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
    }

    @Test
    fun `torch click reaches the camera and the button turns yellow`() {
        show()
        compose.onNodeWithTag(TestTags.BTN_TORCH).assertIsNotSelected()

        compose.onNodeWithTag(TestTags.BTN_TORCH).performClick()
        compose.waitForIdle()

        assertEquals(listOf(true), camera.torchCalls)
        compose.onNodeWithTag(TestTags.BTN_TORCH).assertIsSelected()
        compose.onNodeWithTag(TestTags.BTN_FREEZE).assertIsNotSelected()
    }

    @Test
    fun `the other three buttons can be pressed`() {
        show()

        listOf(TestTags.BTN_FREEZE, TestTags.BTN_FILTER, TestTags.BTN_SNAPSHOT)
            .forEach { compose.onNodeWithTag(it).performClick() }
    }

    @Test
    fun `single tap on the picture focuses there, double tap only resets the zoom`() {
        show()
        compose.onRoot().performTouchInput { click(Offset(width / 2f, height / 3f)) }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertEquals(1, camera.focusCalls.size)

        compose.onRoot().performTouchInput { doubleClick(Offset(width / 2f, height / 3f)) }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertEquals(1, camera.focusCalls.size)
        compose.onNodeWithTag(TestTags.ZOOM_INDICATOR).assertTextEquals("×2.0")
    }

    @Test
    fun `focus ring is drawn only while the state has a focus point`() {
        show(override = { it.copy(focusPoint = 100f to 300f) })
        compose.onNodeWithTag(TestTags.FOCUS_RING).assertIsDisplayed()
    }

    @Test
    fun `no focus point means no ring`() {
        show()
        compose.onAllNodesWithTag(TestTags.FOCUS_RING).assertCountEquals(0)
    }
}
