package dev.lupa.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.lupa.camera.CameraController
import dev.lupa.camera.CameraStatus
import dev.lupa.camera.CaptureException
import dev.lupa.filters.Filter
import dev.lupa.filters.Filters
import dev.lupa.settings.Settings
import dev.lupa.snapshot.SnapshotSaver
import dev.lupa.settings.launchZoom
import dev.lupa.settings.SettingsRepository
import dev.lupa.snapshot.FrozenMath
import dev.lupa.snapshot.FrozenViewport
import dev.lupa.zoom.ZoomMath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import java.io.IOException

/** Замороженный снимок: [baseZoom] — зум камеры в момент снимка, [viewport] — какая его часть видна. */
data class FrozenFrame(val bitmap: Bitmap, val baseZoom: Float, val viewport: FrozenViewport)

/** Чем занят экран: снимок для стоп-кадра или сохранение. Пока занят, та же кнопка не срабатывает. */
enum class Busy { NONE, FREEZING, SAVING }

/** Короткие сообщения поверх превью; UI убирает их по таймеру через [MagnifierViewModel.onMessageShown]. */
sealed interface UiMessage {
    /** Название фильтра крупно по центру, 1 с. */
    data class FilterName(val filter: Filter) : UiMessage

    /** «Сохранено», ~1,5 с. */
    data object Saved : UiMessage

    /** «Не удалось сохранить», ~1,5 с. */
    data object SaveFailed : UiMessage

    /** «Не удалось сделать снимок» (стоп-кадр), ~1 с. */
    data object CaptureFailed : UiMessage
}

/**
 * Состояние экрана лупы: камера, зум (значение, диапазон, программный масштаб), фонарик, фокус,
 * стоп-кадр, занятость, сообщение и настройки. Фильтр добавит таск 06 (см. interfaces.md).
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
    /** Замороженный снимок; `null` — живая камера. */
    val frozen: FrozenFrame? = null,
    val busy: Busy = Busy.NONE,
    val message: UiMessage? = null,
    val filter: Filter = Filter.NORMAL,
    val settings: Settings = Settings(),
) {
    /** Что показывает индикатор зума: живой — итоговый зум; стоп-кадр — зум камеры в момент снимка × его масштаб. */
    val indicatorZoom: Float
        get() = frozen?.let { it.baseZoom * it.viewport.scale } ?: zoom
}

/**
 * Логика экрана лупы. Не знает про View и Compose. [camera] открыт, чтобы Activity привязывала превью
 * к тому же контроллеру, что и у VM, — в том числе после пересоздания Activity.
 */
@OptIn(FlowPreview::class)
class MagnifierViewModel(
    val camera: CameraController,
    private val settingsRepository: SettingsRepository,
    private val saver: SnapshotSaver,
    /** Где кадрируется и красится снимок (большой Bitmap — не на главном потоке). */
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
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

    /** Вид стоп-кадра в момент заморозки — к нему возвращает двойной тап. */
    private var frozenStart = FrozenViewport()

    /** Первый `Ready` после запуска: тогда решает опция «Включать фонарик при запуске». */
    private var firstReadyHandled = false

    private val zoomToRemember = MutableSharedFlow<Float>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val filterToRemember = MutableSharedFlow<Filter>(
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
            mutableState.update {
                it.copy(zoom = launchZoom(settings, remembered), filter = remembered.lastFilter, settings = settings)
            }
            launchZoomResolved = true
            val camera = state.value.camera
            if (camera is CameraStatus.Ready) onCameraStatus(camera)
        }
        viewModelScope.launch {
            camera.status.collect(::onCameraStatus)
        }
        viewModelScope.launch {
            filterToRemember.debounce(REMEMBER_DELAY_MS).collect { filter ->
                try {
                    settingsRepository.rememberFilter(filter)
                } catch (_: IOException) {
                }
            }
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

    /** Изменение настроек: действует сразу и сохраняется. */
    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    /** «Повторить» на экране ошибки камеры. */
    fun onRetry() {
        camera.retry()
    }

    /** Ползунок: [position] 0..1 по логарифмической шкале. Живой — итоговый зум; стоп-кадр — его масштаб ×1…×8. */
    fun onSliderChange(position: Float) {
        val current = state.value
        if (current.frozen != null) {
            setFrozenScale(ZoomMath.fromSlider(position, 1f, ZoomMath.FROZEN_MAX))
            return
        }
        setZoom(ZoomMath.fromSlider(position, current.minZoom, current.maxZoom))
    }

    /**
     * Щипок и перетаскивание. Живой: текущий зум × коэффициент жеста (сдвиг не нужен).
     * Стоп-кадр: масштаб вокруг [centroidX], [centroidY] и сдвиг на [panX], [panY] px, края снимка не отходят от краёв экрана.
     */
    fun onPinch(zoomFactor: Float, centroidX: Float = 0f, centroidY: Float = 0f, panX: Float = 0f, panY: Float = 0f) {
        if (state.value.frozen != null) {
            updateViewport { FrozenMath.transform(it, zoomFactor, centroidX, centroidY, panX, panY, viewWidth, viewHeight) }
            return
        }
        if (zoomFactor == 1f) return
        setZoom(state.value.zoom * zoomFactor)
    }

    /** Двойной тап. Живой: к фиксированному зуму из настроек. Стоп-кадр: вид как в момент заморозки. */
    fun onDoubleTap() {
        if (state.value.frozen != null) {
            updateViewport { frozenStart }
            return
        }
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
        if (current.camera !is CameraStatus.Ready || current.frozen != null) return
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

    /**
     * Кнопка «Стоп-кадр». Живой режим: снимок камеры (он уже обрезан по видимой области превью без
     * программного масштаба), показанный с тем же программным масштабом, — то, что было на экране.
     * Пока снимок делается, кнопка занята. Стоп-кадр: обратно к живой камере, зум не менялся.
     */
    fun onFreezeClick() {
        val current = state.value
        if (current.busy != Busy.NONE) return
        if (current.frozen != null) {
            mutableState.update { it.copy(frozen = null) }
            return
        }
        if (current.camera !is CameraStatus.Ready) return
        // Вид запоминается в момент нажатия: пока снимок делается, живой зум не меняется (см. setZoom).
        val baseZoom = ZoomMath.split(current.zoom, cameraMax).cameraRatio
        val start = FrozenViewport(scale = current.softScale)
        mutableState.update { it.copy(busy = Busy.FREEZING) }
        viewModelScope.launch {
            try {
                val bitmap = camera.capture()
                frozenStart = start
                mutableState.update { it.copy(frozen = FrozenFrame(bitmap, baseZoom, start), busy = Busy.NONE) }
            } catch (_: CaptureException) {
                mutableState.update { it.copy(message = UiMessage.CaptureFailed, busy = Busy.NONE) }
            }
        }
    }

    /** UI показал сообщение положенное время — убрать. */
    fun onMessageShown() {
        mutableState.update { it.copy(message = null) }
    }

    /** Кнопка «Фильтр»: следующий по кругу, его название — на секунду по центру, выбор запоминается. */
    fun onFilterClick() {
        val filter = state.value.filter.next()
        mutableState.update { it.copy(filter = filter, message = UiMessage.FilterName(filter)) }
        filterToRemember.tryEmit(filter)
    }

    /**
     * Кнопка «Снимок»: в галерею уходит то, что сейчас на экране, вместе с фильтром. Живой режим — свежий снимок
     * камеры, обрезанный по программному зуму; стоп-кадр — его видимая часть. Пока идёт сохранение, повторные
     * нажатия игнорируются; итог — «Сохранено» или «Не удалось сохранить».
     */
    fun onSnapshotClick() {
        val current = state.value
        if (current.busy != Busy.NONE) return
        val frozen = current.frozen
        if (frozen == null && current.camera !is CameraStatus.Ready) return
        val softScale = current.softScale
        val filter = current.filter
        mutableState.update { it.copy(busy = Busy.SAVING, message = null) }
        viewModelScope.launch {
            val message = try {
                val source = frozen?.bitmap ?: camera.capture()
                val prepared = withContext(workDispatcher) {
                    val rect = if (frozen != null) {
                        FrozenMath.visibleRect(frozen.viewport, source.width, source.height, viewWidth, viewHeight)
                    } else {
                        FrozenMath.centerCrop(source.width, source.height, softScale)
                    }
                    val cropped = Bitmap.createBitmap(
                        source, rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top,
                    )
                    Filters.applyTo(cropped, filter)
                }
                if (saver.save(prepared).isSuccess) UiMessage.Saved else UiMessage.SaveFailed
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Ошибка камеры, нехватка памяти при кадрировании или записи — для человека всё одно.
                UiMessage.SaveFailed
            }
            mutableState.update { it.copy(message = message, busy = Busy.NONE) }
        }
    }

    /** `true` — кнопку громкости съели (зум или масштаб стоп-кадра изменён); `false` — отдать системе. */
    fun onVolumeKey(up: Boolean): Boolean {
        val current = state.value
        if (!current.settings.volumeKeysZoom) return false
        val frozen = current.frozen
        if (frozen != null) {
            val scale = frozen.viewport.scale
            setFrozenScale(if (up) ZoomMath.stepUp(scale, ZoomMath.FROZEN_MAX) else ZoomMath.stepDown(scale, 1f))
            return true
        }
        setZoom(
            if (up) ZoomMath.stepUp(current.zoom, current.maxZoom)
            else ZoomMath.stepDown(current.zoom, current.minZoom),
        )
        return true
    }

    /** Новый итоговый зум пользователя: в диапазон, камере, в состояние, в память. */
    private fun setZoom(requested: Float) {
        val current = state.value
        if (current.camera !is CameraStatus.Ready || current.busy != Busy.NONE) return
        applyZoom(requested)
        zoomToRemember.tryEmit(state.value.zoom)
    }

    /** Масштаб стоп-кадра вокруг центра экрана (ползунок, громкость). */
    private fun setFrozenScale(scale: Float) {
        updateViewport { FrozenMath.withScale(it, scale, viewWidth, viewHeight) }
    }

    private fun updateViewport(transform: (FrozenViewport) -> FrozenViewport) {
        mutableState.update { current ->
            val frozen = current.frozen ?: return@update current
            current.copy(frozen = frozen.copy(viewport = transform(frozen.viewport)))
        }
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
