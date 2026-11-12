package dev.lupa

import android.Manifest
import android.app.Application
import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dev.lupa.camera.CameraController
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

/** Переход лупа ↔ настройки и флаг «не гасить экран» окна. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivitySettingsTest {
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

    private fun keepScreenOn(): Boolean {
        var flags = 0
        scenario.onActivity { flags = it.window.attributes.flags }
        return flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
    }

    @Test
    fun `gear opens settings and system back returns to the magnifier`() {
        compose.onNodeWithTag(TestTags.BTN_SETTINGS).performClick()
        compose.onNodeWithTag(TestTags.SETTINGS_SCREEN).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.PREVIEW).assertDoesNotExist()

        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        compose.onNodeWithTag(TestTags.PREVIEW).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.SETTINGS_SCREEN).assertDoesNotExist()
    }

    @Test
    fun `screen stays on while the magnifier is shown, not in settings`() {
        compose.waitForIdle()
        assertEquals(true, keepScreenOn())

        compose.onNodeWithTag(TestTags.BTN_SETTINGS).performClick()
        compose.waitForIdle()
        assertEquals(false, keepScreenOn())

        compose.onNodeWithTag(TestTags.BTN_BACK).performClick()
        compose.waitForIdle()
        assertEquals(true, keepScreenOn())
    }

    @Test
    fun `turning the option off drops the flag in the magnifier`() {
        compose.onNodeWithTag(TestTags.BTN_SETTINGS).performClick()
        compose.onNodeWithTag(TestTags.SETTING_KEEP_SCREEN_ON).performScrollTo().performClick()
        compose.onNodeWithTag(TestTags.BTN_BACK).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(TestTags.PREVIEW).assertIsDisplayed()
        // Настройка пишется в DataStore на своём потоке — ждём, пока она дойдёт до экрана.
        compose.waitUntil(timeoutMillis = 10_000) { !keepScreenOn() }
        assertEquals(false, keepScreenOn())

        // DataStore живёт на процесс и переживает тест: возвращаем опцию, чтобы не влиять на соседние.
        compose.onNodeWithTag(TestTags.BTN_SETTINGS).performClick()
        compose.onNodeWithTag(TestTags.SETTING_KEEP_SCREEN_ON).performScrollTo().performClick()
        compose.onNodeWithTag(TestTags.BTN_BACK).performClick()
        compose.waitUntil(timeoutMillis = 10_000) { keepScreenOn() }
    }
}
