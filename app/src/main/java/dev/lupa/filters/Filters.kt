package dev.lupa.filters

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RenderEffect
import android.os.Build
import android.view.View
import androidx.compose.ui.graphics.ColorFilter

/**
 * Одна таблица матриц на три применения: живое превью, стоп-кадр в Compose и Bitmap при сохранении —
 * поэтому фильтр выглядит одинаково везде.
 */
object Filters {
    private const val LUMA_R = 0.299f
    private const val LUMA_G = 0.587f
    private const val LUMA_B = 0.114f

    /** Контраст вокруг середины шкалы: x' = k·x + 128·(1 − k). */
    private const val HIGH_CONTRAST_K = 2f
    private const val YELLOW_CONTRAST_K = 1.6f

    /** 20 чисел в соглашении [ColorMatrix]: 4 строки (R, G, B, A) по 5, смещения в шкале 0..255. */
    fun matrix(filter: Filter): FloatArray = when (filter) {
        Filter.NORMAL -> floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        Filter.HIGH_CONTRAST -> {
            val k = HIGH_CONTRAST_K
            val shift = 128f * (1f - k)
            floatArrayOf(
                k, 0f, 0f, 0f, shift,
                0f, k, 0f, 0f, shift,
                0f, 0f, k, 0f, shift,
                0f, 0f, 0f, 1f, 0f,
            )
        }
        Filter.GRAYSCALE -> floatArrayOf(
            LUMA_R, LUMA_G, LUMA_B, 0f, 0f,
            LUMA_R, LUMA_G, LUMA_B, 0f, 0f,
            LUMA_R, LUMA_G, LUMA_B, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        Filter.INVERT -> floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )
        Filter.YELLOW_ON_BLACK -> {
            // Яркость инвертируется (светлое → тёмное), затем контраст ×1.6 вокруг середины; в R и G, B = 0.
            val k = YELLOW_CONTRAST_K
            val shift = k * 255f + 128f * (1f - k)
            val r = -k * LUMA_R
            val g = -k * LUMA_G
            val b = -k * LUMA_B
            floatArrayOf(
                r, g, b, 0f, shift,
                r, g, b, 0f, shift,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        }
    }

    /**
     * Фильтр на View превью: RenderEffect на API 31+, аппаратный слой с [ColorMatrixColorFilter] на 29–30.
     * [Filter.NORMAL] снимает фильтр. Считается на GPU, без покадровой обработки на CPU.
     */
    fun applyTo(view: View, filter: Filter) {
        if (filter == Filter.NORMAL) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                view.setRenderEffect(null)
            } else {
                view.setLayerType(View.LAYER_TYPE_NONE, null)
            }
            return
        }
        val colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix(filter)))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setRenderEffect(RenderEffect.createColorFilterEffect(colorFilter))
        } else {
            view.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { this.colorFilter = colorFilter })
        }
    }

    /** Для Compose `Image` стоп-кадра; `null` для [Filter.NORMAL]. */
    fun colorFilter(filter: Filter): ColorFilter? =
        if (filter == Filter.NORMAL) null
        else ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(matrix(filter)))

    /** Новый Bitmap с фильтром (для сохранения); исходный не меняется. */
    fun applyTo(bitmap: Bitmap, filter: Filter): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            if (filter != Filter.NORMAL) colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix(filter)))
        }
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
}
