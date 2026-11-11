package dev.lupa.settings

import dev.lupa.filters.Filter
import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchZoomTest {
    @Test
    fun `fixed mode uses the fixed zoom, not the last one`() {
        val settings = Settings(launchZoomMode = LaunchZoomMode.FIXED, fixedZoom = 3f)
        assertEquals(3f, launchZoom(settings, Remembered(lastZoom = 5f, lastFilter = Filter.NORMAL)))
    }

    @Test
    fun `last mode uses the remembered zoom`() {
        val settings = Settings(launchZoomMode = LaunchZoomMode.LAST, fixedZoom = 3f)
        assertEquals(4.5f, launchZoom(settings, Remembered(lastZoom = 4.5f, lastFilter = Filter.NORMAL)))
    }

    @Test
    fun `last mode on the very first launch is x2`() {
        val settings = Settings(launchZoomMode = LaunchZoomMode.LAST, fixedZoom = 4f)
        assertEquals(2f, launchZoom(settings, Remembered(lastZoom = null, lastFilter = Filter.NORMAL)))
    }
}
