package dev.lupa.ui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PermissionScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var requests = 0
    private var settingsOpened = 0

    private fun show(canAskAgain: Boolean) {
        compose.setContent {
            LupaTheme {
                PermissionScreen(
                    canAskAgain = canAskAgain,
                    onRequest = { requests++ },
                    onOpenSettings = { settingsOpened++ },
                )
            }
        }
    }

    @Test
    fun `explains why the camera is needed and offers only the system settings when asking is over`() {
        show(canAskAgain = false)
        compose.onNodeWithTag(TestTags.PERMISSION_SCREEN).assertIsDisplayed()
        compose.onNodeWithText(
            "Лупе нужна камера, чтобы увеличивать изображение. Без доступа к камере она не работает.",
        ).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.BTN_OPEN_SETTINGS).assertIsDisplayed().assert(hasText("Открыть настройки"))
        compose.onNodeWithTag(TestTags.BTN_ASK_AGAIN).assertDoesNotExist()
    }

    @Test
    fun `allow button when the system can still ask`() {
        show(canAskAgain = true)
        compose.onNodeWithTag(TestTags.BTN_ASK_AGAIN).assertIsDisplayed().assert(hasText("Разрешить"))
        compose.onNodeWithTag(TestTags.BTN_OPEN_SETTINGS).assertIsDisplayed()
    }

    @Test
    fun `buttons call their actions`() {
        show(canAskAgain = true)
        compose.onNodeWithTag(TestTags.BTN_ASK_AGAIN).performClick()
        compose.onNodeWithTag(TestTags.BTN_OPEN_SETTINGS).performClick()
        assertEquals(1, requests)
        assertEquals(1, settingsOpened)
    }
}
