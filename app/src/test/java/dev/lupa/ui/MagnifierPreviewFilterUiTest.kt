package dev.lupa.ui

import android.view.View
import androidx.activity.ComponentActivity
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Фильтр доходит до `PreviewView`. На Android 10 это видно по слою View (на 12+ — RenderEffect, его не прочесть). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class MagnifierPreviewFilterUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val camera = FakeCameraController()
    private val previews = mutableListOf<PreviewView>()

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `preview view follows the chosen filter`() {
        val viewModel = MagnifierViewModel(camera, testSettingsRepository(tmp.root, scope), FakeSnapshotSaver())
        compose.setContent {
            LupaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                MagnifierScreen(state = state, viewModel = viewModel, bindPreview = { previews += it })
            }
        }
        camera.emit(CameraStatus.Ready(CameraCapabilities(1f, 10f, hasFlash = true)))
        compose.waitForIdle()
        val preview = previews.single()
        assertEquals(View.LAYER_TYPE_NONE, preview.layerType)

        compose.onNodeWithTag(TestTags.BTN_FILTER).performClick()
        compose.waitForIdle()
        assertEquals(View.LAYER_TYPE_HARDWARE, preview.layerType)

        repeat(4) { compose.onNodeWithTag(TestTags.BTN_FILTER).performClick() } // по кругу до «Обычный»
        compose.waitForIdle()
        assertEquals(View.LAYER_TYPE_NONE, preview.layerType)
    }
}
