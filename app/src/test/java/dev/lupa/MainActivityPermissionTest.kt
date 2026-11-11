package dev.lupa

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dev.lupa.ui.TestTags
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityPermissionTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private lateinit var scenario: ActivityScenario<MainActivity>

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun lastPermissionRequest(): ShadowActivity.PermissionsRequest? {
        var request: ShadowActivity.PermissionsRequest? = null
        scenario.onActivity { request = shadowOf(it).lastRequestedPermission }
        return request
    }

    /** Пользователь отвечает «Запретить» в системном диалоге. */
    @Suppress("DEPRECATION")
    private fun denyCamera(systemCanAskAgain: Boolean) {
        scenario.onActivity { activity ->
            shadowOf(activity.packageManager)
                .setShouldShowRequestPermissionRationale(Manifest.permission.CAMERA, systemCanAskAgain)
            val request = shadowOf(activity).lastRequestedPermission
            activity.onRequestPermissionsResult(
                request.requestCode,
                request.requestedPermissions,
                intArrayOf(PackageManager.PERMISSION_DENIED),
            )
        }
    }

    @Test
    fun `with camera access the magnifier opens at once`() {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        launch()
        compose.onNodeWithTag(TestTags.PREVIEW).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.PERMISSION_SCREEN).assertDoesNotExist()
        assertNull(lastPermissionRequest())
    }

    @Test
    fun `without camera access the system request comes at launch`() {
        launch()
        val request = lastPermissionRequest()
        assertNotNull("при запуске нет запроса разрешения", request)
        assertArrayEquals(arrayOf(Manifest.permission.CAMERA), request!!.requestedPermissions)
        compose.onNodeWithTag(TestTags.PREVIEW).assertDoesNotExist()
    }

    @Test
    fun `refusal shows the explanation with allow button while the system can ask`() {
        launch()
        denyCamera(systemCanAskAgain = true)
        compose.onNodeWithTag(TestTags.PERMISSION_SCREEN).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.BTN_ASK_AGAIN).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.BTN_OPEN_SETTINGS).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.PREVIEW).assertDoesNotExist()
    }

    @Test
    fun `refusal for good leaves only the settings button`() {
        launch()
        denyCamera(systemCanAskAgain = false)
        compose.onNodeWithTag(TestTags.PERMISSION_SCREEN).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.BTN_ASK_AGAIN).assertDoesNotExist()
        compose.onNodeWithTag(TestTags.BTN_OPEN_SETTINGS).assertIsDisplayed()
    }

    @Test
    fun `open settings leads to the app page in system settings`() {
        launch()
        denyCamera(systemCanAskAgain = false)
        compose.onNodeWithTag(TestTags.BTN_OPEN_SETTINGS).performClick()
        val started = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.action)
        assertEquals("package:dev.lupa", started.dataString)
    }

    @Test
    fun `allow asks the system again`() {
        launch()
        val first = lastPermissionRequest()
        denyCamera(systemCanAskAgain = true)
        compose.onNodeWithTag(TestTags.BTN_ASK_AGAIN).performClick()
        val second = lastPermissionRequest()
        assertNotSame(first, second)
        assertArrayEquals(arrayOf(Manifest.permission.CAMERA), second!!.requestedPermissions)
    }

    @Test
    fun `access granted in settings opens the magnifier on return without restart`() {
        launch()
        denyCamera(systemCanAskAgain = false)
        lateinit var before: MainActivity
        scenario.onActivity { before = it }

        scenario.moveToState(Lifecycle.State.CREATED) // ушли в системные настройки
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        scenario.moveToState(Lifecycle.State.RESUMED) // вернулись

        compose.onNodeWithTag(TestTags.PREVIEW).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.PERMISSION_SCREEN).assertDoesNotExist()
        scenario.onActivity { assertSame(before, it) }
    }
}
