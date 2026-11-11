package dev.lupa.filters

/** Цветовой фильтр картинки. Порядок объявления — порядок переключения кнопкой «Фильтр». */
enum class Filter {
    NORMAL, HIGH_CONTRAST, GRAYSCALE, INVERT, YELLOW_ON_BLACK;

    /** Следующий фильтр по кругу. */
    fun next(): Filter = entries[(ordinal + 1) % entries.size]
}
