package dev.lupa.camera

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Камера для тестов: состояние задаёт тест ([emit]), вызовы записываются,
 * [capture] отдаёт [captureBitmap] или бросает [captureError].
 */
class FakeCameraController(initial: CameraStatus = CameraStatus.Starting) : CameraController {
    private val mutableStatus = MutableStateFlow(initial)
    override val status: StateFlow<CameraStatus> = mutableStatus

    val bindCalls = mutableListOf<Pair<PreviewView, LifecycleOwner>>()
    var retryCount = 0
        private set
    val zoomRatios = mutableListOf<Float>()
    val torchCalls = mutableListOf<Boolean>()
    val focusCalls = mutableListOf<Pair<Float, Float>>()

    /** Что вернёт [focusAt]. */
    var focusResult = true

    /** Что отдаст [capture]; если задан [captureError], он бросается вместо снимка. */
    var captureBitmap: Bitmap? = null
    var captureError: Throwable? = null
    var captureCount = 0
        private set

    fun emit(status: CameraStatus) {
        mutableStatus.value = status
    }

    override fun bind(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        bindCalls += previewView to lifecycleOwner
    }

    override fun retry() {
        retryCount++
    }

    override fun setZoomRatio(ratio: Float) {
        zoomRatios += ratio
    }

    override fun enableTorch(on: Boolean) {
        torchCalls += on
    }

    override suspend fun focusAt(x: Float, y: Float): Boolean {
        focusCalls += x to y
        return focusResult
    }

    override suspend fun capture(): Bitmap {
        captureCount++
        captureError?.let { throw it }
        return captureBitmap ?: throw CaptureException("FakeCameraController: снимок не задан")
    }
}
