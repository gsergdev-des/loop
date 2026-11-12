package dev.lupa

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as SystemSettings
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.lupa.camera.CameraController
import dev.lupa.camera.CameraXController
import dev.lupa.settings.SettingsRepository
import dev.lupa.snapshot.MediaStoreSnapshotSaver
import dev.lupa.ui.LupaTheme
import dev.lupa.ui.MagnifierScreen
import dev.lupa.ui.MagnifierViewModel
import dev.lupa.ui.PermissionScreen
import dev.lupa.ui.SettingsScreen

/** Единственная Activity. Решает, какой экран показать: запрос камеры → лупа с живым превью. */
class MainActivity : ComponentActivity() {

    /** Где мы с доступом к камере. */
    private enum class CameraAccess { ASKING, REFUSED, ALLOWED }

    private var cameraAccess by mutableStateOf(CameraAccess.ASKING)
    private var canAskAgain by mutableStateOf(false)

    /** Показаны ли настройки вместо лупы. */
    private var showSettings by mutableStateOf(false)

    /** VM лупы переживает пересоздание Activity, а с ним и камера: Activity берёт её из VM. */
    private val magnifierViewModel: MagnifierViewModel by viewModels {
        viewModelFactory {
            initializer {
                MagnifierViewModel(
                    camera = cameraControllerFactory(applicationContext),
                    settingsRepository = SettingsRepository.create(applicationContext),
                    saver = MediaStoreSnapshotSaver(applicationContext),
                )
            }
        }
    }

    private val cameraPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            cameraAccess = if (granted) CameraAccess.ALLOWED else CameraAccess.REFUSED
            refreshCanAskAgain()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Всегда тёмная тема: светлые значки системных панелей поверх прозрачного фона.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        showSettings = savedInstanceState?.getBoolean(STATE_SHOW_SETTINGS) ?: false
        val restored = savedInstanceState?.getString(STATE_CAMERA_ACCESS)
            ?.let { name -> CameraAccess.entries.firstOrNull { it.name == name } }
        cameraAccess = when {
            hasCameraPermission() -> CameraAccess.ALLOWED
            restored == CameraAccess.REFUSED -> CameraAccess.REFUSED
            // Запрос уже показан до пересоздания Activity — ответ придёт в cameraPermissionRequest.
            restored == CameraAccess.ASKING -> CameraAccess.ASKING
            else -> {
                cameraPermissionRequest.launch(Manifest.permission.CAMERA)
                CameraAccess.ASKING
            }
        }
        if (cameraAccess == CameraAccess.ALLOWED) {
            // Камера (и инициализация CameraX) создаётся сразу, пока Compose строит первый кадр, —
            // не дожидаясь настроек и экрана (холодный старт до картинки ≤ 1,5 с).
            magnifierViewModel.camera
        }

        setContent {
            LupaTheme {
                when (cameraAccess) {
                    CameraAccess.ALLOWED -> if (showSettings) SettingsPage() else Magnifier()
                    CameraAccess.REFUSED -> PermissionScreen(
                        canAskAgain = canAskAgain,
                        onRequest = { cameraPermissionRequest.launch(Manifest.permission.CAMERA) },
                        onOpenSettings = ::openAppSettings,
                    )
                    // Поверх — системный диалог; под ним чёрный фон окна.
                    CameraAccess.ASKING -> Box(Modifier.fillMaxSize())
                }
            }
        }
    }

    @Composable
    private fun Magnifier() {
        val state by magnifierViewModel.state.collectAsStateWithLifecycle()
        MagnifierScreen(
            state = state,
            viewModel = magnifierViewModel,
            bindPreview = { previewView -> magnifierViewModel.camera.bind(previewView, this) },
            onOpenSettings = { showSettings = true },
        )
        // Экран не гаснет, пока на экране лупа и опция включена; уход в настройки снимает флаг.
        val keepScreenOn = state.settings.keepScreenOn
        DisposableEffect(keepScreenOn) {
            if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
    }

    @Composable
    private fun SettingsPage() {
        val state by magnifierViewModel.state.collectAsStateWithLifecycle()
        BackHandler { showSettings = false }
        SettingsScreen(
            settings = state.settings,
            onChange = magnifierViewModel::updateSettings,
            onBack = { showSettings = false },
        )
    }

    /**
     * Кнопки громкости на экране лупы меняют зум, если опция включена; удержание повторяет шаг
     * (система шлёт повторные onKeyDown). Опция выключена — событие уходит системе.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isVolumeKey(keyCode) && cameraAccess == CameraAccess.ALLOWED &&
            magnifierViewModel.onVolumeKey(up = keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        ) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /** Отпускание той же кнопки тоже съедаем, иначе система успеет показать панель громкости. */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isVolumeKey(keyCode) && cameraAccess == CameraAccess.ALLOWED &&
            magnifierViewModel.state.value.settings.volumeKeysZoom
        ) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun isVolumeKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

    override fun onResume() {
        super.onResume()
        // Доступ могли выдать (или отозвать) в системных настройках, пока нас не было.
        if (hasCameraPermission()) {
            cameraAccess = CameraAccess.ALLOWED
        } else if (cameraAccess == CameraAccess.ALLOWED) {
            cameraAccess = CameraAccess.REFUSED
        }
        refreshCanAskAgain()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CAMERA_ACCESS, cameraAccess.name)
        outState.putBoolean(STATE_SHOW_SETTINGS, showSettings)
    }

    private fun hasCameraPermission(): Boolean =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun refreshCanAskAgain() {
        canAskAgain = cameraAccess == CameraAccess.REFUSED &&
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
    }

    /** Страница приложения в системных настройках; если её нет — общие настройки. */
    private fun openAppSettings() {
        val appDetails = Intent(
            SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
        try {
            startActivity(appDetails)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(SystemSettings.ACTION_SETTINGS))
        }
    }

    companion object {
        private const val STATE_CAMERA_ACCESS = "cameraAccess"
        private const val STATE_SHOW_SETTINGS = "showSettings"

        /** Как создаётся камера. Тесты подменяют на фейк: CameraX в Robolectric не поднимается. */
        @VisibleForTesting
        internal var cameraControllerFactory: (Context) -> CameraController = { CameraXController(it) }
    }
}
