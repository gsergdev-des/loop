package dev.lupa.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import dev.lupa.settings.ButtonSize
import dev.lupa.settings.LaunchZoomMode
import dev.lupa.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var settings by mutableStateOf(Settings())
    private var backCount = 0

    private fun show() {
        compose.setContent {
            LupaTheme {
                SettingsScreen(
                    settings = settings,
                    onChange = { settings = it(settings) },
                    onBack = { backCount++ },
                )
            }
        }
    }

    private val rows = listOf(
        TestTags.SETTING_TORCH_ON_LAUNCH to "Включать фонарик при запуске",
        TestTags.SETTING_VOLUME_KEYS to "Зум кнопками громкости",
        TestTags.SETTING_LAUNCH_ZOOM to "Зум при запуске",
        TestTags.SETTING_KEEP_SCREEN_ON to "Не гасить экран, пока открыта лупа",
        TestTags.SETTING_HAPTICS to "Вибрация при нажатии",
        TestTags.SETTING_BUTTON_SIZE to "Размер кнопок",
    )

    @Test
    fun `all six items are shown in the brief order, each row at least 64 dp tall`() {
        show()

        compose.onNodeWithTag(TestTags.SETTINGS_SCREEN).assertIsDisplayed()
        val tops = rows.map { (tag, title) ->
            val node = compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assert(hasText(title, substring = true) or hasAnyDescendant(hasText(title, substring = true)))
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue("строка $tag ≥ 64 dp, а она ${bounds.bottom - bounds.top}", bounds.bottom - bounds.top >= 64.dp)
            bounds.top
        }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun `toggles start at brief defaults and a click changes the setting at once`() {
        show()

        compose.onNodeWithTag(TestTags.SETTING_TORCH_ON_LAUNCH).performScrollTo().performClick()
        compose.onNodeWithTag(TestTags.SETTING_VOLUME_KEYS).performScrollTo().performClick()
        compose.onNodeWithTag(TestTags.SETTING_KEEP_SCREEN_ON).performScrollTo().performClick()
        compose.onNodeWithTag(TestTags.SETTING_HAPTICS).performScrollTo().performClick()

        assertEquals(
            Settings(torchOnLaunch = true, volumeKeysZoom = false, keepScreenOn = false, haptics = false),
            settings,
        )
    }

    @Test
    fun `launch zoom offers fixed values only in fixed mode and picks one`() {
        show()
        compose.onNodeWithTag(TestTags.OPTION_ZOOM_LAST).performScrollTo().assertIsSelected()
        compose.onNodeWithTag(TestTags.fixedZoomOption(3f)).assertDoesNotExist()

        compose.onNodeWithTag(TestTags.OPTION_ZOOM_FIXED).performScrollTo().performClick()
        assertEquals(LaunchZoomMode.FIXED, settings.launchZoomMode)
        for (zoom in listOf(1.5f, 2f, 3f, 4f)) compose.onNodeWithTag(TestTags.fixedZoomOption(zoom)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TestTags.fixedZoomOption(2f)).assertIsSelected()
        compose.onNodeWithTag(TestTags.fixedZoomOption(3f)).performScrollTo().performClick()
        assertEquals(3f, settings.fixedZoom, 0f)
        compose.onNodeWithTag(TestTags.fixedZoomOption(2f)).assertIsNotSelected()

        compose.onNodeWithTag(TestTags.OPTION_ZOOM_LAST).performScrollTo().performClick()
        assertEquals(LaunchZoomMode.LAST, settings.launchZoomMode)
    }

    @Test
    fun `button size switches between normal and large, back arrow calls onBack`() {
        show()
        compose.onNodeWithTag(TestTags.OPTION_BUTTONS_NORMAL).performScrollTo().assertIsSelected()

        compose.onNodeWithTag(TestTags.OPTION_BUTTONS_LARGE).performScrollTo().performClick()
        assertEquals(ButtonSize.LARGE, settings.buttonSize)
        compose.onNodeWithTag(TestTags.OPTION_BUTTONS_NORMAL).assertIsNotSelected()

        compose.onNodeWithTag(TestTags.BTN_BACK).performClick()
        assertEquals(1, backCount)
    }
}
