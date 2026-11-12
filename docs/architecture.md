# Архитектура

Один Gradle-модуль `app`, пакет `dev.lupa`. Kotlin, Compose (Material 3), CameraX, DataStore. Подробности «почему» — в `docs/adr/`, словарь — в `CONTEXT.md`.

## Поток данных

- Камера → VM → экран: `CameraXController` (`camera`) держит `StateFlow<CameraStatus>` (`Starting` / `Ready(capabilities)` / `Error`). `MagnifierViewModel` (`ui`) складывает его с настройками в `MagnifierUiState`; `MagnifierScreen` рисует состояние и шлёт события (`onPinch`, `onTap`, `onFreezeClick` …) обратно в VM. VM не знает про View и Compose: координаты получает числами.
- `PreviewView` создаётся в экране и привязывается через `bindPreview` → `CameraController.bind()`; режим всегда `COMPATIBLE`. При возврате из настроек `PreviewView` пересоздаётся и камера привязывается заново.
- Зум: VM хранит желаемый итоговый зум; `ZoomMath.split` делит его на зум камеры (`setZoomRatio`) и программный масштаб превью (`softScale` ≤ ×2). На каждый `Ready` VM заново применяет зум и фонарик.
- Фильтр: `Filters.applyTo(view, filter)` на `PreviewView`, `Filters.colorFilter` на стоп-кадре, `Filters.applyTo(bitmap, filter)` при сохранении; матрицы — одна таблица в `Filters.matrix`.
- Настройки: `SettingsRepository` (DataStore `settings`) отдаёт `settings` и `remembered` (последний зум и фильтр). Зум при запуске — `launchZoom(settings, remembered)`; изменения зума и фильтра пишутся с задержкой 300 мс (`REMEMBER_DELAY_MS`). Экран настроек меняет их через `updateSettings`.
- Стоп-кадр и снимок: `onFreezeClick` → `camera.capture()` (ImageCapture, обрезка по ViewPort) → `FrozenFrame(bitmap, baseZoom, viewport)`; пока `Busy.FREEZING`/`SAVING` живой зум не меняется. Жесты на стоп-кадре считает `FrozenMath` (масштаб до ×8). `onSnapshotClick` кадрирует видимую часть и красит фильтром на `workDispatcher`, `SnapshotSaver` (`MediaStoreSnapshotSaver`) пишет JPEG в Pictures/Lupa.

## Границы модулей

| Пакет | Владеет | Скрывает / правило |
|---|---|---|
| `dev.lupa.camera` | CameraX, зум камеры, вспышка, фокус, съёмка | CameraX не импортируется нигде, кроме `PreviewView` в экране; `ImageAnalysis` только считает кадры (лог `Lupa`) |
| `dev.lupa.zoom` | `ZoomMath` | чистый Kotlin, без Android |
| `dev.lupa.filters` | `Filter`, `Filters` | единая таблица матриц; применение к View / Compose / Bitmap |
| `dev.lupa.settings` | `Settings`, `Remembered`, `SettingsRepository` | один DataStore на процесс |
| `dev.lupa.snapshot` | `FrozenViewport`, `FrozenMath`, `SnapshotSaver` | чистая математика + запись в MediaStore |
| `dev.lupa.ui` | экраны, тема, `MagnifierViewModel`, `TestTags` | состояние только в VM |
| `MainActivity` | сборка зависимостей, разрешение, навигация | лупа ↔ настройки — простое состояние; клавиши громкости → `onVolumeKey`; подмена камеры в тестах — `cameraControllerFactory` |

## Соглашения

- Имена в коде английские, тексты для пользователя — русские, только в `res/values/strings.xml`.
- Тема всегда тёмная: фон чёрный, акцент `#FFD600`; цвета брать из `LupaColors` (`ui/Theme.kt`).
- Иконки — свои векторные drawable в `res/drawable`; тактильный отклик — `rememberHaptic` (`ui/Haptics.kt`).
- Права: только `CAMERA`; ключи и `keystore.properties` не коммитятся.
- Тесты: чистая математика — JUnit; VM — с `FakeCameraController` и `FakeSnapshotSaver`; репозиторий — на временном файле (`TestSettingsRepository`); экраны — Robolectric + `createComposeRule`, поиск по `TestTags`.
