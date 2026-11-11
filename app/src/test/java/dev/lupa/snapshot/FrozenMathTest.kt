package dev.lupa.snapshot

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Геометрия стоп-кадра на экране 1000×2000 px. Ожидания разобраны вручную: точка экрана p показывает
 * точку снимка u = c + (p − c − offset) / scale, где c — центр экрана.
 */
class FrozenMathTest {
    private val w = 1000f
    private val h = 2000f

    private fun assertViewport(expected: FrozenViewport, actual: FrozenViewport) {
        assertEquals("scale", expected.scale, actual.scale, 1e-4f)
        assertEquals("offsetX", expected.offsetX, actual.offsetX, 1e-3f)
        assertEquals("offsetY", expected.offsetY, actual.offsetY, 1e-3f)
    }

    // --- transform ---

    @Test
    fun `pinch scale never goes above 8`() {
        val vp = FrozenMath.transform(FrozenViewport(scale = 4f), 4f, 500f, 1000f, 0f, 0f, w, h)

        assertEquals(8f, vp.scale, 1e-4f)
    }

    @Test
    fun `pinch scale never goes below 1`() {
        val vp = FrozenMath.transform(FrozenViewport(scale = 1.5f), 0.1f, 500f, 1000f, 0f, 0f, w, h)

        assertViewport(FrozenViewport(1f, 0f, 0f), vp)
    }

    @Test
    fun `at scale 1 the picture cannot be dragged at all`() {
        val vp = FrozenMath.transform(FrozenViewport(), 1f, 500f, 1000f, 100f, -150f, w, h)

        assertViewport(FrozenViewport(1f, 0f, 0f), vp)
    }

    @Test
    fun `dragging stops where the picture edge meets the screen edge`() {
        // При ×2 снимок шире экрана на 1000 px и выше на 2000 px: с каждой стороны запас 500 и 1000.
        val vp = FrozenMath.transform(FrozenViewport(scale = 2f), 1f, 500f, 1000f, 2000f, -3000f, w, h)

        assertViewport(FrozenViewport(2f, 500f, -1000f), vp)
    }

    @Test
    fun `dragging inside the limits moves the picture by the finger travel`() {
        val vp = FrozenMath.transform(FrozenViewport(2f, 100f, 0f), 1f, 500f, 1000f, 50f, -200f, w, h)

        assertViewport(FrozenViewport(2f, 150f, -200f), vp)
    }

    @Test
    fun `pinch keeps the spot under the fingers in place`() {
        // Пальцы на (250, 500): там точка снимка (250, 500). После ×2 она должна остаться на (250, 500):
        // 500 + 2 · (250 − 500) + 250 = 250 и 1000 + 2 · (500 − 1000) + 500 = 500.
        val vp = FrozenMath.transform(FrozenViewport(), 2f, 250f, 500f, 0f, 0f, w, h)

        assertViewport(FrozenViewport(2f, 250f, 500f), vp)
    }

    @Test
    fun `pinch at the maximum does not slide the picture`() {
        val start = FrozenViewport(8f, 100f, 200f)

        val vp = FrozenMath.transform(start, 1.5f, 0f, 0f, 0f, 0f, w, h)

        assertViewport(start, vp)
    }

    // --- withScale ---

    @Test
    fun `withScale zooms around the screen centre`() {
        // В центре экрана до: 500 − 400 / 2 = 300 и 1000 + 600 / 2 = 1300; после ×4 то же: 500 − 800 / 4, 1000 + 1200 / 4.
        val vp = FrozenMath.withScale(FrozenViewport(2f, 400f, -600f), 4f, w, h)

        assertViewport(FrozenViewport(4f, 800f, -1200f), vp)
    }

    @Test
    fun `withScale from the middle stays centred`() {
        assertViewport(FrozenViewport(3f, 0f, 0f), FrozenMath.withScale(FrozenViewport(), 3f, w, h))
    }

    @Test
    fun `withScale back to 1 brings the picture back to the screen edges`() {
        val vp = FrozenMath.withScale(FrozenViewport(2f, 400f, -600f), 1f, w, h)

        assertViewport(FrozenViewport(1f, 0f, 0f), vp)
    }

    @Test
    fun `withScale is held in 1 to 8`() {
        assertEquals(8f, FrozenMath.withScale(FrozenViewport(), 20f, w, h).scale, 1e-4f)
        assertEquals(1f, FrozenMath.withScale(FrozenViewport(4f), 0.5f, w, h).scale, 1e-4f)
    }

    // --- visibleRect: снимок 3000×6000 на экране 1000×2000 ---

    @Test
    fun `at scale 1 the whole snapshot is visible`() {
        assertEquals(IntRectSpec(0, 0, 3000, 6000), FrozenMath.visibleRect(FrozenViewport(), 3000, 6000, w, h))
    }

    @Test
    fun `at scale 2 without offset the central half is visible`() {
        assertEquals(
            IntRectSpec(750, 1500, 2250, 4500),
            FrozenMath.visibleRect(FrozenViewport(scale = 2f), 3000, 6000, w, h),
        )
    }

    @Test
    fun `moved right and up to the limit at scale 2 shows the bottom left quarter`() {
        assertEquals(
            IntRectSpec(0, 3000, 1500, 6000),
            FrozenMath.visibleRect(FrozenViewport(2f, 500f, -1000f), 3000, 6000, w, h),
        )
    }

    @Test
    fun `an offset part inside the snapshot`() {
        // ×4, сдвиг (300, 200): x экрана 0…1000 → снимок 300…550 → ×3 px; y 0…2000 → 700…1200 → ×3 px.
        assertEquals(
            IntRectSpec(900, 2100, 1650, 3600),
            FrozenMath.visibleRect(FrozenViewport(4f, 300f, 200f), 3000, 6000, w, h),
        )
    }

    // --- centerCrop ---

    @Test
    fun `centerCrop without a soft zoom is the whole snapshot`() {
        assertEquals(IntRectSpec(0, 0, 3000, 4000), FrozenMath.centerCrop(3000, 4000, 1f))
    }

    @Test
    fun `centerCrop with a soft zoom of 2 is the central half`() {
        assertEquals(IntRectSpec(750, 1000, 2250, 3000), FrozenMath.centerCrop(3000, 4000, 2f))
    }
}
