package dev.lupa.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.lupa.camera.CameraController
import dev.lupa.camera.CameraStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Состояние экрана лупы. Пока: камера, итоговый диапазон зума и наличие вспышки;
 * зум, фильтр, фонарик, стоп-кадр и сообщения добавят следующие таски (см. interfaces.md).
 */
data class MagnifierUiState(
    val camera: CameraStatus = CameraStatus.Starting,
    /** Итоговый диапазон зума: от max(1, minZoom камеры) до maxTotal = max(1, maxZoom камеры) × SOFT_MAX. */
    val minZoom: Float = 1f,
    val maxZoom: Float = 2f,
    val hasFlash: Boolean = false,
)

/**
 * Логика экрана лупы. Не знает про View и Compose. [camera] открыт, чтобы Activity привязывала превью
 * к тому же контроллеру, что и у VM, — в том числе после пересоздания Activity.
 */
class MagnifierViewModel(val camera: CameraController) : ViewModel() {
    private val mutableState = MutableStateFlow(MagnifierUiState())
    val state: StateFlow<MagnifierUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            camera.status.collect(::onCameraStatus)
        }
    }

    /** «Повторить» на экране ошибки камеры. */
    fun onRetry() {
        camera.retry()
    }

    private fun onCameraStatus(status: CameraStatus) {
        mutableState.update { state ->
            if (status is CameraStatus.Ready) {
                val capabilities = status.capabilities
                state.copy(
                    camera = status,
                    minZoom = maxOf(1f, capabilities.minZoom),
                    maxZoom = maxOf(1f, capabilities.maxZoom) * SOFT_MAX,
                    hasFlash = capabilities.hasFlash,
                )
            } else {
                state.copy(camera = status)
            }
        }
    }

    private companion object {
        /** Временно: таск 03 заменит на ZoomMath.maxTotal / ZoomMath.SOFT_MAX. */
        const val SOFT_MAX = 2f
    }
}
