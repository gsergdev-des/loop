package dev.lupa

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as SystemSettings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.lupa.ui.LupaTheme
import dev.lupa.ui.MagnifierPlaceholder
import dev.lupa.ui.PermissionScreen

/** Единственная Activity. Решает, какой экран показать: запрос камеры → лупа. */
class MainActivity : ComponentActivity() {

    /** Где мы с доступом к камере. */
    private enum class CameraAccess { ASKING, REFUSED, ALLOWED }

    private var cameraAccess by mutableStateOf(CameraAccess.ASKING)
    private var canAskAgain by mutableStateOf(false)

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

        setContent {
            LupaTheme {
                when (cameraAccess) {
                    CameraAccess.ALLOWED -> MagnifierPlaceholder()
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

    private companion object {
        const val STATE_CAMERA_ACCESS = "cameraAccess"
    }
}
