package dev.lupa.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.lupa.camera.CameraController
import dev.lupa.camera.CameraStatus
import dev.lupa.settings.Settings
import dev.lupa.settings.launchZoom
import dev.lupa.settings.SettingsRepository
import dev.lupa.zoom.ZoomMath
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Состояние экрана лупы. Пока: камера, зум (значение, диапазон, программный масштаб), настройки
 * и наличие вспышки; фильтр, фонарик, стоп-кадр и сообщения добавят следующие таски (см. interfaces.md).
 */
data class MagnifierUiState(
    val camera: CameraStatus = CameraStatus.Starting,
    /** Итоговый диапазон зума: от max(1, minZoom камеры) до maxTotal = max(1, maxZoom камеры) × SOFT_MAX. */
    val minZoom: Float = 1f,
    val maxZoom: Float = 2f,
    val hasFlash: Boolean = false,
    /** Итоговый зум живого превью (камера × программный). */
    val zoom: Float = ZoomMath.DEFAULT_ZOOM,
    /** Программный масштаб `PreviewView` вокруг центра: 1…[ZoomMath.SOFT_MAX]. */
    val softScale: Float = 1f,
    val torchOn: Boolean = false,
    /** Экранные координаты кружка фокуса; `null` — кружка нет. */
    val focusPoint: Pair<Float, Float>? = null,
    val settings: Settings = Settings(),
)

/**
 * Логика экрана лупы. Не знает про View и Compose. [camera] открыт, чтобы Activity привязывала превью
 * к тому же контроллеру, что и у VM, — в том числе после пересоздания Activity.
 */
@OptIn(FlowPreview::class)
class MagnifierViewModel(
    val camera: CameraController,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MagnifierUiState())
    val state: StateFlow<MagnifierUiState> = mutableState.asStateFlow()

    /** Максимум зума самой камеры; известен после первого `Ready`. */
    private var cameraMax = 1f

    /** Зум при запуске подгружен из настроек; до этого `Ready` ждёт. */
    private var launchZoomResolved = false

    private var viewWidth = 0f
    private var viewHeight = 0f
    private var lastTapId = 0
    private var ringTimer: Job? = null

    /** Первый `Ready` после запуска: тогда решает опция «Включать фонарик при запуске». */
    private var firstReadyHandled = false

    private val zoomToRemember = MutableSharedFlow<Float>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                mutableState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val remembered = settingsRepository.remembered.first()
            mutableState.update { it.copy(zoom = launchZoom(settings, remembered), settings = settings) }
            launchZoomResolved = true
            val camera = state.value.camera
            if (camera is CameraStatus.Ready) onCameraStatus(camera)
        }
        viewModelScope.launch {
            camera.status.collect(::onCameraStatus)
        }
        viewModelScope.launch {
            zoomToRemember.debounce(REMEMBER_DELAY_MS).collect { zoom ->
                // Не вышло записать — не страшно: зум просто не запомнится.
                try {
                    settingsRepository.rememberZoom(zoom)
                } catch (_: IOException) {
                }
            }
        }
    }

    /** «Повторить» на экране ошибки камеры. */
    fun onRetry() {
        camera.retry()
    }

    /** Ползунок: [position] 0..1 по логарифмической шкале итогового зума. */
    fun onSliderChange(position: Float) {
        val current = state.value
        setZoom(ZoomMath.fromSlider(position, current.minZoom, current.maxZoom))
    }

    /** Щипок: текущий зум × коэффициент жеста. */
    fun onPinch(zoomFactor: Float, centroidX: Float = 0f, centroidY: Float = 0f, panX: Float = 0f, panY: Float = 0f) {
        setZoom(state.value.zoom * zoomFactor)
    }

    /** Двойной тап: к фиксированному зуму из настроек. */
    fun onDoubleTap() {
        setZoom(state.value.settings.fixedZoom)
    }

    /** Фонарик вкл/выкл; без вспышки или пока камера не готова — ничего. */
    fun onTorchClick() {
        val current = state.value
        if (current.camera !is CameraStatus.Ready || !current.hasFlash) return
        val on = !current.torchOn
        mutableState.update { it.copy(torchOn = on) }
        camera.enableTorch(on)
    }

    /** Размер области превью в px: нужен, чтобы пересчитать тап с учётом программного масштаба. */
    fun onViewSize(width: Float, height: Float) {
        viewWidth = width
        viewHeight = height
    }

    /**
     * Тап по живому превью: фокус и экспозиция в точке. Кружок стоит, пока идёт фокусировка, но не дольше
     * [FOCUS_RING_MAX_MS]. Камера сама вернётся к непрерывному автофокусу через 5 с.
     */
    fun onTap(x: Float, y: Float) {
        val current = state.value
        if (current.camera !is CameraStatus.Ready) return
        val (px, py) = if (viewWidth > 0f && viewHeight > 0f) {
            ZoomMath.unscaleTap(x, y, viewWidth, viewHeight, current.softScale)
        } else {
            x to y
        }
        val tapId = ++lastTapId
        mutableState.update { it.copy(focusPoint = x to y) }
        ringTimer?.cancel()
        ringTimer = viewModelScope.launch {
            delay(FOCUS_RING_MAX_MS)
            hideRing(tapId)
        }
        viewModelScope.launch {
            camera.focusAt(px, py)
            hideRing(tapId)
        }
    }

    private fun hideRing(tapId: Int) {
        if (tapId == lastTapId) mutableState.update { it.copy(focusPoint = null) }
    }

    /** Кнопка «Стоп-кадр»: действие появится в таске 05. */
    fun onFreezeClick() {}

    /** Кнопка «Фильтр»: действие появится в таске 06. */
    fun onFilterClick() {}

    /** Кнопка «Снимок»: действие появится в таске 06. */
    fun onSnapshotClick() {}

    /** `true` — кнопку громкости съели (зум изменён); `false` — отдать системе. */
    fun onVolumeKey(up: Boolean): Boolean {
        val current = state.value
        if (!current.settings.volumeKeysZoom) return false
        setZoom(
            if (up) ZoomMath.stepUp(current.zoom, current.maxZoom)
            else ZoomMath.stepDown(current.zoom, current.minZoom),
        )
        return true
    }

    /** Новый итоговый зум пользователя: в диапазон, камере, в состояние, в память. */
    private fun setZoom(requested: Float) {
        val current = state.value
        if (current.camera !is CameraStatus.Ready) return
        applyZoom(requested)
        zoomToRemember.tryEmit(state.value.zoom)
    }

    private fun applyZoom(requested: Float) {
        val current = state.value
        val zoom = ZoomMath.clamp(requested, current.minZoom, current.maxZoom)
        val split = ZoomMath.split(zoom, cameraMax)
        mutableState.update { it.copy(zoom = zoom, softScale = split.softScale) }
        camera.setZoomRatio(split.cameraRatio)
    }

    private fun onCameraStatus(status: CameraStatus) {
        if (status !is CameraStatus.Ready) {
            mutableState.update { it.copy(camera = status) }
            return
        }
        val capabilities = status.capabilities
        cameraMax = maxOf(1f, capabilities.maxZoom)
        mutableState.update {
            it.copy(
                camera = status,
                minZoom = maxOf(1f, capabilities.minZoom),
                maxZoom = ZoomMath.maxTotal(capabilities.maxZoom),
                hasFlash = capabilities.hasFlash,
            )
        }
        // Каждое подключение камеры заново получает зум (после запуска — зум при запуске, прижатый к диапазону).
        if (launchZoomResolved) {
            applyZoom(state.value.zoom)
            if (!firstReadyHandled) {
                firstReadyHandled = true
                if (state.value.settings.torchOnLaunch && capabilities.hasFlash) {
                    mutableState.update { it.copy(torchOn = true) }
                }
            }
            if (state.value.torchOn && capabilities.hasFlash) camera.enableTorch(true)
        }
    }

    private companion object {
        const val REMEMBER_DELAY_MS = 300L
        const val FOCUS_RING_MAX_MS = 1500L
    }
}
