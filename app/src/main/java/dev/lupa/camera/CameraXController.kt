package dev.lupa.camera

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.CameraUnavailableException
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.view.doOnLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.google.common.util.concurrent.ListenableFuture
import java.util.Locale
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * [CameraController] на CameraX: основная задняя камера, одна `UseCaseGroup` из Preview, ImageCapture
 * (~12 Мп) и ImageAnalysis (≤ 640×480, только счётчик кадров) с общим `ViewPort` от `PreviewView`.
 * Камера привязана к жизненному циклу Activity: свернули — CameraX закрывает камеру (и фонарик),
 * вернулись — открывает снова, и приходит новый [CameraStatus.Ready].
 *
 * Все поля живут на главном потоке.
 */
class CameraXController(context: Context) : CameraController {
    private val appContext = context.applicationContext

    private val mutableStatus = MutableStateFlow<CameraStatus>(CameraStatus.Starting)
    override val status: StateFlow<CameraStatus> = mutableStatus.asStateFlow()

    private var previewView: PreviewView? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var observedCameraState: LiveData<CameraState>? = null

    /** Номер попытки подключения: ответы от устаревших попыток (перепривязка, retry, уничтожение) игнорируются. */
    private var attempt = 0

    private val cameraStateObserver = Observer<CameraState> { onCameraState(it) }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            // Ошибку состояния камеры CameraX разрешит сам при новом открытии, а провалившуюся
            // привязку — нет: вернулись на экран без камеры — пробуем подключиться заново.
            if (camera == null && status.value is CameraStatus.Error) connect()
        }

        override fun onStop(owner: LifecycleOwner) {
            // Свернули — CameraX закрывает камеру и фонарик. Пока Activity остановлена, наблюдатель
            // CameraState не получает событий, поэтому сбрасываем статус сами: тогда открытие камеры
            // при возврате даст новый Ready, даже если он равен прежнему.
            if (camera != null) mutableStatus.value = CameraStatus.Starting
        }

        override fun onDestroy(owner: LifecycleOwner) {
            owner.lifecycle.removeObserver(this)
            if (owner === lifecycleOwner) {
                // Не держим уничтоженную Activity и её PreviewView: VM может пережить Activity.
                attempt++
                detachCamera()
                previewView = null
                lifecycleOwner = null
            }
        }
    }

    init {
        // CameraX инициализируется в фоне сразу, параллельно с первым кадром интерфейса (холодный старт).
        ProcessCameraProvider.getInstance(appContext)
    }

    override fun bind(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        // TextureView: только его содержимое проходит через RenderEffect фильтров и масштаб View.
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

        val previousOwner = this.lifecycleOwner
        this.previewView = previewView
        this.lifecycleOwner = lifecycleOwner
        connect()
        if (previousOwner !== lifecycleOwner) {
            previousOwner?.lifecycle?.removeObserver(lifecycleObserver)
            lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        }
    }

    override fun retry() {
        connect()
    }

    override fun setZoomRatio(ratio: Float) {
        val bound = camera ?: return
        val zoom = bound.cameraInfo.zoomState.value ?: return
        bound.cameraControl.setZoomRatio(ratio.coerceIn(zoom.minZoomRatio, zoom.maxZoomRatio))
    }

    override fun enableTorch(on: Boolean) {
        val bound = camera ?: return
        if (bound.cameraInfo.hasFlashUnit()) bound.cameraControl.enableTorch(on)
    }

    override suspend fun focusAt(x: Float, y: Float): Boolean = withContext(Dispatchers.Main.immediate) {
        val control = camera?.cameraControl ?: return@withContext false
        val view = previewView ?: return@withContext false
        val point = view.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(FOCUS_AUTO_CANCEL_SECONDS, TimeUnit.SECONDS)
            .build()
        try {
            control.startFocusAndMetering(action).await().isFocusSuccessful
        } catch (e: CancellationException) {
            ensureActive() // отменили нас — отмена идёт дальше; отменила камера — фокус не взят
            false
        } catch (e: Exception) {
            // Камера закрылась или новый запрос фокуса отменил этот.
            false
        }
    }

    override suspend fun capture(): Bitmap = withContext(Dispatchers.Main.immediate) {
        val useCase = imageCapture ?: throw CaptureException("Камера не готова к снимку")
        suspendCancellableCoroutine { continuation ->
            useCase.takePicture(
                Dispatchers.Default.asExecutor(),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        runCatching { image.use { it.toUprightBitmap() } }
                            .onSuccess { continuation.resume(it) }
                            .onFailure { continuation.resumeWithException(CaptureException("Не удалось подготовить снимок", it)) }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resumeWithException(CaptureException("Снимок не удался", exception))
                    }
                },
            )
        }
    }

    /** Новая попытка: ждём CameraX, затем раскладку PreviewView (нужна для ViewPort), затем привязываем. */
    private fun connect() {
        val owner = lifecycleOwner ?: return
        val view = previewView ?: return
        val current = ++attempt
        detachCamera()
        mutableStatus.value = CameraStatus.Starting

        val providerFuture = ProcessCameraProvider.getInstance(appContext)
        providerFuture.addListener({
            if (current != attempt) return@addListener
            val provider = try {
                providerFuture.get()
            } catch (e: Exception) {
                fail(errorOf(e), e)
                return@addListener
            }
            view.doOnLayout {
                if (current == attempt) bindUseCases(provider, owner, view)
            }
        }, appContext.mainExecutor)
    }

    private fun bindUseCases(provider: ProcessCameraProvider, owner: LifecycleOwner, view: PreviewView) {
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        val hasBackCamera = try {
            provider.hasCamera(selector)
        } catch (e: CameraInfoUnavailableException) {
            fail(CameraError.FAILED, e)
            return
        }
        if (!hasBackCamera) {
            fail(CameraError.NOT_FOUND, null)
            return
        }

        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(view.surfaceProvider)
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(CAPTURE_RESOLUTION)
            .build()
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(ANALYSIS_RESOLUTION)
            .build()
        analysis.setAnalyzer(analysisExecutor, FrameCounter())

        // ViewPort от PreviewView: снимок обрезается ровно по видимой области превью.
        val group = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(capture)
            .addUseCase(analysis)
            .apply { view.viewPort?.let(::setViewPort) }
            .build()

        val bound = try {
            provider.unbindAll()
            provider.bindToLifecycle(owner, selector, group)
        } catch (e: Exception) {
            fail(errorOf(e), e)
            return
        }
        camera = bound
        imageCapture = capture
        observedCameraState = bound.cameraInfo.cameraState.also { it.observe(owner, cameraStateObserver) }
    }

    private fun onCameraState(state: CameraState) {
        val error = state.error?.let { cameraErrorOf(it.code) }
        mutableStatus.value = when {
            error != null -> CameraStatus.Error(error)
            state.type == CameraState.Type.OPEN -> CameraStatus.Ready(capabilities())
            else -> CameraStatus.Starting
        }
    }

    private fun capabilities(): CameraCapabilities {
        val info = camera?.cameraInfo ?: return CameraCapabilities(minZoom = 1f, maxZoom = 1f, hasFlash = false)
        val zoom = info.zoomState.value
        return CameraCapabilities(
            minZoom = zoom?.minZoomRatio ?: 1f,
            maxZoom = zoom?.maxZoomRatio ?: 1f,
            hasFlash = info.hasFlashUnit(),
        )
    }

    private fun fail(error: CameraError, cause: Throwable?) {
        Log.w(TAG, "Камера не подключилась: $error", cause)
        detachCamera()
        mutableStatus.value = CameraStatus.Error(error)
    }

    private fun detachCamera() {
        observedCameraState?.removeObserver(cameraStateObserver)
        observedCameraState = null
        camera = null
        imageCapture = null
    }

    /** Ошибка CameraX при инициализации или привязке → причина для пользователя. */
    private fun errorOf(error: Throwable): CameraError {
        val unavailable = generateSequence(error) { it.cause }
            .take(MAX_CAUSE_DEPTH)
            .filterIsInstance<CameraUnavailableException>()
            .firstOrNull()
        return when (unavailable?.reason) {
            CameraUnavailableException.CAMERA_IN_USE,
            CameraUnavailableException.CAMERA_MAX_IN_USE,
            -> CameraError.IN_USE
            CameraUnavailableException.CAMERA_DISABLED -> CameraError.DISABLED
            null -> if (hasBackCameraHardware()) CameraError.FAILED else CameraError.NOT_FOUND
            else -> CameraError.FAILED
        }
    }

    private fun hasBackCameraHardware(): Boolean =
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)

    private companion object {
        const val TAG = "Lupa"
        const val FOCUS_AUTO_CANCEL_SECONDS = 5L
        const val MAX_CAUSE_DEPTH = 8

        /** Стоп-кадр и снимок: около 12 Мп (4000×3000, 4:3), ближайшее меньшее, затем большее. */
        val CAPTURE_RESOLUTION: ResolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(Size(4000, 3000), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
            )
            .build()

        /** Счётчик кадров: не больше 640×480, чтобы не отнимать ресурсы у превью. */
        val ANALYSIS_RESOLUTION: ResolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
            )
            .build()

        /** Один фоновый поток на процесс для счётчика кадров (контроллер может пересоздаваться вместе с VM). */
        val analysisExecutor: ExecutorService by lazy {
            Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "LupaFrames").apply { isDaemon = true } }
        }

        /** Время до первого кадра пишется один раз за процесс. */
        val firstFrameLogged = AtomicBoolean(false)

        /** Ошибка из `CameraState` → причина для пользователя; null — CameraX сам повторяет открытие. */
        fun cameraErrorOf(code: Int): CameraError? = when (code) {
            CameraState.ERROR_CAMERA_IN_USE, CameraState.ERROR_MAX_CAMERAS_IN_USE -> CameraError.IN_USE
            CameraState.ERROR_CAMERA_DISABLED -> CameraError.DISABLED
            CameraState.ERROR_CAMERA_REMOVED -> CameraError.NOT_FOUND
            CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> null
            else -> CameraError.FAILED // сбой потока, фатальная ошибка, «Не беспокоить»
        }
    }

    /**
     * Анализатор только считает кадры — для проверки на телефоне через `adb logcat -s Lupa`:
     * время от старта процесса до первого кадра и раз в 5 с — кадры в секунду. Кадр закрывается сразу.
     */
    private class FrameCounter : ImageAnalysis.Analyzer {
        private var windowStart = 0L
        private var lastFrame = 0L
        private var frames = 0

        override fun analyze(image: ImageProxy) {
            image.close()
            val now = SystemClock.elapsedRealtime()
            if (firstFrameLogged.compareAndSet(false, true)) {
                Log.i(TAG, "Первый кадр: ${now - Process.getStartElapsedRealtime()} мс от старта процесса")
            }
            if (lastFrame == 0L || now - lastFrame > PAUSE_RESET_MS) {
                // Первый кадр или кадры пошли после паузы (приложение сворачивали) — окно заново.
                windowStart = now
                frames = 0
            }
            lastFrame = now
            frames++
            val elapsed = now - windowStart
            if (elapsed >= FPS_WINDOW_MS) {
                val fps = (frames - 1) * 1000f / elapsed
                Log.i(TAG, String.format(Locale.ROOT, "Кадров в секунду: %.1f", fps))
                windowStart = now
                frames = 1
            }
        }

        private companion object {
            const val FPS_WINDOW_MS = 5_000L
            const val PAUSE_RESET_MS = 1_000L
        }
    }
}

/**
 * Снимок в портрет: обрезка по `cropRect` (видимая область превью в координатах буфера)
 * и поворот на `rotationDegrees`.
 */
private fun ImageProxy.toUprightBitmap(): Bitmap {
    val source = toBitmap()
    val crop = Rect(cropRect)
    if (!crop.intersect(0, 0, source.width, source.height)) crop.set(0, 0, source.width, source.height)
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0 && crop.width() == source.width && crop.height() == source.height) return source
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val upright = Bitmap.createBitmap(source, crop.left, crop.top, crop.width(), crop.height(), matrix, true)
    if (upright !== source) source.recycle()
    return upright
}

/** Ждёт ListenableFuture CameraX без блокировки потока; ошибка future бросается как есть. */
private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addListener({
        try {
            continuation.resume(get())
        } catch (e: ExecutionException) {
            continuation.resumeWithException(e.cause ?: e)
        } catch (e: Throwable) {
            continuation.resumeWithException(e)
        }
    }, Runnable::run)
}
