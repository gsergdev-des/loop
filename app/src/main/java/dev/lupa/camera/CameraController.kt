package dev.lupa.camera

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.StateFlow

/**
 * Камера лупы. Всё про CameraX спрятано за этим интерфейсом: остальной код CameraX не импортирует,
 * кроме `PreviewView` на экране лупы. Методы вызываются с главного потока (кроме suspend-функций).
 */
interface CameraController {
    val status: StateFlow<CameraStatus>

    /** Привязывает Preview+ImageCapture+ImageAnalysis к lifecycleOwner и этому PreviewView (режим COMPATIBLE).
     *  Повторный вызов с новым PreviewView перепривязывает. */
    fun bind(previewView: PreviewView, lifecycleOwner: LifecycleOwner)

    /** Повторная попытка после CameraStatus.Error. */
    fun retry()

    /** Зум камеры; значение прижимается к [minZoom..maxZoom] устройства. */
    fun setZoomRatio(ratio: Float)

    fun enableTorch(on: Boolean)

    /** Фокус+экспозиция в точке; координаты — в системе PreviewView без программного масштаба.
     *  Автоотмена через 5 с. Возвращает true, если фокус взят. */
    suspend fun focusAt(x: Float, y: Float): Boolean

    /** Снимок ImageCapture (~12 Мп), повёрнутый в портрет и обрезанный по ViewPort (= видимой области превью
     *  без программного масштаба). Бросает CaptureException. */
    suspend fun capture(): Bitmap
}

/**
 * Состояние камеры. [Ready] приходит заново после каждого переподключения (вернулись из фона, `retry()`):
 * по нему потребитель заново применяет зум и фонарик.
 */
sealed interface CameraStatus {
    data object Starting : CameraStatus
    data class Ready(val capabilities: CameraCapabilities) : CameraStatus
    data class Error(val reason: CameraError) : CameraStatus
}

/** Возможности камеры устройства: зум из `ZoomState`, без констант. */
data class CameraCapabilities(val minZoom: Float, val maxZoom: Float, val hasFlash: Boolean)

enum class CameraError { IN_USE, DISABLED, NOT_FOUND, FAILED }

class CaptureException(message: String, cause: Throwable? = null) : Exception(message, cause)
