package dev.lupa

import android.Manifest
import android.app.Application
import android.content.Context
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dev.lupa.camera.CameraCapabilities
import dev.lupa.camera.CameraController
import dev.lupa.camera.CameraStatus
import dev.lupa.camera.FakeCameraController
import dev.lupa.ui.TestTags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Кнопки громкости через Activity: на лупе (опция «Зум кнопками громкости» по умолчанию вкл.)
 * они съедаются и шагают зум, на экране настроек уходят системе и зум не трогают.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityVolumeKeysTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val camera = FakeCameraController()
    private lateinit var realCameraFactory: (Context) -> CameraController

    @Before
    fun setUp() {
        realCameraFactory = MainActivity.cameraControllerFactory
        MainActivity.cameraControllerFactory = { camera }
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
        MainActivity.cameraControllerFactory = realCameraFactory
    }

    /**
     * Камера готова (зум камеры до ×10) и получила зум при запуске. Он ждёт настроек из DataStore на своём
     * потоке; под нагрузкой (параллельная release-сборка) это бывает дольше 10 с — отсюда запас.
     */
    private fun cameraReady() {
        camera.emit(CameraStatus.Ready(CameraCapabilities(minZoom = 1f, maxZoom = 10f, hasFlash = true)))
        compose.waitUntil(timeoutMillis = 30_000) { camera.zoomRatios.isNotEmpty() }
    }

    /** Нажатие и отпускание кнопки; в ответе — съела ли Activity каждое из двух событий. */
    private fun press(keyCode: Int): Pair<Boolean, Boolean> {
        var down = false
        var up = false
        scenario.onActivity {
            down = it.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            up = it.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
        return down to up
    }

    @Test
    fun `in the magnifier volume keys are eaten and step the zoom`() {
        cameraReady()
        compose.onNodeWithTag(TestTags.ZOOM_INDICATOR).assertTextEquals("×2.0")

        assertEquals("громкость «+» на лупе не съедена", true to true, press(KeyEvent.KEYCODE_VOLUME_UP))
        compose.onNodeWithTag(TestTags.ZOOM_INDICATOR).assertTextEquals("×3.0")
        assertEquals(3f, camera.zoomRatios.last())

        // Возвращаем ×2: запомненный зум живёт в DataStore на процесс и дошёл бы до соседних тестов.
        assertEquals("громкость «−» на лупе не съедена", true to true, press(KeyEvent.KEYCODE_VOLUME_DOWN))
        compose.onNodeWithTag(TestTags.ZOOM_INDICATOR).assertTextEquals("×2.0")
        assertEquals(2f, camera.zoomRatios.last())
    }

    @Test
    fun `in settings volume keys go to the system and leave the zoom alone`() {
        cameraReady()
        compose.onNodeWithTag(TestTags.BTN_SETTINGS).performClick()
        compose.onNodeWithTag(TestTags.SETTINGS_SCREEN).assertIsDisplayed()
        val zoomCallsBefore = camera.zoomRatios.toList()

        assertEquals("громкость «+» на настройках съедена", false to false, press(KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals("громкость «−» на настройках съедена", false to false, press(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertEquals("зум камеры менялся на настройках", zoomCallsBefore, camera.zoomRatios)

        compose.onNodeWithTag(TestTags.BTN_BACK).performClick()
        compose.onNodeWithTag(TestTags.ZOOM_INDICATOR).assertTextEquals("×2.0")
    }
}
