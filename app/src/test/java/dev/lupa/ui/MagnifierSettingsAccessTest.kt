package dev.lupa.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lupa.camera.CameraCapabilities
import dev.lupa.camera.CameraStatus
import dev.lupa.camera.FakeCameraController
import dev.lupa.settings.ButtonSize
import dev.lupa.settings.Settings
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Шестерёнка, вибрация нажатий и размер кнопок на экране лупы. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
class MagnifierSettingsAccessTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val camera = FakeCameraController()
    private var opened = 0
    private lateinit var composeView: View

    /** Сбрасывает «последний отклик» теневого View другим значением: так видно, какое нажатие вызвало отклик. */
    private fun resetHaptics() {
        composeView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    private fun lastHaptic(): Int = shadowOf(composeView).lastHapticFeedbackPerformed()

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun show(settings: Settings = Settings()) {
        val viewModel = MagnifierViewModel(camera, testSettingsRepository(tmp.root, scope), FakeSnapshotSaver())
        compose.setContent {
            composeView = LocalView.current
            run {
                LupaTheme {
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    MagnifierScreen(
                        state = state.copy(settings = settings),
                        viewModel = viewModel,
                        bindPreview = {},
                        onOpenSettings = { opened++ },
                    )
                }
            }
        }
        camera.emit(CameraStatus.Ready(CameraCapabilities(1f, 10f, hasFlash = true)))
        compose.waitForIdle()
    }

    private val buttons = listOf(
        TestTags.BTN_TORCH, TestTags.BTN_FREEZE, TestTags.BTN_FILTER, TestTags.BTN_SNAPSHOT, TestTags.BTN_SETTINGS,
    )

    @Test
    fun `gear sits at the right edge of the controls above the slider and opens settings`() {
        show()

        val gear = compose.onNodeWithTag(TestTags.BTN_SETTINGS).assertIsDisplayed().assertHeightIsEqualTo(48.dp)
            .getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val controls = compose.onNodeWithTag(TestTags.CONTROLS).getUnclippedBoundsInRoot()
        val slider = compose.onNodeWithTag(TestTags.ZOOM_SLIDER).getUnclippedBoundsInRoot()
        assertTrue("шестерёнка над ползунком", gear.bottom <= slider.top)
        assertTrue("шестерёнка в блоке управления", gear.top >= controls.top)
        assertTrue("шестерёнка в правой половине", gear.left > root.width / 2)
        assertTrue("шестерёнка в нижней трети", gear.top >= root.height * 2 / 3)

        compose.onNodeWithTag(TestTags.BTN_SETTINGS).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `with haptics on every button and the gear ask for a virtual key tap`() {
        show(Settings(haptics = true))

        for (tag in buttons) {
            resetHaptics()
            compose.onNodeWithTag(tag).performClick()
            assertEquals("кнопка $tag", HapticFeedbackConstants.VIRTUAL_KEY, lastHaptic())
        }
    }

    @Test
    fun `with haptics off the buttons stay silent but still work`() {
        show(Settings(haptics = false))

        for (tag in buttons) {
            resetHaptics()
            compose.onNodeWithTag(tag).performClick()
            assertEquals("кнопка $tag", HapticFeedbackConstants.CONFIRM, lastHaptic())
        }
        assertEquals(1, opened)
    }

    @Test
    fun `large button size makes buttons 96 dp tall with 44 dp icons`() {
        show(Settings(buttonSize = ButtonSize.LARGE))

        for (tag in buttons.take(4)) {
            compose.onNodeWithTag(tag).assertHeightIsEqualTo(96.dp)
            val icon = compose.onNodeWithTag(TestTags.iconOf(tag), useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals(44f, icon.width.value, 0.5f)
        }
    }
}
