package dev.lupa.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lupa.camera.CameraCapabilities
import dev.lupa.camera.CameraStatus
import dev.lupa.camera.FakeCameraController
import dev.lupa.filters.Filter
import dev.lupa.snapshot.FrozenViewport
import kotlinx.coroutines.CompletableDeferred
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
import org.robolectric.annotation.GraphicsMode

/** Фильтры и «Снимок» на экране лупы: экран 360×800 dp при xxhdpi — 1080×2400 px. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MagnifierFilterUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val camera = FakeCameraController()
    private val saver = FakeSnapshotSaver()
    private val previews = mutableListOf<PreviewView>()

    private val shot: Bitmap = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun show(override: ((MagnifierUiState) -> MagnifierUiState)? = null) {
        camera.captureBitmap = shot
        val viewModel = MagnifierViewModel(
            camera, testSettingsRepository(tmp.root, scope), saver, workDispatcher = Dispatchers.Unconfined,
        )
        compose.setContent {
            LupaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                MagnifierScreen(
                    state = override?.invoke(state) ?: state,
                    viewModel = viewModel,
                    bindPreview = { previews += it },
                )
            }
        }
        camera.emit(CameraStatus.Ready(CameraCapabilities(1f, 10f, hasFlash = true)))
        compose.waitForIdle()
    }

    private fun drawScreen(): Bitmap {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    @Test
    fun `filter button shows the filter name big in the centre for one second and lights up`() {
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(TestTags.BTN_FILTER).assertIsNotSelected()

        compose.onNodeWithTag(TestTags.BTN_FILTER).performClick()
        compose.mainClock.advanceTimeBy(500)

        val name = compose.onNodeWithTag(TestTags.FILTER_NAME).assertIsDisplayed().assertTextEquals("Высокий контраст")
        val bounds = name.getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        assertEquals("по центру по горизонтали", (root.left + root.right).value / 2, (bounds.left + bounds.right).value / 2, 2f)
        assertEquals("по центру по вертикали", (root.top + root.bottom).value / 2, (bounds.top + bounds.bottom).value / 2, 40f)
        val layouts = mutableListOf<TextLayoutResult>()
        name.fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        val sp = layouts.single().layoutInput.style.fontSize.value
        assertTrue("надпись ≥ 32 sp, а она $sp", sp >= 32f)
        compose.onNodeWithTag(TestTags.BTN_FILTER).assertIsSelected()

        compose.mainClock.advanceTimeBy(700)
        compose.onAllNodesWithTag(TestTags.FILTER_NAME).assertCountEquals(0)
        compose.onNodeWithTag(TestTags.BTN_FILTER).assertIsSelected()
    }

    @Test
    fun `filter names follow the spec order and the button goes dark again on normal`() {
        show()
        val names = listOf("Высокий контраст", "Ч/Б", "Инверсия", "Жёлтый на чёрном", "Обычный")

        for (expected in names) {
            compose.onNodeWithTag(TestTags.BTN_FILTER).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(TestTags.FILTER_NAME).assertTextEquals(expected)
        }
        compose.onNodeWithTag(TestTags.BTN_FILTER).assertIsNotSelected()
    }

    @Test
    fun `frozen picture is drawn through the filter`() {
        show(override = {
            it.copy(frozen = FrozenFrame(shot, baseZoom = 2f, viewport = FrozenViewport()), filter = Filter.INVERT)
        })

        val screen = drawScreen()
        // Красный после инверсии — голубой.
        assertEquals(Color.CYAN, screen.getPixel(screen.width / 2, screen.height / 3))
    }

    @Test
    fun `saved message appears for one and a half seconds`() {
        show()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithTag(TestTags.BTN_SNAPSHOT).performClick()
        compose.mainClock.advanceTimeBy(1_400)
        compose.onNodeWithTag(TestTags.MESSAGE).assertIsDisplayed().assertTextEquals("Сохранено")

        compose.mainClock.advanceTimeBy(300)
        compose.onAllNodesWithTag(TestTags.MESSAGE).assertCountEquals(0)
    }

    @Test
    fun `failed save says so`() {
        saver.failure = java.io.IOException("нет места")
        show()

        compose.onNodeWithTag(TestTags.BTN_SNAPSHOT).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(TestTags.MESSAGE).assertTextEquals("Не удалось сохранить")
    }

    @Test
    fun `snapshot button shows it is busy while saving`() {
        val gate = CompletableDeferred<Unit>()
        saver.gate = gate
        show()
        val busy = {
            compose.onAllNodes(
                hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate) and
                    hasAnyAncestor(hasTestTag(TestTags.BTN_SNAPSHOT)),
                useUnmergedTree = true,
            )
        }
        busy().assertCountEquals(0)

        compose.onNodeWithTag(TestTags.BTN_SNAPSHOT).performClick()
        compose.waitForIdle()
        busy().assertCountEquals(1)

        gate.complete(Unit)
        compose.waitForIdle()
        busy().assertCountEquals(0)
    }
}
