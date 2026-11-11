package dev.lupa.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LupaThemeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun schemeInsideTheme(): ColorScheme {
        lateinit var scheme: ColorScheme
        compose.setContent { LupaTheme { scheme = MaterialTheme.colorScheme } }
        compose.waitForIdle()
        return scheme
    }

    private fun assertLupaColors(scheme: ColorScheme) {
        assertEquals(Color.Black, scheme.background)
        assertEquals(Color(0xFFFFD600), scheme.primary)
        assertEquals(Color.Black, scheme.onPrimary)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun `dark scheme with yellow accent when the phone is in light mode`() {
        assertLupaColors(schemeInsideTheme())
    }

    @Test
    @Config(qualifiers = "night")
    fun `dark scheme with yellow accent when the phone is in dark mode`() {
        assertLupaColors(schemeInsideTheme())
    }

    @Test
    fun `every role of the scheme comes from the project palette`() {
        // Палитра проекта (interfaces.md): чёрный, жёлтый #FFD600, неактивная #CC2A2A2A, белый.
        val palette = setOf(Color.Black, Color(0xFFFFD600), Color(0xCC2A2A2A), Color.White)
        val roles = colorRoles(schemeInsideTheme())
        assertTrue("ролей подозрительно мало: ${roles.size}", roles.size >= 36)
        val foreign = roles.filterValues { it !in palette }
        assertTrue("роли не из палитры: $foreign", foreign.isEmpty())
    }

    @Test
    fun `outline is neutral, not the accent`() {
        // outline — рамка и ручка выключенного Switch, рамки полей: нейтральный цвет палитры.
        val outline = schemeInsideTheme().outline
        assertNotEquals(Color(0xFFFFD600), outline)
        assertTrue("outline $outline не нейтральный", outline in setOf(Color.Black, Color(0xCC2A2A2A), Color.White))
    }

    /** Все цветовые роли ColorScheme через его публичные геттеры (Color на JVM — long). */
    private fun colorRoles(scheme: ColorScheme): Map<String, Color> =
        ColorScheme::class.java.methods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == Long::class.javaPrimitiveType }
            .associate { it.name.removePrefix("get").substringBefore('-') to Color((it.invoke(scheme) as Long).toULong()) }
}
