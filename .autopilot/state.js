window.STATE =
{
  "version": 2,
  "slug": "lupa-android",
  "dir": "2026-11-11-lupa-android--wip",
  "title": "Лупа для Android",
  "mode": "full",
  "depth": "normal",
  "tier": "T2",
  "briefFile": "2026-11-11-brief.md",
  "memoryFile": "AGENTS.md",
  "memoryOwner": "autopilot",
  "skillDir": "/root/.claude/skills/synced/ecd4b8a9-0891-4714-86fc-742e549f5afe_821d0aa3-3ec0-4431-98b4-45e615589ff0/sdev",
  "baseCommit": "273164a",
  "startedAt": "2026-11-11T19:44:15+00:00",
  "updatedAt": "2026-11-11T22:04:01+00:00",
  "finishedAt": null,
  "stages": [
    {
      "id": "preflight",
      "status": "done",
      "startedAt": "2026-11-11T19:44:15+00:00",
      "finishedAt": "2026-11-11T19:46:46+00:00"
    },
    {
      "id": "manifest",
      "status": "done",
      "startedAt": "2026-11-11T19:46:46+00:00",
      "finishedAt": "2026-11-11T19:48:38+00:00"
    },
    {
      "id": "briefing",
      "status": "skipped",
      "startedAt": "2026-11-11T19:48:38+00:00",
      "finishedAt": "2026-11-11T19:50:29+00:00",
      "note": "полный автомат — самобрифинг"
    },
    {
      "id": "spec",
      "status": "done",
      "startedAt": "2026-11-11T19:50:32+00:00",
      "finishedAt": "2026-11-11T19:57:32+00:00"
    },
    {
      "id": "plan",
      "status": "done",
      "startedAt": "2026-11-11T19:57:32+00:00",
      "finishedAt": "2026-11-11T20:00:42+00:00"
    },
    {
      "id": "build",
      "status": "active",
      "startedAt": "2026-11-11T20:00:42+00:00"
    },
    {
      "id": "review",
      "status": "pending"
    },
    {
      "id": "final",
      "status": "pending"
    }
  ],
  "requirements": {
    "total": 74,
    "done": 62,
    "inTicket": 12,
    "inSpec": 0,
    "placeholder": 0,
    "deferred": 0,
    "dropped": 0
  },
  "tickets": [
    {
      "id": "01",
      "title": "Каркас: проект, тема, разрешение камеры, хранилище настроек, проверка",
      "requirements": [
        "R02",
        "R03",
        "R04",
        "R07",
        "R08",
        "R09",
        "R11",
        "R14",
        "R15",
        "R16",
        "R34",
        "R56",
        "R57",
        "R61",
        "R65",
        "R69",
        "R71",
        "R72i"
      ],
      "blockedBy": [],
      "wave": 1,
      "zone": [
        "settings.gradle.kts",
        "build.gradle.kts",
        "gradle/",
        "gradlew*",
        "gradle.properties",
        "app/build.gradle.kts",
        "app/proguard-rules.pro",
        "app/src/main/AndroidManifest.xml",
        "app/src/main/res/",
        "app/src/main/java/dev/lupa/MainActivity.kt",
        "app/src/main/java/dev/lupa/settings/",
        "app/src/main/java/dev/lupa/ui/",
        "app/src/test/",
        "tools/check_apk.py"
      ],
      "review": "да — фундамент",
      "model": "сильная",
      "status": "done",
      "retries": 0,
      "repairs": 1,
      "startedAt": "2026-11-11T20:00:50+00:00",
      "repairFindings": [
        "LupaColorScheme: outline не жёлтый, роли неактивных состояний и контейнеров из палитры проекта"
      ],
      "finishedAt": "2026-11-11T20:42:27+00:00",
      "tests": {
        "passed": 24,
        "failed": 0
      },
      "commit": "3fb665f"
    },
    {
      "id": "02",
      "title": "Камера: живое превью на весь экран и ошибки камеры",
      "requirements": [
        "R01",
        "R10",
        "R12",
        "R22",
        "R59",
        "R62",
        "R63",
        "R68"
      ],
      "blockedBy": [
        "01"
      ],
      "wave": 2,
      "zone": [
        "app/src/main/java/dev/lupa/camera/",
        "app/src/main/java/dev/lupa/ui/",
        "app/src/main/java/dev/lupa/MainActivity.kt",
        "app/src/main/res/values/strings.xml",
        "app/src/test/"
      ],
      "review": "да — фундамент",
      "model": "сильная",
      "status": "done",
      "retries": 0,
      "repairs": 0,
      "startedAt": "2026-11-11T20:42:35+00:00",
      "finishedAt": "2026-11-11T21:12:49+00:00",
      "tests": {
        "passed": 33,
        "failed": 0
      },
      "commit": "a52c467"
    },
    {
      "id": "03",
      "title": "Зум: ползунок, щипок, индикатор, двойной тап, кнопки громкости",
      "requirements": [
        "R17",
        "R19",
        "R24",
        "R25",
        "R29",
        "R30",
        "R31",
        "R35",
        "R36",
        "R37",
        "R52",
        "R74i"
      ],
      "blockedBy": [
        "02"
      ],
      "wave": 3,
      "zone": [
        "app/src/main/java/dev/lupa/zoom/",
        "app/src/main/java/dev/lupa/ui/",
        "app/src/main/java/dev/lupa/MainActivity.kt",
        "app/src/main/res/values/strings.xml",
        "app/src/test/"
      ],
      "review": "нет",
      "model": "обычная",
      "status": "done",
      "retries": 0,
      "repairs": 0,
      "startedAt": "2026-11-11T21:12:50+00:00",
      "finishedAt": "2026-11-11T21:26:57+00:00",
      "tests": {
        "passed": 68,
        "failed": 0
      },
      "commit": "21146d7"
    },
    {
      "id": "04",
      "title": "Нижняя панель из 4 кнопок, фонарик, фокус по тапу",
      "requirements": [
        "R05",
        "R17",
        "R20",
        "R23",
        "R26",
        "R27",
        "R32",
        "R38",
        "R39",
        "R46",
        "R47",
        "R48",
        "R49",
        "R60",
        "R67"
      ],
      "blockedBy": [
        "03"
      ],
      "wave": 4,
      "zone": [
        "app/src/main/java/dev/lupa/ui/",
        "app/src/main/res/",
        "app/src/test/"
      ],
      "review": "нет",
      "model": "обычная",
      "status": "done",
      "retries": 0,
      "repairs": 0,
      "startedAt": "2026-11-11T21:26:57+00:00",
      "finishedAt": "2026-11-11T21:34:43+00:00",
      "tests": {
        "passed": 90,
        "failed": 0
      },
      "commit": "dab1249"
    },
    {
      "id": "05",
      "title": "Стоп-кадр: чёткий снимок, увеличение до ×8 и перетаскивание",
      "requirements": [
        "R18",
        "R40",
        "R41",
        "R42"
      ],
      "blockedBy": [
        "04"
      ],
      "wave": 5,
      "zone": [
        "app/src/main/java/dev/lupa/snapshot/",
        "app/src/main/java/dev/lupa/ui/",
        "app/src/main/res/values/strings.xml",
        "app/src/test/"
      ],
      "review": "нет",
      "model": "сильная",
      "status": "done",
      "retries": 0,
      "repairs": 0,
      "startedAt": "2026-11-11T21:34:43+00:00",
      "finishedAt": "2026-11-11T21:52:00+00:00",
      "tests": {
        "passed": 123,
        "failed": 0
      },
      "commit": "e1cb4b8"
    },
    {
      "id": "06",
      "title": "Фильтры на превью и стоп-кадре, снимок в галерею",
      "requirements": [
        "R13",
        "R43",
        "R44",
        "R45",
        "R50",
        "R51",
        "R52",
        "R58",
        "R64",
        "R66"
      ],
      "blockedBy": [
        "05"
      ],
      "wave": 6,
      "zone": [
        "app/src/main/java/dev/lupa/filters/",
        "app/src/main/java/dev/lupa/snapshot/",
        "app/src/main/java/dev/lupa/ui/",
        "app/src/main/res/values/strings.xml",
        "app/src/test/"
      ],
      "review": "нет",
      "model": "обычная",
      "status": "done",
      "retries": 0,
      "repairs": 0,
      "startedAt": "2026-11-11T21:52:00+00:00",
      "finishedAt": "2026-11-11T22:04:01+00:00",
      "tests": {
        "passed": 152,
        "failed": 0
      },
      "commit": "c46e623"
    },
    {
      "id": "07",
      "title": "Настройки, вибрация, «не гасить экран», размер кнопок, README",
      "requirements": [
        "R06",
        "R21",
        "R28",
        "R31",
        "R33",
        "R37",
        "R39",
        "R53",
        "R54",
        "R55",
        "R70",
        "R73i"
      ],
      "blockedBy": [
        "06"
      ],
      "wave": 7,
      "zone": [
        "app/src/main/java/dev/lupa/ui/",
        "app/src/main/java/dev/lupa/MainActivity.kt",
        "app/src/main/res/",
        "app/src/test/",
        "README.md"
      ],
      "review": "нет",
      "model": "обычная",
      "status": "in-progress",
      "retries": 0,
      "repairs": 0,
      "startedAt": "2026-11-11T22:04:01+00:00"
    }
  ],
  "tests": {
    "passed": 152,
    "failed": 0
  },
  "debt": {
    "placeholders": [],
    "assumptions": [
      "Стоп-кадр — настоящий снимок около 12 Мп, а не «полное» 200 Мп (в ТЗ два указания, выполнены оба)",
      "ImageAnalysis не подключается — ни одна функция не анализирует кадры, а лишний поток отнимает fps",
      "Шестерёнка — в правом углу зоны управления внизу: верх ТЗ отдаёт только индикатору зума",
      "Двойной тап сбрасывает зум к фиксированному значению из настроек (×2)",
      "Кнопки громкости: шаг ×0.5 до ×2, дальше ×1",
      "Шкала зума: ×1 … максимум камеры × 2 (сверху — программное увеличение превью)",
      "Фиксированный зум при запуске выбирается из ×1.5 / ×2 / ×3 / ×4",
      "Стоп-кадр: щипок ×1…×8 от целого снимка, старт — как было на экране",
      "Снимок сохраняет то, что видно на экране, с фильтром, JPEG",
      "Крупные кнопки — 96 dp, обычные — 80 dp",
      "Без keystore.properties release-APK подписывается отладочным ключом; свой ключ — по README",
      "Проверку на телефоне делаешь ты: здесь нет ни телефона, ни эмулятора",
      "Системная заставка Android 12+ — однотонная тёмная, без логотипа",
      "Поправка: ImageAnalysis всё-таки подключён (он есть в ТЗ) — в низком разрешении, только замер первого кадра и fps в Logcat",
      "Первый запуск — зум ×2; на стоп-кадре ползунок, громкость и индикатор работают с его масштабом",
      "Фонарик при запуске горит, только если включена опция; при возврате из фона — как был"
    ],
    "emptyEnv": []
  },
  "additions": [],
  "coverage": {
    "found": 5,
    "fixed": 4,
    "deferred": 1,
    "items": [
      "R69 — проверка на телефоне после каждого этапа: нет устройства и эмулятора; проверяешь ты на итоговом APK по списку из отчёта"
    ]
  },
  "concerns": [
    "app/src/main/AndroidManifest.xml:17 — allowBackup=\"false\" не просили в спецификации (настройки не восстановятся из резервной копии)",
    "app/src/main/java/dev/lupa/MainActivity.kt:85-96 — восстановление после пересоздания в ASKING/REFUSED не покрыто тестом (scenario.recreate())",
    "tools/check_apk.py:116-156 — отказы проверены только вручную; нужны фикстуры (для манифеста — вход из файла с выводом aapt2)",
    "app/src/main/java/dev/lupa/settings/SettingsRepository.kt:79-80 — FIRST_LAUNCH_ZOOM дублирует ZoomMath.DEFAULT_ZOOM (закрывает таск 03)",
    "app/src/test/java/dev/lupa/ui/LupaThemeTest.kt — нет отдельной проверки, что secondaryContainer и surfaceContainerHighest не жёлтые (из них неактивная дорожка Slider и выключенный Switch)",
    "app/src/main/java/dev/lupa/ui/Theme.kt — primaryContainer жёлтый: у включённого Switch ручка при нажатии/фокусе жёлтая на жёлтой дорожке (учесть в настройках, таск 07)",
    "app/src/main/java/dev/lupa/ui/Theme.kt — error = жёлтый, как акцент (в палитре нет красного; полей с ошибками в приложении нет)",
    "app/src/main/java/dev/lupa/camera/CameraXController.kt:332,252 — ERROR_OTHER_RECOVERABLE_ERROR → Starting; если CameraX не переоткрыл камеру, статус навсегда Starting без сообщения и «Повторить» (нужен таймаут → Error(FAILED))",
    "app/src/main/java/dev/lupa/camera/CameraXController.kt:379,328 — toUprightBitmap (поворот/обрезка) и cameraErrorOf проверены только компиляцией; нужен Robolectric-тест с фейковым ImageProxy и табличный тест кодов",
    "app/src/main/java/dev/lupa/camera/CameraXController.kt:232 — при viewPort == null привязка идёт без ViewPort, снимок перестаёт совпадать с видимой областью",
    "app/src/main/java/dev/lupa/camera/CameraXController.kt:215 — Preview без ResolutionSelector: 4:3 ≤1440×1080, на экране 20:9 видимая часть растягивается ×1,9 (мягкая картинка для лупы)",
    "app/src/test/java/dev/lupa/ui/MagnifierZoomUiTest.kt — UI-тесты зума написаны после кода, красными до реализации не прогонялись",
    "app/src/main/java/dev/lupa/ui/MagnifierScreen.kt — ползунок: своя невидимая ручка 64 dp и боковой отступ 10 dp (M3 Slider игнорирует высоту и вылезает на 20 dp); дорожка на 20 dp короче",
    "app/src/main/java/dev/lupa/ui/MagnifierScreen.kt — отступ индикатора по statusBars ∪ displayCutout тестом не проверен (в Robolectric инсеты нулевые)",
    "app/src/main/java/dev/lupa/ui/MagnifierViewModel.kt — запись зума с задержкой 300 мс теряется при убийстве процесса; IOException при записи глушится",
    "app/src/main/java/dev/lupa/MainActivity.kt — нет прямого теста onKeyDown/onKeyUp (логика проверена в VM)",
    "app/src/main/java/dev/lupa/ui/ControlPanel.kt — заливка активной кнопки (Accent/Inactive) проверена только чтением кода; в Robolectric captureToImage таймаутит, тест смотрит semantics selected",
    "app/src/main/java/dev/lupa/ui/ControlPanel.kt — ButtonMetrics.Large = 104/48/20, а спецификация (история 45) — 96 dp / 44 dp / 18 sp; выровнять в таске 07",
    "app/src/main/java/dev/lupa/ui/ControlPanel.kt — на 360 dp кнопки ~81 dp; «Стоп-кадр» 16 sp bold без переноса может обрезаться на узких экранах",
    "app/src/main/java/dev/lupa/ui/MagnifierViewModel.kt — onTap без размера области (0) не пересчитывает координаты",
    "app/src/main/java/dev/lupa/ui/MagnifierViewModel.kt — пока снимок делается (FREEZING), живой зум не меняется (правило исполнителя, в спецификации не было)",
    "app/src/main/java/dev/lupa/ui/MagnifierScreen.kt — стоп-кадр с ContentScale.FillBounds опирается на контракт capture() «снимок в пропорциях экрана»",
    "app/src/main/java/dev/lupa/ui/MagnifierViewModel.kt — стоп-кадр ~12 Мп держит ~48 МБ памяти; на телефоне не проверено",
    "app/src/test/java/dev/lupa/filters/FiltersViewTest.kt — тест слоя View на API 29 и Bitmap-фильтра, «MediaStore отказал», «снимок до Ready» не были красными до кода; RenderEffect на API 31+ тестом не проверить",
    "app/src/test/java/dev/lupa/snapshot/MediaStoreSnapshotSaverTest.kt — MediaStore в тесте подменён своим FakeMediaProvider, не реальный провайдер",
    "app/src/main/java/dev/lupa/ui/MagnifierViewModel.kt — живой зум заморожен и на время SAVING (не только FREEZING)"
  ],
  "blind": null,
  "beats": [
    "2026-11-11T19:44:15+00:00",
    "2026-11-11T19:46:46+00:00",
    "2026-11-11T19:48:38+00:00",
    "2026-11-11T19:50:29+00:00",
    "2026-11-11T19:50:32+00:00",
    "2026-11-11T19:57:29+00:00",
    "2026-11-11T19:57:32+00:00",
    "2026-11-11T19:58:27+00:00",
    "2026-11-11T20:00:29+00:00",
    "2026-11-11T20:00:38+00:00",
    "2026-11-11T20:00:42+00:00",
    "2026-11-11T20:00:50+00:00",
    "2026-11-11T20:31:19+00:00",
    "2026-11-11T20:36:50+00:00",
    "2026-11-11T20:36:51+00:00",
    "2026-11-11T20:42:27+00:00",
    "2026-11-11T20:42:28+00:00",
    "2026-11-11T20:42:35+00:00",
    "2026-11-11T21:04:16+00:00",
    "2026-11-11T21:12:49+00:00",
    "2026-11-11T21:12:50+00:00",
    "2026-11-11T21:26:57+00:00",
    "2026-11-11T21:34:42+00:00",
    "2026-11-11T21:34:43+00:00",
    "2026-11-11T21:52:00+00:00",
    "2026-11-11T22:04:01+00:00"
  ],
  "rev": 40,
  "waiting": null,
  "waits": [],
  "log": [
    {
      "t": "2026-11-11T19:44:15+00:00",
      "k": "init"
    },
    {
      "t": "2026-11-11T19:46:46+00:00",
      "k": "stage",
      "id": "manifest",
      "act": "start"
    },
    {
      "t": "2026-11-11T19:48:38+00:00",
      "k": "stage",
      "id": "briefing",
      "act": "start"
    },
    {
      "t": "2026-11-11T19:50:29+00:00",
      "k": "stage",
      "id": "briefing",
      "act": "skip",
      "note": "полный автомат — самобрифинг"
    },
    {
      "t": "2026-11-11T19:50:29+00:00",
      "k": "add",
      "key": "debt.assumptions",
      "n": 13
    },
    {
      "t": "2026-11-11T19:50:32+00:00",
      "k": "gate",
      "g": "G1",
      "ok": false,
      "open": 74,
      "total": 74
    },
    {
      "t": "2026-11-11T19:50:32+00:00",
      "k": "stage",
      "id": "spec",
      "act": "start"
    },
    {
      "t": "2026-11-11T19:57:29+00:00",
      "k": "gate",
      "g": "G2",
      "ok": true,
      "found": 5,
      "fixed": 4,
      "deferred": 1
    },
    {
      "t": "2026-11-11T19:57:29+00:00",
      "k": "add",
      "key": "debt.assumptions",
      "n": 3
    },
    {
      "t": "2026-11-11T19:57:32+00:00",
      "k": "stage",
      "id": "plan",
      "act": "start"
    },
    {
      "t": "2026-11-11T19:58:27+00:00",
      "k": "tier",
      "v": "T2"
    },
    {
      "t": "2026-11-11T20:00:29+00:00",
      "k": "tickets",
      "n": 7
    },
    {
      "t": "2026-11-11T20:00:29+00:00",
      "k": "gate",
      "g": "G3",
      "ok": true,
      "problems": 0
    },
    {
      "t": "2026-11-11T20:00:42+00:00",
      "k": "stage",
      "id": "build",
      "act": "start"
    },
    {
      "t": "2026-11-11T20:00:50+00:00",
      "k": "ticket",
      "ids": [
        "01"
      ],
      "act": "start"
    },
    {
      "t": "2026-11-11T20:31:19+00:00",
      "k": "ticket",
      "ids": [
        "01"
      ],
      "act": "review"
    },
    {
      "t": "2026-11-11T20:36:50+00:00",
      "k": "ticket",
      "ids": [
        "01"
      ],
      "act": "repair",
      "note": "LupaColorScheme: outline не жёлтый, роли неактивных состояний и контейнеров из палитры проекта"
    },
    {
      "t": "2026-11-11T20:36:51+00:00",
      "k": "add",
      "key": "concerns",
      "n": 4
    },
    {
      "t": "2026-11-11T20:42:27+00:00",
      "k": "ticket",
      "ids": [
        "01"
      ],
      "act": "done",
      "tests": {
        "passed": 24,
        "failed": 0
      }
    },
    {
      "t": "2026-11-11T20:42:28+00:00",
      "k": "add",
      "key": "concerns",
      "n": 3
    },
    {
      "t": "2026-11-11T20:42:35+00:00",
      "k": "ticket",
      "ids": [
        "02"
      ],
      "act": "start"
    },
    {
      "t": "2026-11-11T21:04:16+00:00",
      "k": "ticket",
      "ids": [
        "02"
      ],
      "act": "review"
    },
    {
      "t": "2026-11-11T21:12:49+00:00",
      "k": "add",
      "key": "concerns",
      "n": 4
    },
    {
      "t": "2026-11-11T21:12:49+00:00",
      "k": "ticket",
      "ids": [
        "02"
      ],
      "act": "done",
      "tests": {
        "passed": 33,
        "failed": 0
      }
    },
    {
      "t": "2026-11-11T21:12:50+00:00",
      "k": "ticket",
      "ids": [
        "03"
      ],
      "act": "start"
    },
    {
      "t": "2026-11-11T21:26:57+00:00",
      "k": "add",
      "key": "concerns",
      "n": 5
    },
    {
      "t": "2026-11-11T21:26:57+00:00",
      "k": "ticket",
      "ids": [
        "03"
      ],
      "act": "done",
      "tests": {
        "passed": 68,
        "failed": 0
      }
    },
    {
      "t": "2026-11-11T21:26:57+00:00",
      "k": "ticket",
      "ids": [
        "04"
      ],
      "act": "start"
    },
    {
      "t": "2026-11-11T21:34:42+00:00",
      "k": "add",
      "key": "concerns",
      "n": 4
    },
    {
      "t": "2026-11-11T21:34:43+00:00",
      "k": "ticket",
      "ids": [
        "04"
      ],
      "act": "done",
      "tests": {
        "passed": 90,
        "failed": 0
      }
    },
    {
      "t": "2026-11-11T21:34:43+00:00",
      "k": "ticket",
      "ids": [
        "05"
      ],
      "act": "start"
    },
    {
      "t": "2026-11-11T21:52:00+00:00",
      "k": "add",
      "key": "concerns",
      "n": 3
    },
    {
      "t": "2026-11-11T21:52:00+00:00",
      "k": "ticket",
      "ids": [
        "05"
      ],
      "act": "done",
      "tests": {
        "passed": 123,
        "failed": 0
      }
    },
    {
      "t": "2026-11-11T21:52:00+00:00",
      "k": "ticket",
      "ids": [
        "06"
      ],
      "act": "start"
    },
    {
      "t": "2026-11-11T22:04:01+00:00",
      "k": "add",
      "key": "concerns",
      "n": 3
    },
    {
      "t": "2026-11-11T22:04:01+00:00",
      "k": "ticket",
      "ids": [
        "06"
      ],
      "act": "done",
      "tests": {
        "passed": 152,
        "failed": 0
      }
    },
    {
      "t": "2026-11-11T22:04:01+00:00",
      "k": "ticket",
      "ids": [
        "07"
      ],
      "act": "start"
    }
  ],
  "reqList": [
    {
      "id": "R01",
      "text": "«Нужна лупа на камере телефона»",
      "status": "done"
    },
    {
      "id": "R02",
      "text": "«без рекламы»",
      "status": "done"
    },
    {
      "id": "R03",
      "text": "«офлайн» · «Без интернета» · «Приложение работает в режиме полёта»",
      "status": "done"
    },
    {
      "id": "R04",
      "text": "«Разрешение INTERNET в манифесте не объявляется»",
      "status": "done"
    },
    {
      "id": "R05",
      "text": "«с минимумом крупных кнопок, которые удобно нажимать одной рукой» · «Все кнопки нажимаются большим пальцем одной руки»",
      "status": "done"
    },
    {
      "id": "R06",
      "text": "«Личное использование, один телефон, установка через APK (без Google Play)»",
      "status": "in-ticket"
    },
    {
      "id": "R07",
      "text": "«Сторонних SDK (реклама, аналитика, Firebase) нет» · «никакой рекламы, аналитики, крэш-репортеров и сетевых запросов»",
      "status": "done"
    },
    {
      "id": "R08",
      "text": "«Без … аккаунтов … и уведомлений»",
      "status": "done"
    },
    {
      "id": "R09",
      "text": "«Запуск — сразу в режим лупы, без заставок и меню» · «никаких заставок, онбординга, оценок «понравилось ли приложение»»",
      "status": "done"
    },
    {
      "id": "R10",
      "text": "«Модель телефона и версия Android: Redmi Note 13 Pro 5G, Android 16 (HyperOS)»",
      "status": "done"
    },
    {
      "id": "R11",
      "text": "«Язык Kotlin» · «UI Jetpack Compose, Material 3»",
      "status": "done"
    },
    {
      "id": "R12",
      "text": "«Камера CameraX (Preview, ImageCapture, ImageAnalysis)»",
      "status": "done"
    },
    {
      "id": "R13",
      "text": "«Фильтры ColorMatrix / RenderEffect поверх превью»",
      "status": "done"
    },
    {
      "id": "R14",
      "text": "«Хранение настроек DataStore Preferences»",
      "status": "done"
    },
    {
      "id": "R15",
      "text": "«minSdk / targetSdk 29 / 36 (Android 16)»",
      "status": "done"
    },
    {
      "id": "R16",
      "text": "«Сборка Gradle, один модуль app, подписанный release-APK»",
      "status": "done"
    },
    {
      "id": "R17",
      "text": "«Edge-to-edge обязателен … Нижнюю панель кнопок отодвинуть по WindowInsets.navigationBars, индикатор зума — по statusBars»",
      "status": "done"
    },
    {
      "id": "R18",
      "text": "«Стоп-кадр снимать не в полном разрешении, а около 12 Мп через ResolutionSelector»",
      "status": "done"
    },
    {
      "id": "R19",
      "text": "«Зум цифровой (кроп основной камеры); максимум брать из maxZoomRatio устройства, не хардкодить»",
      "status": "done"
    },
    {
      "id": "R20",
      "text": "«Вспышка есть — кнопка фонарика всегда видна» · «Если вспышки нет — кнопка скрыта»",
      "status": "done"
    },
    {
      "id": "R21",
      "text": "«Установка на HyperOS. В «Для разработчиков» включить «Отладка по USB» и «Установка через USB» … Альтернатива — скопировать APK на телефон и открыть файлом»",
      "status": "in-ticket"
    },
    {
      "id": "R22",
      "text": "«Один экран … превью камеры на весь экран»",
      "status": "done"
    },
    {
      "id": "R23",
      "text": "«Всё управление — в нижней трети, чтобы доставать большим пальцем»",
      "status": "done"
    },
    {
      "id": "R24",
      "text": "«Верх: только индикатор зума (например, «×4.0») крупным шрифтом, полупрозрачная подложка»",
      "status": "done"
    },
    {
      "id": "R25",
      "text": "«Над кнопками: горизонтальный ползунок зума на всю ширину, высота зоны касания не меньше 64 dp»",
      "status": "done"
    },
    {
      "id": "R26",
      "text": "«Нижний ряд — 4 кнопки, каждая не меньше 80×80 dp, иконка + подпись: Фонарик · Стоп-кадр · Фильтр · Снимок»",
      "status": "done"
    },
    {
      "id": "R27",
      "text": "«Активное состояние кнопки (фонарик включён, кадр заморожен) видно сразу: заливка контрастным цветом»",
      "status": "done"
    },
    {
      "id": "R28",
      "text": "«Настройки — маленькая шестерёнка в углу, не мешает основным кнопкам»",
      "status": "in-ticket"
    },
    {
      "id": "R29",
      "text": "«щипок — зум»",
      "status": "done"
    },
    {
      "id": "R30",
      "text": "«двойной тап — сброс зума к значению по умолчанию»",
      "status": "done"
    },
    {
      "id": "R31",
      "text": "«кнопка громкости «+»/«−» — зум (включается в настройках)» · «Зум кнопками громкости (вкл.)»",
      "status": "in-ticket"
    },
    {
      "id": "R32",
      "text": "«подписи от 16 sp, иконки от 36 dp»",
      "status": "done"
    },
    {
      "id": "R33",
      "text": "«вибрация при нажатии» · «Вибрация при нажатии (вкл.)»",
      "status": "in-ticket"
    },
    {
      "id": "R34",
      "text": "«Тёмная тема по умолчанию»",
      "status": "done"
    },
    {
      "id": "R35",
      "text": "«Зум. От ×1 до максимума камеры (zoomState.maxZoomRatio). Плавное изменение ползунком и щипком»",
      "status": "done"
    },
    {
      "id": "R36",
      "text": "«Если оптического/цифрового зума камеры не хватает — дополнительный программный зум превью до ×2 сверху»",
      "status": "done"
    },
    {
      "id": "R37",
      "text": "«По умолчанию при запуске — последнее использованное значение» · «Зум при запуске: последний / фиксированный (×2 по умолчанию)»",
      "status": "in-ticket"
    },
    {
      "id": "R38",
      "text": "«Фонарик. Одна кнопка вкл/выкл (cameraControl.enableTorch)»",
      "status": "done"
    },
    {
      "id": "R39",
      "text": "«Опция в настройках: «включать фонарик при запуске»» · «Включать фонарик при запуске (выкл. по умолчанию)»",
      "status": "in-ticket"
    },
    {
      "id": "R40",
      "text": "«Стоп-кадр. Нажатие замораживает текущее изображение в полном разрешении (ImageCapture, не скриншот превью)» · «Стоп-кадр чёткий (не размытое превью)»",
      "status": "done"
    },
    {
      "id": "R41",
      "text": "«В замороженном кадре работают щипок-зум до ×8, перетаскивание»",
      "status": "done"
    },
    {
      "id": "R42",
      "text": "«Повторное нажатие — обратно к живой камере»",
      "status": "done"
    },
    {
      "id": "R43",
      "text": "«Кнопка переключает по кругу: Обычный → Высокий контраст → Ч/Б → Инверсия → Жёлтый на чёрном»",
      "status": "done"
    },
    {
      "id": "R44",
      "text": "«Название фильтра показывается на 1 секунду крупно по центру»",
      "status": "done"
    },
    {
      "id": "R45",
      "text": "«Фильтр применяется и к живому превью, и к стоп-кадру» · «Все 5 фильтров работают на живом изображении и на стоп-кадре»",
      "status": "done"
    },
    {
      "id": "R46",
      "text": "«Непрерывный автофокус по умолчанию»",
      "status": "done"
    },
    {
      "id": "R47",
      "text": "«тап — фокус и экспозиция в точке (FocusMeteringAction)» · «одиночный тап — фокус в точке»",
      "status": "done"
    },
    {
      "id": "R48",
      "text": "«с кружком-индикатором»",
      "status": "done"
    },
    {
      "id": "R49",
      "text": "«Через 5 секунд — возврат к непрерывному автофокусу»",
      "status": "done"
    },
    {
      "id": "R50",
      "text": "«Снимок. Сохраняет текущий кадр (с фильтром) в галерею, папку Pictures/Lupa, через MediaStore»",
      "status": "done"
    },
    {
      "id": "R51",
      "text": "«Короткое подтверждение «Сохранено»»",
      "status": "done"
    },
    {
      "id": "R52",
      "text": "«Приложение запоминает между запусками: последний зум, последний фильтр, состояние фонарика (если включена опция)»",
      "status": "done"
    },
    {
      "id": "R53",
      "text": "«Экран настроек — один простой список переключателей»",
      "status": "in-ticket"
    },
    {
      "id": "R54",
      "text": "«Не гасить экран, пока открыта лупа (вкл.)»",
      "status": "in-ticket"
    },
    {
      "id": "R55",
      "text": "«Размер кнопок: обычный / крупный»",
      "status": "in-ticket"
    },
    {
      "id": "R56",
      "text": "«Единственное разрешение — CAMERA. Запрос при первом запуске»",
      "status": "done"
    },
    {
      "id": "R57",
      "text": "«при отказе — экран с объяснением и кнопкой «Открыть настройки»»",
      "status": "done"
    },
    {
      "id": "R58",
      "text": "«Сохранение в галерею через MediaStore, без разрешения на хранилище»",
      "status": "done"
    },
    {
      "id": "R59",
      "text": "«При сворачивании камера и фонарик освобождаются»",
      "status": "done"
    },
    {
      "id": "R60",
      "text": "«при возврате — восстанавливаются зум, фильтр и фонарик» · «После сворачивания и возврата зум, фильтр и фонарик восстановлены»",
      "status": "done"
    },
    {
      "id": "R61",
      "text": "«портретная ориентация» · «Ориентация зафиксирована портретной»",
      "status": "done"
    },
    {
      "id": "R62",
      "text": "«Ошибка камеры (занята другим приложением) — понятное сообщение и кнопка «Повторить»»",
      "status": "done"
    },
    {
      "id": "R63",
      "text": "«Холодный старт до превью — не дольше 1,5 секунды»",
      "status": "done"
    },
    {
      "id": "R64",
      "text": "«Превью без заметных подтормаживаний при включённом фильтре (цель — 30 кадров/с)»",
      "status": "done"
    },
    {
      "id": "R65",
      "text": "«Размер APK — до 10 МБ»",
      "status": "done"
    },
    {
      "id": "R66",
      "text": "«Код разбит на понятные части: CameraController, MagnifierScreen, SettingsRepository, Filters»",
      "status": "done"
    },
    {
      "id": "R67",
      "text": "«не добавлять кнопок на главный экран сверх четырёх»",
      "status": "done"
    },
    {
      "id": "R68",
      "text": "«После установки APK и разрешения камеры лупа открывается сразу, без промежуточных экранов»",
      "status": "done"
    },
    {
      "id": "R69",
      "text": "«Делать по этапам, после каждого — сборка APK и проверка на телефоне»",
      "status": "done"
    },
    {
      "id": "R70",
      "text": "«В конце — README с командой сборки и установкой через adb install»",
      "status": "in-ticket"
    },
    {
      "id": "R71",
      "text": "«Репозиторий loop под это тз»",
      "status": "done"
    },
    {
      "id": "R72i",
      "text": "(подразумевается) подписанный release-APK требует ключа подписи, который хранится у пользователя и не попадает в репозиторий",
      "status": "done"
    },
    {
      "id": "R73i",
      "text": "(подразумевается) готовый APK должен попасть на телефон пользователя — собрать его можно и без Android Studio",
      "status": "in-ticket"
    },
    {
      "id": "R74i",
      "text": "(подразумевается) пока кнопки громкости управляют зумом, они не меняют громкость; при выключенной опции — работают как обычно",
      "status": "done"
    }
  ],
  "gates": {
    "G1": {
      "ok": false,
      "at": "2026-11-11T19:50:32+00:00",
      "open": 74,
      "total": 74
    },
    "G2": {
      "ok": true,
      "at": "2026-11-11T19:57:29+00:00",
      "found": 5,
      "fixed": 4,
      "deferred": 1
    },
    "G3": {
      "ok": true,
      "at": "2026-11-11T20:00:29+00:00",
      "problems": 0
    }
  },
  "result": null
}
