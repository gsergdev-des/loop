package dev.lupa.snapshot

import dev.lupa.zoom.ZoomMath
import kotlin.math.roundToInt

/**
 * Видимая часть стоп-кадра. Снимок имеет пропорции экрана; при scale = 1 он ровно заполняет экран.
 * Снимок растянут в [scale] раз вокруг центра экрана и сдвинут на ([offsetX], [offsetY]) px экрана.
 */
data class FrozenViewport(val scale: Float = 1f, val offsetX: Float = 0f, val offsetY: Float = 0f)

/** Прямоугольник в пикселях: [left], [top] включительно, [right], [bottom] — нет. */
data class IntRectSpec(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * Геометрия стоп-кадра: чистый Kotlin, без Android.
 *
 * Точка экрана p показывает точку снимка (в px экрана при scale = 1) u = c + (p − c − offset) / scale,
 * где c — центр экрана. Края снимка не отходят от краёв экрана, пока |offset| ≤ (scale − 1) · размер / 2.
 */
object FrozenMath {
    /** Применить жест (масштаб zoom вокруг centroid, сдвиг pan) с ограничениями: scale в [1, FROZEN_MAX], края снимка не отходят от краёв экрана. */
    fun transform(
        vp: FrozenViewport, zoom: Float, centroidX: Float, centroidY: Float,
        panX: Float, panY: Float, viewW: Float, viewH: Float,
    ): FrozenViewport {
        val scale = clampScale(vp.scale * zoom)
        // Фактический коэффициент: у границы масштаба снимок не «уплывает» из-под пальцев.
        val k = scale / vp.scale
        val offsetX = (centroidX - viewW / 2f) * (1f - k) + vp.offsetX * k + panX
        val offsetY = (centroidY - viewH / 2f) * (1f - k) + vp.offsetY * k + panY
        return bounded(scale, offsetX, offsetY, viewW, viewH)
    }

    /** Масштаб для ползунка и кнопок громкости: вокруг центра экрана. */
    fun withScale(vp: FrozenViewport, scale: Float, viewW: Float, viewH: Float): FrozenViewport {
        val newScale = clampScale(scale)
        val k = newScale / vp.scale
        return bounded(newScale, vp.offsetX * k, vp.offsetY * k, viewW, viewH)
    }

    /** Прямоугольник снимка (в пикселях bitmap), который сейчас виден. */
    fun visibleRect(vp: FrozenViewport, bitmapW: Int, bitmapH: Int, viewW: Float, viewH: Float): IntRectSpec {
        // Размер экрана не известен — считаем экраном сам снимок.
        val w = if (viewW > 0f && viewH > 0f) viewW else bitmapW.toFloat()
        val h = if (viewW > 0f && viewH > 0f) viewH else bitmapH.toFloat()
        val (left, right) = visibleSpan(vp.scale, vp.offsetX, w, bitmapW)
        val (top, bottom) = visibleSpan(vp.scale, vp.offsetY, h, bitmapH)
        return IntRectSpec(left, top, right, bottom)
    }

    /** Центральная часть 1/softScale — то, что видно в живом превью при программном зуме. */
    fun centerCrop(bitmapW: Int, bitmapH: Int, softScale: Float): IntRectSpec {
        val scale = maxOf(1f, softScale)
        val (left, right) = visibleSpan(scale, 0f, bitmapW.toFloat(), bitmapW)
        val (top, bottom) = visibleSpan(scale, 0f, bitmapH.toFloat(), bitmapH)
        return IntRectSpec(left, top, right, bottom)
    }

    private fun clampScale(scale: Float): Float = ZoomMath.clamp(scale, 1f, ZoomMath.FROZEN_MAX)

    private fun bounded(scale: Float, offsetX: Float, offsetY: Float, viewW: Float, viewH: Float): FrozenViewport {
        val maxX = maxOf(0f, (scale - 1f) * viewW / 2f)
        val maxY = maxOf(0f, (scale - 1f) * viewH / 2f)
        return FrozenViewport(scale, offsetX.coerceIn(-maxX, maxX), offsetY.coerceIn(-maxY, maxY))
    }

    /** Видимый отрезок одной оси в px снимка: экран 0…[viewSize] → снимок, внутри 0…[bitmapSize], не пустой. */
    private fun visibleSpan(scale: Float, offset: Float, viewSize: Float, bitmapSize: Int): Pair<Int, Int> {
        val center = viewSize / 2f
        val pxPerView = bitmapSize / viewSize
        val start = ((center - (center + offset) / scale) * pxPerView).roundToInt().coerceIn(0, bitmapSize)
        val end = ((center + (center - offset) / scale) * pxPerView).roundToInt().coerceIn(0, bitmapSize)
        if (end > start) return start to end
        // Меньше пикселя (крошечный снимок) — всё равно один пиксель, чтобы было что сохранить.
        val first = minOf(start, bitmapSize - 1).coerceAtLeast(0)
        return first to first + 1
    }
}
