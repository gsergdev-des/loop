package dev.lupa.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Матрицы фильтров, применённые к цвету: ожидания — из описания фильтров в спецификации. */
class FiltersMatrixTest {
    /** Как делает ColorMatrix: строка из 5 чисел на канал, смещение в шкале 0..255, результат прижат к 0..255. */
    private fun apply(filter: Filter, r: Int, g: Int, b: Int): IntArray {
        val m = Filters.matrix(filter)
        assertEquals(20, m.size)
        return IntArray(3) { row ->
            val v = m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b + m[row * 5 + 4]
            Math.round(v.coerceIn(0f, 255f))
        }
    }

    @Test
    fun `normal keeps every colour`() {
        for (c in listOf(intArrayOf(0, 0, 0), intArrayOf(255, 255, 255), intArrayOf(200, 30, 90))) {
            assertEquals(c.toList(), apply(Filter.NORMAL, c[0], c[1], c[2]).toList())
        }
    }

    @Test
    fun `grayscale makes the channels equal and uses luma weights`() {
        val (r, g, b) = apply(Filter.GRAYSCALE, 200, 30, 90)
        assertTrue("R=G=B, а вышло $r $g $b", r == g && g == b)
        // 0.299*200 + 0.587*30 + 0.114*90 = 87.7
        assertEquals(88, r)
    }

    @Test
    fun `invert turns white into black and black into white`() {
        assertEquals(listOf(0, 0, 0), apply(Filter.INVERT, 255, 255, 255).toList())
        assertEquals(listOf(255, 255, 255), apply(Filter.INVERT, 0, 0, 0).toList())
        assertEquals(listOf(155, 205, 55), apply(Filter.INVERT, 100, 50, 200).toList())
    }

    @Test
    fun `yellow on black turns white into black and black into yellow`() {
        assertEquals(listOf(0, 0, 0), apply(Filter.YELLOW_ON_BLACK, 255, 255, 255).toList())
        val (r, g, b) = apply(Filter.YELLOW_ON_BLACK, 0, 0, 0)
        assertEquals(255, r)
        assertEquals(255, g)
        assertEquals(0, b)
    }

    @Test
    fun `yellow on black never has blue`() {
        for (v in listOf(0, 60, 128, 200, 255)) assertEquals(0, apply(Filter.YELLOW_ON_BLACK, v, v, v)[2])
    }

    @Test
    fun `high contrast keeps mid grey and lightens lighter grey`() {
        assertEquals(128, apply(Filter.HIGH_CONTRAST, 128, 128, 128)[0])
        assertTrue(apply(Filter.HIGH_CONTRAST, 160, 160, 160)[0] > 160)
        assertTrue(apply(Filter.HIGH_CONTRAST, 96, 96, 96)[0] < 96)
    }
}
