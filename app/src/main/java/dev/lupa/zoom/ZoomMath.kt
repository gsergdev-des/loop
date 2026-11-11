package dev.lupa.zoom

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

/** Разбиение итогового зума: что отдать камере и во сколько программно растянуть превью. */
data class ZoomSplit(val cameraRatio: Float, val softScale: Float)

/** Математика зума: чистый Kotlin, без Android. */
object ZoomMath {
    /** Программный зум превью сверх максимума камеры. */
    const val SOFT_MAX = 2f

    /** Масштаб стоп-кадра. */
    const val FROZEN_MAX = 8f

    /** Зум самого первого запуска. */
    const val DEFAULT_ZOOM = 2f

    /** Потолок итогового зума: максимум камеры (не меньше ×1) на программный зум. */
    fun maxTotal(cameraMax: Float): Float = maxOf(1f, cameraMax) * SOFT_MAX

    fun clamp(value: Float, min: Float, max: Float): Float = minOf(maxOf(value, min), max)

    /** Камере — сколько она может, остальное растягивает превью. */
    fun split(total: Float, cameraMax: Float): ZoomSplit {
        val cameraRatio = minOf(total, cameraMax)
        return ZoomSplit(cameraRatio, total / cameraRatio)
    }

    /** Положение ползунка 0..1 для значения зума; шкала логарифмическая. */
    fun toSlider(value: Float, min: Float, max: Float): Float {
        if (max <= min) return 0f
        val position = ln(clamp(value, min, max) / min) / ln(max / min)
        return clamp(position, 0f, 1f)
    }

    fun fromSlider(position: Float, min: Float, max: Float): Float {
        if (max <= min) return min
        return clamp(min * (max / min).pow(clamp(position, 0f, 1f)), min, max)
    }

    /** Следующий шаг ряда 1, 1.5, 2, 3, 4, … строго больше [value], не выше [max]. */
    fun stepUp(value: Float, max: Float): Float {
        val next = when {
            value < 1f - STEP_EPS -> 1f
            value < 1.5f - STEP_EPS -> 1.5f
            value < 2f - STEP_EPS -> 2f
            else -> floor(value + STEP_EPS) + 1f
        }
        return minOf(next, max)
    }

    /** Предыдущий шаг ряда строго меньше [value], не ниже [min]. */
    fun stepDown(value: Float, min: Float): Float {
        val previous = when {
            value > 2f + STEP_EPS -> ceil(value - STEP_EPS) - 1f
            value > 1.5f + STEP_EPS -> 1.5f
            else -> 1f
        }
        return maxOf(previous, min)
    }

    /** «×4.0»: один знак, всегда точка, какая бы ни была локаль. */
    fun format(value: Float): String = "×" + String.format(Locale.ROOT, "%.1f", value)

    /** Тап по экрану → точка в PreviewView до программного масштаба (он растянут вокруг центра). */
    fun unscaleTap(x: Float, y: Float, width: Float, height: Float, softScale: Float): Pair<Float, Float> {
        val centerX = width / 2f
        val centerY = height / 2f
        return (centerX + (x - centerX) / softScale) to (centerY + (y - centerY) / softScale)
    }

    private const val STEP_EPS = 1e-4f
}
