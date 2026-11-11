package dev.lupa.filters

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterTest {
    @Test
    fun `next goes around in the order from the spec`() {
        // Спецификация: Обычный → Высокий контраст → Ч/Б → Инверсия → Жёлтый на чёрном → снова Обычный.
        val expected = listOf(
            Filter.HIGH_CONTRAST,
            Filter.GRAYSCALE,
            Filter.INVERT,
            Filter.YELLOW_ON_BLACK,
            Filter.NORMAL,
        )
        var current = Filter.NORMAL
        val visited = expected.map { current = current.next(); current }
        assertEquals(expected, visited)
    }
}
