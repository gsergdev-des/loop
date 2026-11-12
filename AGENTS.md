<!-- autopilot:start -->
# Лупа для Android

Офлайн-лупа на камере телефона без рекламы: зум, фонарик, стоп-кадр, фильтры — крупные кнопки под большой палец; для личного использования.

## Команды

| Команда | Что делает |
|---------|------------|
| `ANDROID_HOME=/opt/android-sdk ./gradlew --no-daemon testDebugUnitTest assembleRelease && python3 tools/check_apk.py` | Полная проверка (она же — критерий «зелёно»): 166 тестов, release-APK, размер ≤ 10 МБ, из прав только CAMERA, нет запрещённых зависимостей |
| `ANDROID_HOME=/opt/android-sdk ./gradlew assembleRelease` | Сборка → `app/build/outputs/apk/release/app-release.apk` (с пустым кэшем ~6 мин) |
| `./gradlew --no-daemon testDebugUnitTest --tests 'dev.lupa.zoom.ZoomMathTest'` | Один тест-класс |
| `/opt/android-sdk/build-tools/36.0.0/aapt2 dump permissions app/build/outputs/apk/release/app-release.apk` | Права в APK; подпись: `/opt/android-sdk/build-tools/36.0.0/apksigner verify --print-certs <apk>` |
| `adb install -r app/build/outputs/apk/release/app-release.apk` | Установка на телефон (на устройстве не проверялось — устройства в среде нет) |
| `adb logcat -s Lupa` | Логи: время до первого кадра и fps раз в 5 с |

Требования: JDK 17+, Android SDK (platform 36, build-tools 36.0.0), больше ничего вне Gradle.

## Где что

- `app/src/main/java/dev/lupa/MainActivity.kt` — единственная Activity: разрешение, лупа ↔ настройки, клавиши громкости, сборка VM.
- `app/src/main/java/dev/lupa/ui/MagnifierViewModel.kt` — всё состояние лупы; `MagnifierScreen.kt` / `ControlPanel.kt` — экран и кнопки.
- `app/src/main/java/dev/lupa/camera/CameraXController.kt` — единственное место, где живёт CameraX.
- `app/src/main/java/dev/lupa/zoom/ZoomMath.kt`, `snapshot/FrozenMath.kt`, `filters/Filters.kt` — чистая математика, меняется чаще всего и покрыта тестами.
- `app/src/main/java/dev/lupa/ui/TestTags.kt` — теги для UI-тестов; `app/src/main/res/values/strings.xml` — все тексты (по-русски).
- `app/src/test/java/dev/lupa/camera/FakeCameraController.kt` — фейк камеры для тестов VM и экранов.
- `app/build.gradle.kts`, `gradle/libs.versions.toml` — сборка, подпись, версии; `tools/check_apk.py` — проверка APK.
- Архитектура и соглашения — `docs/architecture.md`; решения «почему так» — `docs/adr/`; словарь — `CONTEXT.md`.

## Подводные камни

- Свежее окружение без `~/.gradle` и `~/.m2`: первый запуск Robolectric-тестов качает большие jar (android-all-instrumented ~200 МБ, nativeruntime-dist-compat) с Maven Central. Бывает 429 или отказ прокси на зеркале gcp.mirrors.maven.org — повторить или прогреть кэш. Release-сборка от этого не зависит.
- Версии стека зафиксированы (AGP 8.13.x, Kotlin 2.2.x, Gradle 8.14.3, `docs/adr/0005`): AGP 9 и Kotlin 2.3+ не брать.
- `PreviewView` только в режиме `COMPATIBLE` (TextureView), иначе не работают фильтры и программный зум (`docs/adr/0002`).
- Запрещены `INTERNET`, Firebase, Play Services, аналитика, `material-icons-extended`, navigation-compose. `androidx.core` подмешивает `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — он убран в манифесте через `tools:node="remove"`; `check_apk.py` упадёт, если появится лишнее право.
- Без `keystore.properties` release подписывается debug-ключом (в логе предупреждение) — это штатно.
- Robolectric-тесты: `@Config(sdk = [35])`, `@GraphicsMode(NATIVE)` там, где нужен Bitmap; элементы искать по `TestTags`, не по координатам.
- Итоговый зум = зум камеры × программный (до ×2); `Ready` приходит после каждого переподключения камеры, и VM заново применяет зум и фонарик.

## Окружение

- `ANDROID_HOME` или `sdk.dir` в `local.properties` (в `.gitignore`) — путь к Android SDK; здесь `/opt/android-sdk`.
- `keystore.properties` в корне (в `.gitignore`, как `*.jks` и `*.keystore`) — свой ключ подписи: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. Секретов в репозитории нет.


## Как здесь работает Autopilot

Сборка ведётся навыком `/sdev`. Требования, спецификация и таски — в `.autopilot/`.
Прогресс — `.autopilot/dashboard.html`. Требование из `manifest.md` может снять
только пользователь. Прерванную сборку продолжает «продолжи автопилот».

### Правка без автопилота

Мелкий баг или доводка после сборки — обычная сессия, без прогона.

Сначала прочитай:
1. `.autopilot/PROGRESS.md` — что не доделано, известные баги, о чём договорились с пользователем.
2. Что правили после последней сборки — первая строка таблицы в «История git».
3. Только если правка их касается: почему сделано так — `docs/adr/`; что значат слова — `CONTEXT.md`;
   что именно просили — `manifest.md` в папке сборки (указана в начале `PROGRESS.md`).

После правки:
- коммит — по правилам из «История git»;
- строка в «Правки без автопилота» в `PROGRESS.md`: дата — что — почему;
- починенный пункт из «Не доделано» или «Известные баги» — удали;
- договорились с пользователем о том, что следующая сборка не должна пересматривать, —
  строка в «Договорились», его словами.

### История git

Целиком не читай — бери срез под вопрос:

| Вопрос | Команда |
|---|---|
| что правили после последней сборки | `git log --oneline "$(git log -1 --format=%h --grep='^Autopilot: сборка сдана')"..HEAD -- . ':(exclude).autopilot'` |
| всё, что делали ради требования | `git log --oneline --grep="R02"` |
| история файла | `git log --oneline -10 -- <файл>` |
| когда появился или исчез код | `git log --oneline -S "<фрагмент>"` |
| почему строка такая | `git blame -L <с>,<по> <файл>`, затем `git show <sha>` |
| что внутри коммита | `git show --stat <sha>` |

Срез длиннее ~30 коммитов — отдай его субагенту и возьми выжимку в десять строк.

Коммит: заголовок — что (`fix: даты в одном формате`), тело — почему, трейлер
`--trailer "Requirements: R02"`, если правка касается требования из `manifest.md`.
Заголовок `Autopilot: сборка сдана` пишет только автопилот — по нему находят последнюю сборку.
<!-- autopilot:end -->
