package dev.lupa.ui

import androidx.annotation.StringRes
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.lupa.R
import dev.lupa.camera.CameraError
import dev.lupa.camera.CameraStatus

/**
 * Экран лупы: живое превью на весь экран, без полей и под системными панелями.
 * При ошибке камеры поверх превью — [CameraErrorContent].
 *
 * [bindPreview] получает созданный `PreviewView` один раз; Activity привязывает к нему камеру.
 */
@Composable
fun MagnifierScreen(
    state: MagnifierUiState,
    viewModel: MagnifierViewModel,
    bindPreview: (PreviewView) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(LupaColors.Background),
    ) {
        CameraPreview(bindPreview, Modifier.fillMaxSize().testTag(TestTags.PREVIEW))
        val camera = state.camera
        if (camera is CameraStatus.Error) {
            CameraErrorContent(reason = camera.reason, onRetry = viewModel::onRetry)
        }
    }
}

/** Понятное сообщение об ошибке камеры и кнопка «Повторить». Закрывает превью целиком. */
@Composable
fun CameraErrorContent(reason: CameraError, onRetry: () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag(TestTags.CAMERA_ERROR),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(reason.messageRes()),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .testTag(TestTags.BTN_RETRY),
            ) {
                Text(
                    text = stringResource(R.string.camera_retry),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@StringRes
private fun CameraError.messageRes(): Int = when (this) {
    CameraError.IN_USE -> R.string.camera_error_in_use
    CameraError.DISABLED -> R.string.camera_error_disabled
    CameraError.NOT_FOUND -> R.string.camera_error_not_found
    CameraError.FAILED -> R.string.camera_error_failed
}

/** `PreviewView` создаётся один раз и сразу отдаётся на привязку камеры. */
@Composable
private fun CameraPreview(bindPreview: (PreviewView) -> Unit, modifier: Modifier) {
    val currentBindPreview by rememberUpdatedState(bindPreview)
    AndroidView(
        factory = { context -> PreviewView(context).also { currentBindPreview(it) } },
        modifier = modifier,
    )
}
