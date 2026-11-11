# Интерфейсы и правила проекта

Этот файл каждый исполнитель читает первым. Здесь границы между частями приложения, правила проекта и то, что построили завершённые таски. Имена в коде английские, тексты для пользователя русские.

## Правила проекта

- Один Gradle-модуль `app`, пакет и applicationId `dev.lupa`, название приложения «Лупа». Kotlin, Jetpack Compose (Material 3), CameraX, DataStore Preferences. minSdk 29, targetSdk 36, compileSdk 36, JVM target 17.
- Связка сборки: Gradle Wrapper **8.14.3**, AGP **8.13.x**, Kotlin **2.2.x** + `org.jetbrains.kotlin.plugin.compose`. Compose BOM **2025.10–2025.12**, CameraX **1.5.x**, DataStore **1.1.x**, Robolectric **4.16**. Версии хранятся в `gradle/libs.versions.toml`. AGP 9.x и Kotlin 2.3+ не брать.
- **Никаких** сетевых, рекламных, аналитических и крэш-библиотек, Firebase, Play Services. Никакого `INTERNET`. Из `uses-permission` итогового манифеста остаётся **только** `android.permission.CAMERA`. `androidx.core` добавляет `${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, его убирать через `tools:node="remove"`.
- Без `material-icons-extended` и без navigation-compose: иконки — свои векторные drawable в `res/drawable`, переход между лупой и настройками — простое состояние в `MainActivity`.
- Все тексты для пользователя — в `res/values/strings.xml`, по-русски. Тексты из спецификации переносить дословно.
- Тёмная тема всегда: фон чёрный, акцент — жёлтый `#FFD600`, «на акценте» — чёрный. Неактивная кнопка — `#CC2A2A2A` со светлым содержимым.
- Release: `isMinifyEnabled = true`, `isShrinkResources = true`. Подпись читается из `keystore.properties` в корне проекта, если файл есть. Если его нет — подпись debug-ключом и `logger.warn` с подсказкой про README. **Ключи и пароли в репозиторий не попадают никогда**, `keystore.properties`, `*.jks` и `*.keystore` — в `.gitignore`.
- **Команда проверки** (её прогоняет каждый таск, и она должна быть зелёной):
  ```
  ./gradlew --no-daemon testDebugUnitTest assembleRelease && python3 tools/check_apk.py
  ```
  `tools/check_apk.py` (создаёт T01) проверяет три вещи. Release-APK есть и весит ≤ 10 МБ. В итоговом манифесте `uses-permission` — ровно `android.permission.CAMERA`. Среди зависимостей нет запрещённых групп (`com.google.firebase`, `com.google.android.gms`, `com.squareup.okhttp3`, `io.sentry`, `com.google.ads`).
- **Один тест-класс:** `./gradlew --no-daemon testDebugUnitTest --tests 'dev.lupa.zoom.ZoomMathTest'`. **Установка:** зависимостей вне Gradle нет; JDK 17+ и Android SDK (platform 36, build-tools 36.0.0). **Запуск на телефоне:** `adb install -r app/build/outputs/apk/release/app-release.apk`.
- Не трогать: `.autopilot/` (кроме `interfaces.md` — его дописывает оркестратор), `AGENTS.md`, `CLAUDE.md`.
- Android SDK лежит в `/opt/android-sdk`. Путь — в `local.properties` (в `.gitignore`) или в `ANDROID_HOME`.
- UI-тесты — Robolectric + `createComposeRule`, `@Config(sdk = [35])`, `@GraphicsMode(NATIVE)` там, где нужны Bitmap. Камера в тестах — фейк `FakeCameraController` из `app/src/test`.
- В тестах элементы ищутся по тегам из `TestTags`, а не по координатам.

## Границы, решённые в спецификации

### `dev.lupa.camera` — камера (`CameraController`)

**Владеет:** CameraX (`ProcessCameraProvider`, `Preview`, `ImageCapture`, `ImageAnalysis` ≤ 640×480 — только счётчик кадров в Logcat `Lupa`: время до первого кадра от старта процесса и fps раз в 5 с; `UseCaseGroup` с `ViewPort`), выбором задней камеры, зумом камеры, вспышкой, метерингом, съёмкой кадра, ошибками камеры.
**Скрывает:** всё про CameraX. Остальной код CameraX не импортирует, кроме `PreviewView` в экране лупы.

```kotlin
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

sealed interface CameraStatus {
    data object Starting : CameraStatus
    data class Ready(val capabilities: CameraCapabilities) : CameraStatus
    data class Error(val reason: CameraError) : CameraStatus
}
data class CameraCapabilities(val minZoom: Float, val maxZoom: Float, val hasFlash: Boolean)
enum class CameraError { IN_USE, DISABLED, NOT_FOUND, FAILED }
class CaptureException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

- `Ready` приходит **заново после каждого переподключения** камеры (вернулись из фона, `retry()`). По нему потребитель заново применяет зум и фонарик.
- `maxZoom` берётся из `ZoomState.maxZoomRatio`, без констант.
- Реализация — `CameraXController(context: Context)`.

### `dev.lupa.zoom.ZoomMath` — математика зума (чистый Kotlin, без Android)

```kotlin
object ZoomMath {
    const val SOFT_MAX = 2f          // программный зум превью сверх максимума камеры
    const val FROZEN_MAX = 8f        // масштаб стоп-кадра
    const val DEFAULT_ZOOM = 2f      // первый запуск
    fun maxTotal(cameraMax: Float): Float                 // max(1, cameraMax) * SOFT_MAX
    fun clamp(value: Float, min: Float, max: Float): Float
    fun split(total: Float, cameraMax: Float): ZoomSplit  // cameraRatio = min(total, cameraMax), softScale = total / cameraRatio
    fun toSlider(value: Float, min: Float, max: Float): Float   // логарифмически в 0..1
    fun fromSlider(position: Float, min: Float, max: Float): Float
    fun stepUp(value: Float, max: Float): Float           // ряд 1, 1.5, 2, 3, 4, 5, 6 … (дальше +1)
    fun stepDown(value: Float, min: Float): Float
    fun format(value: Float): String                      // "×4.0", всегда точка, один знак
    /** Тап по экрану (x,y) в координаты PreviewView без программного масштаба softScale вокруг центра. */
    fun unscaleTap(x: Float, y: Float, width: Float, height: Float, softScale: Float): Pair<Float, Float>
}
data class ZoomSplit(val cameraRatio: Float, val softScale: Float)
```

Нижняя граница итогового зума — `max(1f, minZoom)`.

### `dev.lupa.filters` — фильтры (`Filters`)

```kotlin
enum class Filter { NORMAL, HIGH_CONTRAST, GRAYSCALE, INVERT, YELLOW_ON_BLACK;
    fun next(): Filter }                                   // по кругу, в этом порядке
object Filters {
    /** 20 чисел в соглашении android.graphics.ColorMatrix (смещения в шкале 0..255). */
    fun matrix(filter: Filter): FloatArray
    /** Фильтр на View превью: RenderEffect на API 31+, LAYER_TYPE_HARDWARE + ColorMatrixColorFilter на 29–30; NORMAL снимает. */
    fun applyTo(view: View, filter: Filter)
    /** Для Compose Image стоп-кадра; null для NORMAL. */
    fun colorFilter(filter: Filter): androidx.compose.ui.graphics.ColorFilter?
    /** Новый Bitmap с фильтром (для сохранения). */
    fun applyTo(bitmap: Bitmap, filter: Filter): Bitmap
}
```

Названия фильтров для пользователя: `Обычный`, `Высокий контраст`, `Ч/Б`, `Инверсия`, `Жёлтый на чёрном`. Это строки ресурсов. `Filter` → строка сопоставляется в UI.

### `dev.lupa.settings` — настройки и запомненное (`SettingsRepository`)

```kotlin
enum class LaunchZoomMode { LAST, FIXED }
enum class ButtonSize { NORMAL, LARGE }
data class Settings(
    val torchOnLaunch: Boolean = false,
    val volumeKeysZoom: Boolean = true,
    val launchZoomMode: LaunchZoomMode = LaunchZoomMode.LAST,
    val fixedZoom: Float = 2f,                 // один из FIXED_ZOOM_CHOICES
    val keepScreenOn: Boolean = true,
    val haptics: Boolean = true,
    val buttonSize: ButtonSize = ButtonSize.NORMAL,
)
data class Remembered(val lastZoom: Float?, val lastFilter: Filter)   // lastZoom == null — первый запуск

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<Settings>
    val remembered: Flow<Remembered>
    suspend fun update(transform: (Settings) -> Settings)
    suspend fun rememberZoom(zoom: Float)
    suspend fun rememberFilter(filter: Filter)
    companion object {
        val FIXED_ZOOM_CHOICES = listOf(1.5f, 2f, 3f, 4f)
        fun create(context: Context): SettingsRepository   // один экземпляр DataStore "settings" на процесс
    }
}
/** Зум при запуске: FIXED → fixedZoom; LAST → lastZoom ?: ZoomMath.DEFAULT_ZOOM. */
fun launchZoom(settings: Settings, remembered: Remembered): Float
```

Тесты создают репозиторий на `PreferenceDataStoreFactory.create { tmpFile }`.

### `dev.lupa.snapshot` — стоп-кадр и сохранение

```kotlin
/** Видимая часть стоп-кадра. Снимок имеет пропорции экрана; при scale = 1 он ровно заполняет экран. */
data class FrozenViewport(val scale: Float = 1f, val offsetX: Float = 0f, val offsetY: Float = 0f) // смещение в px экрана
object FrozenMath {
    /** Применить жест (масштаб zoom вокруг centroid, сдвиг pan) с ограничениями: scale в [1, FROZEN_MAX], края снимка не отходят от краёв экрана. */
    fun transform(vp: FrozenViewport, zoom: Float, centroidX: Float, centroidY: Float,
                  panX: Float, panY: Float, viewW: Float, viewH: Float): FrozenViewport
    fun withScale(vp: FrozenViewport, scale: Float, viewW: Float, viewH: Float): FrozenViewport // для ползунка/громкости, вокруг центра
    /** Прямоугольник снимка (в пикселях bitmap), который сейчас виден. */
    fun visibleRect(vp: FrozenViewport, bitmapW: Int, bitmapH: Int, viewW: Float, viewH: Float): IntRectSpec
    /** Центральная часть 1/softScale — то, что видно в живом превью при программном зуме. */
    fun centerCrop(bitmapW: Int, bitmapH: Int, softScale: Float): IntRectSpec
}
data class IntRectSpec(val left: Int, val top: Int, val right: Int, val bottom: Int)

interface SnapshotSaver { suspend fun save(bitmap: Bitmap): Result<Uri> }
class MediaStoreSnapshotSaver(context: Context) : SnapshotSaver  // Pictures/Lupa, JPEG 92, IS_PENDING, Lupa_yyyyMMdd_HHmmss.jpg
```

### `dev.lupa.ui.MagnifierViewModel` — состояние экрана лупы

**Владеет:** желаемым зумом, фильтром, фонариком, стоп-кадром, «занятостью», сообщениями. **Не знает** про View и Compose: координаты получает числами.

```kotlin
data class FrozenFrame(val bitmap: Bitmap, val baseZoom: Float, val viewport: FrozenViewport)
enum class Busy { NONE, FREEZING, SAVING }
sealed interface UiMessage {                       // показываются поверх превью
    data class FilterName(val filter: Filter) : UiMessage   // 1 с, по центру
    data object Saved : UiMessage                            // ~1,5 с
    data object SaveFailed : UiMessage
    data object CaptureFailed : UiMessage
}
data class MagnifierUiState(
    val camera: CameraStatus = CameraStatus.Starting,
    val zoom: Float = ZoomMath.DEFAULT_ZOOM,        // итоговый зум живого превью
    val minZoom: Float = 1f, val maxZoom: Float = 2f, // итоговый диапазон (maxTotal)
    val softScale: Float = 1f,                       // для масштаба PreviewView
    val filter: Filter = Filter.NORMAL,
    val torchOn: Boolean = false, val hasFlash: Boolean = false,
    val frozen: FrozenFrame? = null,
    val busy: Busy = Busy.NONE,
    val focusPoint: Pair<Float, Float>? = null,      // экранные координаты кружка
    val message: UiMessage? = null,
    val settings: Settings = Settings(),
) { val indicatorZoom: Float }                       // живой: zoom; стоп-кадр: baseZoom × viewport.scale

class MagnifierViewModel(
    private val camera: CameraController,
    private val settingsRepository: SettingsRepository,
    private val saver: SnapshotSaver,
) : ViewModel() {
    val state: StateFlow<MagnifierUiState>
    fun onSliderChange(position: Float)              // 0..1; живой — итоговый зум, стоп-кадр — его масштаб
    fun onPinch(zoomFactor: Float, centroidX: Float = 0f, centroidY: Float = 0f, panX: Float = 0f, panY: Float = 0f)
    fun onDoubleTap()                                // живой — к fixedZoom; стоп-кадр — к виду в момент заморозки
    fun onTap(x: Float, y: Float)                    // только живой режим: фокус + кружок
    fun onVolumeKey(up: Boolean): Boolean            // true — событие съедено (опция вкл.)
    fun onTorchClick(); fun onFreezeClick(); fun onFilterClick(); fun onSnapshotClick()
    fun onViewSize(width: Float, height: Float)      // размер области превью в px
    fun onMessageShown()                             // UI убирает сообщение по таймеру
    fun onRetry()
    fun updateSettings(transform: (Settings) -> Settings)
}
```

- На каждый `CameraStatus.Ready` VM заново применяет зум камеры (`split(zoom).cameraRatio`) и фонарик (желаемое состояние). На первом `Ready` после запуска фонарик включается, если `settings.torchOnLaunch`.
- Зум при запуске — `launchZoom(settings, remembered)`, прижатый к диапазону, когда пришёл `Ready`. Фильтр — `remembered.lastFilter`. Каждое изменение зума и фильтра пишется в репозиторий с задержкой ~300 мс.

### `dev.lupa.ui` — экраны (Compose) и `MainActivity`

```kotlin
@Composable fun MagnifierScreen(state: MagnifierUiState, viewModel: MagnifierViewModel /* действия */,
                                bindPreview: (PreviewView) -> Unit, onOpenSettings: () -> Unit)
@Composable fun SettingsScreen(settings: Settings, onChange: ((Settings) -> Settings) -> Unit, onBack: () -> Unit)
@Composable fun PermissionScreen(canAskAgain: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit)
@Composable fun CameraErrorContent(reason: CameraError, onRetry: () -> Unit)   // внутри MagnifierScreen
@Composable fun LupaTheme(content: @Composable () -> Unit)
object TestTags {
    const val ZOOM_INDICATOR = "zoomIndicator"; const val ZOOM_SLIDER = "zoomSlider"
    const val BTN_TORCH = "btnTorch"; const val BTN_FREEZE = "btnFreeze"
    const val BTN_FILTER = "btnFilter"; const val BTN_SNAPSHOT = "btnSnapshot"
    const val BTN_SETTINGS = "btnSettings"; const val CONTROLS = "controls"
    const val PREVIEW = "preview"; const val FROZEN_IMAGE = "frozenImage"
    const val FOCUS_RING = "focusRing"; const val FILTER_NAME = "filterName"; const val MESSAGE = "message"
    const val PERMISSION_SCREEN = "permissionScreen"; const val BTN_OPEN_SETTINGS = "btnOpenSettings"
    const val BTN_ASK_AGAIN = "btnAskAgain"; const val CAMERA_ERROR = "cameraError"; const val BTN_RETRY = "btnRetry"
    const val SETTINGS_SCREEN = "settingsScreen"
}
```

- `MainActivity` (единственная Activity, `screenOrientation="portrait"`, `enableEdgeToEdge()`). Создаёт `CameraXController`, `SettingsRepository.create`, `MediaStoreSnapshotSaver` и VM через фабрику. Решает, какой экран показать: разрешение → лупа ↔ настройки. Ловит `KEYCODE_VOLUME_UP/DOWN` в `onKeyDown` (на экране лупы → `viewModel.onVolumeKey`). Держит `FLAG_KEEP_SCREEN_ON`, пока на экране лупа и `keepScreenOn`.
- Тактильный отклик — `LocalView.current.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)`, если `settings.haptics`.

## Швы для тестов

1. `ZoomMath`, `FrozenMath`, `Filters.matrix` — чистые функции, JUnit.
2. `SettingsRepository` — на временном файле DataStore.
3. `MagnifierViewModel` — с `FakeCameraController` (в `app/src/test/.../camera/FakeCameraController.kt`: управляемый `status`, записывает вызовы, `capture()` отдаёт заданный Bitmap или бросает) и фейковым `SnapshotSaver`.
4. Экраны — Robolectric Compose-тесты по `TestTags`. `bindPreview` в тестах — пустая лямбда.

## Что построили завершённые таски

<!-- дописывается после каждого таска: что появилось, какие имена, что пригодится следующим -->

### Таск 01 — каркас

- Версии (в `gradle/libs.versions.toml`): AGP 8.13.2, Kotlin 2.2.21, Compose BOM 2025.12.01, CameraX 1.5.3, DataStore 1.1.7, activity-compose 1.11.0, lifecycle 2.9.4, Robolectric 4.16, coroutines-test 1.10.2. `debugImplementation` compose `ui-test-manifest` — нужен для `createComposeRule`.
- `dev.lupa.settings`: `LaunchZoomMode`, `ButtonSize`, `Settings`, `Remembered`, `SettingsRepository(dataStore)` {`settings`, `remembered`, `update`, `rememberZoom`, `rememberFilter`, `FIXED_ZOOM_CHOICES`, `create(context)`}, `launchZoom(settings, remembered)` — по этому файлу. **Временно** `launchZoom` берёт private `FIRST_LAUNCH_ZOOM = 2f`; таск 03 переключает его на `ZoomMath.DEFAULT_ZOOM`.
- `dev.lupa.filters.Filter` {NORMAL, HIGH_CONTRAST, GRAYSCALE, INVERT, YELLOW_ON_BLACK; `next()`} — без матриц (таск 06).
- `dev.lupa.ui`: `LupaTheme(content)`, `LupaColors` {`Background`, `OnBackground`, `Accent`, `OnAccent`, `Inactive`, `OnInactive`} — брать цвета отсюда; `PermissionScreen(canAskAgain, onRequest, onOpenSettings)`; `MagnifierPlaceholder()` (тег `preview`) — заменяет таск 02; `TestTags` — все константы.
- `MainActivity`: поток разрешения (запрос при запуске, `PermissionScreen` при отказе, проверка на `ON_RESUME`), `enableEdgeToEdge()`. Манифест: `allowBackup="false"`, `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` удалён.
- Подпись: `keystore.properties` в корне — `storeFile` (от корня проекта или абсолютный), `storePassword`, `keyAlias`, `keyPassword`; нет файла → debug-ключ + предупреждение.
- `tools/check_apk.py [--apk ПУТЬ] [--deps ФАЙЛ]` — размер ≤ 10 МБ, `uses-permission` = только CAMERA, нет запрещённых групп.
- Тесты: `app/src/test/java/dev/lupa/...` (Robolectric `@Config(sdk = [35])`). Первый прогон в свежем контейнере качает SDK-jar Robolectric (~200 МБ) с Maven Central — бывает 429, тогда просто повторить.
- Тема (после ремонта): все 48 ролей `ColorScheme` — из палитры. Акцент `#FFD600`: primary, secondary, tertiary, error, *Fixed, **primaryContainer**. `#CC2A2A2A`: secondaryContainer (неактивная дорожка Slider), tertiaryContainer, surfaceVariant, surfaceContainer/High/Highest (дорожка выключенного Switch, диалоги), surfaceBright, outlineVariant. outline — белый. Внимание таску 07: у включённого Switch ручка в состоянии нажатия/фокуса берётся из primaryContainer (жёлтая на жёлтой дорожке) — задать цвета Switch явно через `SwitchDefaults.colors(...)`.

### Таск 02 — камера

- `dev.lupa.camera`: `CameraController`, `CameraStatus`, `CameraCapabilities`, `CameraError`, `CaptureException` — ровно по этому файлу; `CameraXController(context: Context) : CameraController`. `ERROR_OTHER_RECOVERABLE_ERROR` отображается как `Starting` (CameraX повторяет сам).
- `dev.lupa.ui`: `MagnifierUiState(camera = Starting, minZoom = 1f, maxZoom = 2f, hasFlash = false)` — остальные поля из этого файла добавляют таски 03+. `MagnifierViewModel(val camera: CameraController)` {`state`, `onRetry()`} — **`camera` публичный**: Activity берёт контроллер из VM, чтобы после пересоздания привязать превью к тому же контроллеру; таски 03+ расширяют конструктор (`settingsRepository`, `saver`). `maxZoom` пока через временную private `SOFT_MAX = 2f` в VM → таск 03 заменяет на `ZoomMath.maxTotal`.
- `@Composable MagnifierScreen(state, viewModel, bindPreview: (PreviewView) -> Unit)` — `onOpenSettings` добавит таск 07. `@Composable CameraErrorContent(reason, onRetry)`.
- `MainActivity`: `@VisibleForTesting internal var MainActivity.cameraControllerFactory: (Context) -> CameraController` — точка подмены камеры в тестах.
- Тест-фейк `app/src/test/java/dev/lupa/camera/FakeCameraController(initial)` {`emit(status)`, `bindCalls`, `retryCount`, `zoomRatios`, `torchCalls`, `focusCalls`, `focusResult`, `captureBitmap`, `captureError`, `captureCount`}.
- Строки: `camera_error_in_use`, `camera_error_disabled`, `camera_error_not_found`, `camera_error_failed`, `camera_retry`.

### Таск 03 — зум

- `dev.lupa.zoom.ZoomMath` и `ZoomSplit` — ровно по этому файлу. `launchZoom` теперь берёт `ZoomMath.DEFAULT_ZOOM`.
- `MagnifierUiState` + `zoom`, `softScale`, `settings` (вместе с `camera`, `minZoom`, `maxZoom`, `hasFlash`).
- `MagnifierViewModel(camera, settingsRepository)` + `onSliderChange(position)`, `onPinch(zoomFactor, centroidX, centroidY, panX, panY)`, `onDoubleTap()`, `onVolumeKey(up): Boolean`. Зум пишется `rememberZoom` с задержкой 300 мс; при запуске — `launchZoom`, прижатый после первого `Ready`; на каждом `Ready` зум камеры применяется заново.
- `MagnifierScreen(state, viewModel, bindPreview)`: индикатор `zoomIndicator` (statusBars ∪ displayCutout), ползунок `zoomSlider` — своя невидимая ручка 64 dp, отступ по бокам 10 dp; контейнер **`controls`** — внизу, по `navigationBars`, сюда таск 04 добавляет ряд кнопок под ползунком. Строка `zoom_slider_description`.
- `MainActivity`: `onKeyDown`/`onKeyUp` для громкости → `viewModel.onVolumeKey`; фабрика VM берёт `SettingsRepository.create(applicationContext)`.
- Тесты: `app/src/test/java/dev/lupa/ui/TestSettingsRepository.kt` — репозиторий на временном файле для VM/UI-тестов.

### Таск 04 — панель, фонарик, фокус

- `MagnifierUiState` + `torchOn`, `focusPoint: Pair<Float, Float>?`.
- `MagnifierViewModel` + `onTorchClick()`, `onTap(x, y)`, `onViewSize(w, h)`; `onFreezeClick`/`onFilterClick`/`onSnapshotClick` — **пустые**, наполняют таски 05–06. На первом `Ready` фонарик включается по `settings.torchOnLaunch`; на каждом `Ready` применяются желаемые зум и фонарик. Кружок фокуса гаснет по возврату `focusAt`, не позднее 1,5 с. **Таск 05:** `onTap` должен ничего не делать на стоп-кадре.
- `ControlPanel(state, viewModel, modifier, metrics: ButtonMetrics = ButtonMetrics.Normal)` в `ui/ControlPanel.kt`; `ButtonMetrics(height, icon, label)`: `Normal` = 80 dp / 36 dp / 16 sp; `Large` сейчас 104/48/20 — **таск 07 выставляет 96 dp / 44 dp / 18 sp по спецификации** и подключает настройку. Активная кнопка — `selected` в semantics + заливка `Accent`/`OnAccent`; неактивная — `Inactive`/`OnInactive`.
- `MagnifierScreen(state, viewModel, bindPreview, buttonMetrics = ButtonMetrics.Normal)`. `TestTags.iconOf(buttonTag)` — тег иконки кнопки.
- Иконки: `res/drawable/ic_torch.xml`, `ic_freeze.xml`, `ic_filter.xml`, `ic_camera.xml`. Строки: `button_torch`, `button_freeze`, `button_filter`, `button_snapshot`.
- Тактильный отклик ещё не подключён — таск 07.

### Таск 05 — стоп-кадр

- `dev.lupa.snapshot`: `FrozenViewport`, `IntRectSpec`, `FrozenMath.transform/withScale/visibleRect/centerCrop` — по этому файлу.
- `dev.lupa.ui`: `FrozenFrame(bitmap, baseZoom, viewport)`, `enum Busy {NONE, FREEZING, SAVING}`, `sealed UiMessage { CaptureFailed }` — **таск 06 добавляет `FilterName`, `Saved`, `SaveFailed`**. `MagnifierUiState` + `frozen`, `busy`, `message`, вычисляемое `indicatorZoom`. VM + `onMessageShown()`; `onFreezeClick` работает; пока `FREEZING`, живой зум не меняется; на стоп-кадре `onTap` не фокусирует, ползунок/громкость/щипок/двойной тап работают с `viewport`.
- Экран: стоп-кадр — `frozenImage` с `ContentScale.FillBounds` и масштабом/смещением из `viewport`. `PanelButton` имеет параметр `busy` (крутилка вместо иконки). Сообщения: текст и длительность — выражения `when` в `MagnifierScreen` (`textRes`/`durationMillis`), сообщение стоит над блоком управления, UI снимает его таймером и вызывает `onMessageShown()` — **таск 06 дополняет эти `when`** (название фильтра — по центру, крупно).
- Строка `message_capture_failed`. Тест-фейк: `FakeCameraController.captureGate: CompletableDeferred<Unit>?` — придержать `capture()` в тесте.

### Таск 06 — фильтры и снимок

- `dev.lupa.filters.Filters` {`matrix`, `applyTo(View)`, `colorFilter`, `applyTo(Bitmap)`} — по этому файлу. Матрицы: контраст ×2 вокруг 128; Ч/Б 0.299/0.587/0.114; инверсия; «Жёлтый на чёрном» = 1.6·(255 − яркость) − 76.8 в R и G, B = 0.
- `dev.lupa.snapshot`: `interface SnapshotSaver { suspend fun save(Bitmap): Result<Uri> }`, `MediaStoreSnapshotSaver(context)`.
- `UiMessage` + `FilterName(filter)`, `Saved`, `SaveFailed`. `MagnifierUiState` + `filter`. **`MagnifierViewModel(camera, settingsRepository, saver, workDispatcher: CoroutineDispatcher = Dispatchers.Default)`** — обрезка и фильтр большого Bitmap вне главного потока; тесты подставляют свой диспетчер. Живой зум заморожен на время `FREEZING` и `SAVING`.
- Строки: `message_saved`, `message_save_failed`, `filter_normal`, `filter_high_contrast`, `filter_grayscale`, `filter_invert`, `filter_yellow_on_black`.
- Тест-фейк `app/src/test/java/dev/lupa/ui/FakeSnapshotSaver.kt`.
