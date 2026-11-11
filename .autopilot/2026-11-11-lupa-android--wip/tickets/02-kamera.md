# 02 — Камера: живое превью на весь экран и ошибки камеры

**Требования:** R01, R10, R12, R22, R59, R62, R63, R68
**Зависит от:** 01
**Зона:** `app/src/main/java/dev/lupa/camera/` · `app/src/main/java/dev/lupa/ui/` · `app/src/main/java/dev/lupa/MainActivity.kt` · `app/src/main/res/values/strings.xml` · `app/src/test/`
**Волна:** 2
**Ревью:** да — фундамент
**Модель:** сильная

## Что должно заработать

После выдачи разрешения на весь экран сразу идёт живая картинка с основной задней камеры: без полей и под системными панелями тоже. `CameraXController` реализует `CameraController` из `interfaces.md` целиком: привязку Preview, ImageCapture и ImageAnalysis с общим `ViewPort`, состояние камеры, зум камеры, фонарик, фокус в точке и снимок. Это фундамент для всех следующих тасков.

Если камера занята другим приложением, вместо картинки видно понятное сообщение и кнопку «Повторить». Так же обрабатываются отсутствие камеры, запрет политикой и сбой. Свернул приложение — камера освободилась, вернулся — снова пришёл `Ready`.

Появляется `MagnifierViewModel` с `MagnifierUiState` (пока: камера, диапазон зума, наличие вспышки) и `MagnifierScreen` с превью. Кнопок ещё нет.

## Из брифа, дословно

> «Нужна лупа на камере телефона»
> «Камера CameraX (Preview, ImageCapture, ImageAnalysis)»
> «Один экран, портретная ориентация, превью камеры на весь экран.»
> «Ошибка камеры (занята другим приложением) — понятное сообщение и кнопка «Повторить».»
> «При сворачивании камера и фонарик освобождаются»
> «Холодный старт до превью — не дольше 1,5 секунды.»
> «После установки APK и разрешения камеры лупа открывается сразу, без промежуточных экранов»
> «Модель телефона и версия Android: Redmi Note 13 Pro 5G, Android 16 (HyperOS).»
> «Камера 200 Мп. Стоп-кадр снимать не в полном разрешении, а около 12 Мп через `ResolutionSelector`» · «максимум брать из `maxZoomRatio` устройства, не хардкодить»

## Разделы спецификации

Истории 1, 2, 46 (освобождение камеры), 48, 49, 50, 57. «Решения по реализации»: превью в режиме COMPATIBLE, ImageCapture/ResolutionSelector/ViewPort, ImageAnalysis, состояние экрана. `interfaces.md`: `dev.lupa.camera`, `MagnifierViewModel` (часть про `Ready`), `MagnifierScreen`, швы.

## Критерии приёмки

- [ ] `CameraController`, `CameraStatus`, `CameraCapabilities`, `CameraError`, `CaptureException` — ровно по `interfaces.md`. `CameraXController` привязывает к lifecycle Activity одну `UseCaseGroup`: Preview, ImageCapture (`ResolutionSelector` около 4000×3000, 4:3, `CAPTURE_MODE_MINIMIZE_LATENCY`) и ImageAnalysis (≤ 640×480, `KEEP_ONLY_LATEST`) с `ViewPort` от `PreviewView`. `PreviewView` в режиме `COMPATIBLE`, масштаб `FILL_CENTER`.
- [ ] ImageAnalysis только считает кадры: в Logcat с тегом `Lupa` пишется время от старта процесса до первого кадра и раз в 5 с — fps; кадр закрывается сразу.
- [ ] `status`: `Starting` → `Ready(capabilities)`, где `maxZoom` из `ZoomState.maxZoomRatio`, `minZoom` из `minZoomRatio`, `hasFlash` из `CameraInfo.hasFlashUnit()`. Ошибки `CameraState` и исключения привязки отображаются в `CameraError`: `IN_USE` (камера занята / максимум камер), `DISABLED`, `NOT_FOUND` (нет задней камеры), `FAILED` (прочее). После переподключения (`ON_START` после `ON_STOP`, `retry()`) снова приходит `Ready`.
- [ ] `capture()` возвращает Bitmap, повёрнутый по `rotationDegrees` и обрезанный по `cropRect` (видимая область превью); ошибка — `CaptureException`. `focusAt` — `FocusMeteringAction` AF+AE с автоотменой 5 с, точка из `previewView.meteringPointFactory`. `setZoomRatio` прижимает значение к диапазону устройства.
- [ ] Камера подключается сразу при показе экрана лупы, не дожидаясь чтения настроек; до первого кадра на главном потоке нет тяжёлой работы.
- [ ] `MagnifierScreen`: превью на весь экран (тег `preview`) под системными панелями; при `CameraStatus.Error` поверх него `CameraErrorContent` (тег `cameraError`) с текстом по причине и кнопкой «Повторить» (`btnRetry`) → `viewModel.onRetry()` → `camera.retry()`. Тексты: `IN_USE` — «Камера занята другим приложением. Закройте его и нажмите «Повторить».»; `DISABLED` — «Камера отключена настройками телефона.»; `NOT_FOUND` — «На телефоне не найдена основная камера.»; `FAILED` — «Не удалось запустить камеру.».
- [ ] `FakeCameraController` в `app/src/test` (управляемый `status`, запись вызовов `setZoomRatio`/`enableTorch`/`focusAt`, `capture()` отдаёт заданный Bitmap или бросает). Тесты: VM отражает `Starting`/`Ready`/`Error`; `onRetry()` вызывает `retry()`; Compose-тест — ошибка `IN_USE` показывает нужный текст и кнопку, нажатие вызывает retry.
- [ ] `MainActivity` после выдачи разрешения показывает `MagnifierScreen` с настоящим `CameraXController` (VM через фабрику); заготовка из таска 01 заменена.
