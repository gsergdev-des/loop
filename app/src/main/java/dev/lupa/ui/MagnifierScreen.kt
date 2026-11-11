package dev.lupa.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.lupa.zoom.ZoomMath
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
    buttonMetrics: ButtonMetrics = ButtonMetrics.Normal,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(LupaColors.Background),
    ) {
        CameraPreview(
            bindPreview,
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Программный зум: превью растягивается вокруг центра (камера уже на максимуме).
                    scaleX = state.softScale
                    scaleY = state.softScale
                }
                .testTag(TestTags.PREVIEW),
        )
        ZoomGestures(viewModel, Modifier.fillMaxSize())
        ZoomIndicator(state.zoom, Modifier.align(Alignment.TopCenter))
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .testTag(TestTags.CONTROLS),
        ) {
            ZoomSlider(state, viewModel::onSliderChange)
            ControlPanel(state, viewModel, metrics = buttonMetrics)
        }
        state.focusPoint?.let { FocusRing(it.first, it.second) }
        val camera = state.camera
        if (camera is CameraStatus.Error) {
            CameraErrorContent(reason = camera.reason, onRetry = viewModel::onRetry)
        }
    }
}

/** Прозрачный слой над превью: щипок меняет зум, двойной тап возвращает зум по умолчанию. */
@Composable
private fun ZoomGestures(viewModel: MagnifierViewModel, modifier: Modifier) {
    Box(
        modifier
            .onSizeChanged { viewModel.onViewSize(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(viewModel) {
                detectTapGestures(
                    onTap = { viewModel.onTap(it.x, it.y) },
                    onDoubleTap = { viewModel.onDoubleTap() },
                )
            }
            .pointerInput(viewModel) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    if (zoom != 1f) viewModel.onPinch(zoom, centroid.x, centroid.y, pan.x, pan.y)
                }
            },
    )
}

/** Кружок фокуса с центром в экранной точке ([x], [y]). */
@Composable
private fun FocusRing(x: Float, y: Float) {
    val radius = with(LocalDensity.current) { FOCUS_RING_SIZE.toPx() / 2 }
    Box(
        Modifier
            .offset { IntOffset((x - radius).roundToInt(), (y - radius).roundToInt()) }
            .size(FOCUS_RING_SIZE)
            .border(3.dp, LupaColors.Accent, CircleShape)
            .testTag(TestTags.FOCUS_RING),
    )
}

private val FOCUS_RING_SIZE = 72.dp

/** Итоговый зум вверху по центру на полупрозрачной подложке; не заходит под статус-бар и вырез камеры. */
@Composable
private fun ZoomIndicator(zoom: Float, modifier: Modifier) {
    Box(
        modifier
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
            .padding(top = 8.dp),
    ) {
        Text(
            text = ZoomMath.format(zoom),
            color = LupaColors.OnBackground,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(ZOOM_INDICATOR_BACKGROUND, RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .testTag(TestTags.ZOOM_INDICATOR),
        )
    }
}

private val ZOOM_INDICATOR_BACKGROUND = Color(0x99000000)

/**
 * Ползунок зума: сам ползунок (с ручкой) занимает ширину экрана минус поля 16 dp, зона касания — 64 dp
 * по высоте; шкала логарифмическая. Material 3 тянет ручку за края дорожки на её половину ширины, поэтому
 * дорожка уже на [SLIDER_THUMB_WIDTH]: ручка у края остаётся внутри полей.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZoomSlider(state: MagnifierUiState, onChange: (Float) -> Unit) {
    val description = stringResource(R.string.zoom_slider_description)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(SLIDER_TOUCH_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Slider(
            value = ZoomMath.toSlider(state.zoom, state.minZoom, state.maxZoom),
            onValueChange = onChange,
            modifier = Modifier
                .padding(horizontal = SLIDER_THUMB_WIDTH / 2)
                .semantics { contentDescription = description }
                .testTag(TestTags.ZOOM_SLIDER),
            interactionSource = interactionSource,
            thumb = {
                // Невидимая высота ручки делает зону касания 64 dp; видна только сама ручка.
                Box(Modifier.size(SLIDER_THUMB_WIDTH, SLIDER_TOUCH_HEIGHT), contentAlignment = Alignment.Center) {
                    SliderDefaults.Thumb(interactionSource = interactionSource)
                }
            },
        )
    }
}

private val SLIDER_TOUCH_HEIGHT = 64.dp
private val SLIDER_THUMB_WIDTH = 20.dp

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
