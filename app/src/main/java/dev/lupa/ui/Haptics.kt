package dev.lupa.ui

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView

/**
 * Короткий тактильный отклик нажатия. Выключен в настройках — ничего не делает.
 * Идёт через `View`, поэтому разрешение VIBRATE не нужно.
 */
@Composable
fun rememberHaptic(enabled: Boolean): () -> Unit {
    val view = LocalView.current
    val currentEnabled = rememberUpdatedState(enabled)
    return remember(view) {
        {
            if (currentEnabled.value) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            Unit
        }
    }
}
