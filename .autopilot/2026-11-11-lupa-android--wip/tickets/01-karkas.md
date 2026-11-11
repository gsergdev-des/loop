# 01 — Каркас: проект, тема, разрешение камеры, хранилище настроек, проверка

**Требования:** R02, R03, R04, R07, R08, R09, R11, R14, R15, R16, R34, R56, R57, R61, R65, R69, R71, R72i
**Зависит от:** —
**Зона:** `settings.gradle.kts` · `build.gradle.kts` · `gradle/` · `gradlew*` · `gradle.properties` · `app/build.gradle.kts` · `app/proguard-rules.pro` · `app/src/main/AndroidManifest.xml` · `app/src/main/res/` · `app/src/main/java/dev/lupa/MainActivity.kt` · `app/src/main/java/dev/lupa/settings/` · `app/src/main/java/dev/lupa/ui/` · `app/src/test/` · `tools/check_apk.py`
**Волна:** 1
**Ревью:** да — фундамент
**Модель:** сильная

## Что должно заработать

В репозитории появляется Android-проект, который собирается одной командой в подписанный release-APK. Приложение называется «Лупа», всегда тёмное и всегда портретное. При первом запуске оно запрашивает доступ к камере; это единственное разрешение. Если отказать, появляется экран с объяснением и кнопкой «Открыть настройки», а когда система ещё позволяет спросить снова — и с кнопкой «Разрешить». Когда доступ выдан, в том числе в системных настройках с возвратом, открывается экран лупы. Пока это чёрный экран-заготовка с тегом `preview`, камеру подключит таск 02.

Готово хранилище настроек `SettingsRepository` на DataStore со всеми полями из `interfaces.md` и функцией `launchZoom`. Тесты (JUnit и Robolectric) запускаются, а `tools/check_apk.py` сторожит размер APK, разрешения и запрещённые библиотеки.

## Из брифа, дословно

> «Сборка Gradle, один модуль `app`, подписанный release-APK»
> «Язык Kotlin» · «UI Jetpack Compose, Material 3» · «Хранение настроек DataStore Preferences» · «minSdk / targetSdk 29 / 36 (Android 16)»
> «Сторонних SDK (реклама, аналитика, Firebase) нет. Разрешение INTERNET в манифесте не объявляется.»
> «Единственное разрешение — CAMERA. Запрос при первом запуске; при отказе — экран с объяснением и кнопкой «Открыть настройки».»
> «Запуск — сразу в режим лупы, без заставок и меню.» · «Тёмная тема по умолчанию.» · «Ориентация зафиксирована портретной.»
> «Размер APK — до 10 МБ.» · «Делать по этапам, после каждого — сборка APK и проверка на телефоне.»

## Разделы спецификации

Истории 2–7, 23, 47, 51–54, 56, 58. «Решения по реализации»: сборка, один модуль, тема запуска, иконки, чистка манифеста, подпись, тесты. `interfaces.md`: правила проекта, `dev.lupa.settings`, `TestTags`, `LupaTheme`, `PermissionScreen`, швы для тестов.

## Критерии приёмки

- [ ] Gradle Wrapper 8.14.3 и версии из `interfaces.md` в `gradle/libs.versions.toml`. `./gradlew --no-daemon testDebugUnitTest assembleRelease` проходит; в проекте один модуль `app`, пакет и applicationId `dev.lupa`; minSdk 29, targetSdk и compileSdk 36. Все зависимости из «Решений по реализации» подключены сразу: Compose BOM + Material 3, activity-compose, lifecycle (runtime-compose, viewmodel-compose), CameraX (core, camera2, lifecycle, view), DataStore Preferences, тестовые JUnit 4, Robolectric 4.16, compose ui-test-junit4, kotlinx-coroutines-test.
- [ ] Release-сборка с R8 и `shrinkResources`. Есть `keystore.properties` в корне — подпись этим ключом; нет — подпись debug-ключом и предупреждение в логе Gradle. `keystore.properties`, `*.jks`, `*.keystore` в `.gitignore` (уже есть — не ломать).
- [ ] `python3 tools/check_apk.py` проходит на собранном APK и **падает**, если: APK больше 10 МБ; в итоговом манифесте `uses-permission` не ровно `android.permission.CAMERA` (в том числе если появился `INTERNET` или `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`); в `releaseRuntimeClasspath` есть группы `com.google.firebase`, `com.google.android.gms`, `com.google.ads`, `com.squareup.okhttp3`, `io.sentry`. Манифест читается через `aapt2 dump xmltree` или `apkanalyzer` из `/opt/android-sdk` (путь из `ANDROID_HOME` или `local.properties`).
- [ ] Манифест: одна Activity `MainActivity` с `screenOrientation="portrait"`; тема запуска с чёрным фоном и прозрачной иконкой системного splash; название «Лупа»; векторная адаптивная иконка-лупа.
- [ ] `LupaTheme` — всегда тёмная схема Material 3 (фон чёрный, акцент `#FFD600`, на акценте чёрный), независимо от системной темы; `enableEdgeToEdge()` в `MainActivity`.
- [ ] Поток разрешения. Нет разрешения → системный запрос сразу при запуске. Отказ → `PermissionScreen` (тег `permissionScreen`): объяснение «Лупе нужна камера, чтобы увеличивать изображение. Без доступа к камере она не работает.», кнопка «Открыть настройки» (`btnOpenSettings`) ведёт на страницу приложения в системных настройках, кнопка «Разрешить» (`btnAskAgain`) видна только при `shouldShowRequestPermissionRationale`. Разрешение проверяется на каждом `ON_RESUME`: выдано → экран лупы-заготовка (тег `preview`) без перезапуска. Robolectric-тесты на экран и на переключение.
- [ ] `SettingsRepository`, `Settings`, `Remembered`, `LaunchZoomMode`, `ButtonSize`, `FIXED_ZOOM_CHOICES`, `launchZoom` — ровно по `interfaces.md`, плюс `enum class Filter` с `next()` в `dev.lupa.filters` (матрицы — таск 06). Тесты на временном файле: значения по умолчанию (фонарик при запуске выкл., громкость вкл., «последний», ×2, не гасить экран вкл., вибрация вкл., обычные кнопки); запись и чтение каждого поля; `launchZoom` для FIXED, для LAST с сохранённым зумом и для LAST без него (→ ×2).
- [ ] `TestTags` со всеми константами из `interfaces.md`; все тексты — в `res/values/strings.xml`.
- [ ] `app/src/test/.../camera/FakeCameraController.kt` пока не нужен (появится в 02), но тестовая инфраструктура Robolectric + Compose работает: есть хотя бы один зелёный Compose-тест.
