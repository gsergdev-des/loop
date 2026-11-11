package dev.lupa.ui

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lupa.camera.CameraCapabilities
import dev.lupa.camera.CameraError
import dev.lupa.camera.CameraStatus
import dev.lupa.camera.FakeCameraController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Экран лупы с фейковой камерой: превью на весь экран и сообщения об ошибках камеры. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MagnifierScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val camera = FakeCameraController()
    private var boundPreviews = 0

    private fun show() {
        val viewModel = MagnifierViewModel(camera)
        compose.setContent {
            LupaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                MagnifierScreen(state = state, viewModel = viewModel, bindPreview = { boundPreviews++ })
            }
        }
    }

    @Test
    fun `camera busy in another app shows the message and retry reconnects`() {
        show()
        camera.emit(CameraStatus.Error(CameraError.IN_USE))

        compose.onNodeWithTag(TestTags.CAMERA_ERROR).assertIsDisplayed()
        compose.onNodeWithText("Камера занята другим приложением. Закройте его и нажмите «Повторить».")
            .assertIsDisplayed()
        compose.onNodeWithTag(TestTags.BTN_RETRY).assertIsDisplayed().assert(hasText("Повторить"))

        compose.onNodeWithTag(TestTags.BTN_RETRY).performClick()
        assertEquals(1, camera.retryCount)
    }

    @Test
    fun `every camera failure has its own message and the same retry button`() {
        show()
        val messages = mapOf(
            CameraError.DISABLED to "Камера отключена настройками телефона.",
            CameraError.NOT_FOUND to "На телефоне не найдена основная камера.",
            CameraError.FAILED to "Не удалось запустить камеру.",
        )
        for ((reason, message) in messages) {
            camera.emit(CameraStatus.Error(reason))
            compose.onNodeWithText(message).assertIsDisplayed()
            compose.onNodeWithTag(TestTags.BTN_RETRY).assertIsDisplayed()
        }
    }

    @Test
    fun `ready camera shows only the full screen preview bound once`() {
        show()
        camera.emit(CameraStatus.Error(CameraError.IN_USE))
        compose.onNodeWithTag(TestTags.CAMERA_ERROR).assertIsDisplayed()

        camera.emit(CameraStatus.Ready(CameraCapabilities(1f, 10f, true)))

        compose.onNodeWithTag(TestTags.CAMERA_ERROR).assertDoesNotExist()
        compose.onNodeWithTag(TestTags.PREVIEW).assertIsDisplayed()
        assertEquals(1, boundPreviews)
    }
}
