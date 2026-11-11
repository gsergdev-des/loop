package dev.lupa.filters

import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Применение фильтров к View (Android 10–11 — аппаратный слой) и к Bitmap. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FiltersViewTest {
    private fun view() = FrameLayout(ApplicationProvider.getApplicationContext())

    @Test
    @Config(sdk = [29])
    fun `on Android 10 a filter puts the view on a hardware layer and normal takes it off`() {
        val view = view()

        Filters.applyTo(view, Filter.INVERT)
        assertEquals(View.LAYER_TYPE_HARDWARE, view.layerType)

        Filters.applyTo(view, Filter.NORMAL)
        assertEquals(View.LAYER_TYPE_NONE, view.layerType)
    }

    @Test
    @Config(sdk = [35])
    fun `bitmap gets the filter and the original stays as it was`() {
        val source = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }

        val result = Filters.applyTo(source, Filter.INVERT)

        assertNotSame(source, result)
        assertEquals(Color.BLACK, result.getPixel(2, 2))
        assertEquals(Color.WHITE, source.getPixel(2, 2))
        assertEquals(Color.WHITE, Filters.applyTo(source, Filter.NORMAL).getPixel(2, 2))
    }
}
