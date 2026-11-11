package dev.lupa.zoom

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ZoomMathTest {
    private val eps = 1e-4f

    @Test
    fun `max total is camera max times soft zoom, and never below the soft zoom itself`() {
        assertEquals(20f, ZoomMath.maxTotal(10f), eps)
        assertEquals(2f, ZoomMath.maxTotal(1f), eps)
        assertEquals(2f, ZoomMath.maxTotal(0.6f), eps)
    }

    @Test
    fun `clamp keeps the value inside the range`() {
        assertEquals(1f, ZoomMath.clamp(0.3f, 1f, 20f), 0f)
        assertEquals(20f, ZoomMath.clamp(50f, 1f, 20f), 0f)
        assertEquals(5f, ZoomMath.clamp(5f, 1f, 20f), 0f)
    }

    @Test
    fun `below camera max the camera does all the zoom`() {
        val split = ZoomMath.split(4f, cameraMax = 10f)
        assertEquals(4f, split.cameraRatio, eps)
        assertEquals(1f, split.softScale, eps)
    }

    @Test
    fun `above camera max the camera is at max and the preview is stretched by the rest`() {
        val split = ZoomMath.split(15f, cameraMax = 10f)
        assertEquals(10f, split.cameraRatio, eps)
        assertEquals(1.5f, split.softScale, eps)
        val top = ZoomMath.split(20f, cameraMax = 10f)
        assertEquals(10f, top.cameraRatio, eps)
        assertEquals(2f, top.softScale, eps)
    }

    @Test
    fun `slider is logarithmic - ends are 0 and 1, equal ratios take equal travel`() {
        assertEquals(0f, ZoomMath.toSlider(1f, 1f, 20f), eps)
        assertEquals(1f, ZoomMath.toSlider(20f, 1f, 20f), eps)
        // Диапазон ×1…×16: ×4 ровно посередине (1→4 и 4→16 — по четыре раза).
        assertEquals(0.5f, ZoomMath.toSlider(4f, 1f, 16f), eps)
        assertEquals(
            ZoomMath.toSlider(2f, 1f, 20f) - ZoomMath.toSlider(1f, 1f, 20f),
            ZoomMath.toSlider(10f, 1f, 20f) - ZoomMath.toSlider(5f, 1f, 20f),
            eps,
        )
    }

    @Test
    fun `from slider reverses to slider`() {
        assertEquals(1f, ZoomMath.fromSlider(0f, 1f, 20f), eps)
        assertEquals(20f, ZoomMath.fromSlider(1f, 1f, 20f), eps)
        assertEquals(4f, ZoomMath.fromSlider(0.5f, 1f, 16f), eps)
        for (value in listOf(1f, 1.7f, 2f, 6.3f, 13f, 20f)) {
            assertEquals(value, ZoomMath.fromSlider(ZoomMath.toSlider(value, 1f, 20f), 1f, 20f), 1e-3f)
        }
    }

    @Test
    fun `slider positions outside 0 to 1 and values outside the range are pinned to the ends`() {
        assertEquals(20f, ZoomMath.fromSlider(1.4f, 1f, 20f), eps)
        assertEquals(1f, ZoomMath.fromSlider(-0.2f, 1f, 20f), eps)
        assertEquals(1f, ZoomMath.toSlider(30f, 1f, 20f), eps)
        assertEquals(0f, ZoomMath.toSlider(0.5f, 1f, 20f), eps)
    }

    @Test
    fun `steps up through 1, 1_5, 2, 3, 4, 5`() {
        val path = mutableListOf(1f)
        repeat(5) { path += ZoomMath.stepUp(path.last(), max = 20f) }
        assertEquals(listOf(1f, 1.5f, 2f, 3f, 4f, 5f), path)
    }

    @Test
    fun `steps down through the same row`() {
        val path = mutableListOf(5f)
        repeat(5) { path += ZoomMath.stepDown(path.last(), min = 1f) }
        assertEquals(listOf(5f, 4f, 3f, 2f, 1.5f, 1f), path)
    }

    @Test
    fun `steps from a value between row members go to the nearest member in that direction`() {
        assertEquals(2f, ZoomMath.stepUp(1.7f, 20f), eps)
        assertEquals(1.5f, ZoomMath.stepDown(1.7f, 1f), eps)
        assertEquals(3f, ZoomMath.stepUp(2.4f, 20f), eps)
        assertEquals(2f, ZoomMath.stepDown(2.4f, 1f), eps)
        assertEquals(8f, ZoomMath.stepUp(7.3f, 20f), eps)
    }

    @Test
    fun `steps are pinned to the borders`() {
        assertEquals(1f, ZoomMath.stepDown(1f, 1f), 0f)
        assertEquals(20f, ZoomMath.stepUp(20f, 20f), 0f)
        assertEquals(20f, ZoomMath.stepUp(19.5f, 20f), 0f)
        assertEquals(1f, ZoomMath.stepDown(1.2f, 1f), 0f)
        // Камера с минимумом ×1.2: ряд не опускается ниже него.
        assertEquals(1.2f, ZoomMath.stepDown(1.5f, 1.2f), eps)
    }

    @Test
    fun `format has one decimal and the multiplication sign`() {
        assertEquals("×4.0", ZoomMath.format(4f))
        assertEquals("×12.5", ZoomMath.format(12.5f))
        assertEquals("×1.5", ZoomMath.format(1.5f))
    }

    @Test
    fun `format uses a dot whatever the locale`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ru-RU"))
            assertEquals("×2.5", ZoomMath.format(2.5f))
            Locale.setDefault(Locale.forLanguageTag("de-DE"))
            assertEquals("×12.5", ZoomMath.format(12.5f))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `tap is mapped back through the preview scale around the centre`() {
        // Экран 1000×2000, превью растянуто ×2 вокруг центра (500, 1000).
        assertEquals(500f to 1000f, ZoomMath.unscaleTap(500f, 1000f, 1000f, 2000f, 2f))
        // Тап в (900, 1400): от центра (+400, +400) → в превью (+200, +200).
        assertEquals(700f to 1200f, ZoomMath.unscaleTap(900f, 1400f, 1000f, 2000f, 2f))
        // Без масштаба точка та же.
        assertEquals(123f to 456f, ZoomMath.unscaleTap(123f, 456f, 1000f, 2000f, 1f))
    }
}
