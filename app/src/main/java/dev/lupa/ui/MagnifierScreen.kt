package dev.lupa.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
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
import dev.lupa.filters.Filter
import dev.lupa.filters.Filters

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
    onOpenSettings: () -> Unit,
    buttonMetrics: ButtonMetrics = state.settings.buttonSize.metrics(),
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
            state.filter,
        )
        state.frozen?.let { FrozenImage(it, state.filter, Modifier.fillMaxSize()) }
        ZoomGestures(viewModel, Modifier.fillMaxSize())
        ZoomIndicator(state.indicatorZoom, Modifier.align(Alignment.TopCenter))
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            // Сообщение — над блоком управления; блок при этом не сдвигается (колонка прижата к низу).
            // Название фильтра — не здесь, а крупно по центру экрана.
            state.message?.takeIf { it !is UiMessage.FilterName }?.let { MessageBanner(it) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag(TestTags.CONTROLS),
            ) {
                SettingsButton(
                    onClick = onOpenSettings,
                    haptics = state.settings.haptics,
                    modifier = Modifier.align(Alignment.End).padding(end = 16.dp),
                )
                ZoomSlider(state, viewModel::onSliderChange)
                ControlPanel(state, viewModel, metrics = buttonMetrics)
            }
        }
        state.message?.let { message ->
            MessageTimer(message, viewModel::onMessageShown)
            if (message is UiMessage.FilterName) FilterNameOverlay(message.filter, Modifier.align(Alignment.Center))
        }
        state.focusPoint?.let { FocusRing(it.first, it.second) }
        val camera = state.camera
        if (camera is CameraStatus.Error) {
            CameraErrorContent(reason = camera.reason, onRetry = viewModel::onRetry)
        }
    }
}

/** Маленькая шестерёнка в правом углу зоны управления, над ползунком: открывает настройки. */
@Composable
private fun SettingsButton(onClick: () -> Unit, haptics: Boolean, modifier: Modifier) {
    val haptic = rememberHaptic(haptics)
    val description = stringResource(R.string.button_settings_description)
    Box(
        modifier = modifier
            .size(SETTINGS_BUTTON_SIZE)
            .clip(CircleShape)
            .background(LupaColors.Inactive)
            .clickable(role = Role.Button, onClickLabel = description) { haptic(); onClick() }
            .testTag(TestTags.BTN_SETTINGS),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = description,
            tint = LupaColors.OnInactive,
            modifier = Modifier.size(SETTINGS_ICON_SIZE),
        )
    }
}

private val SETTINGS_BUTTON_SIZE = 48.dp
private val SETTINGS_ICON_SIZE = 32.dp

/**
 * Снимок стоп-кадра поверх превью: при масштабе 1 ровно на весь экран (снимок в пропорциях экрана),
 * дальше — растянут вокруг центра и сдвинут, как велит [FrozenFrame.viewport].
 */
@Composable
private fun FrozenImage(frozen: FrozenFrame, filter: Filter, modifier: Modifier) {
    val image = remember(frozen.bitmap) { frozen.bitmap.asImageBitmap() }
    val viewport = frozen.viewport
    Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        colorFilter = Filters.colorFilter(filter),
        modifier = modifier
            .graphicsLayer {
                scaleX = viewport.scale
                scaleY = viewport.scale
                translationX = viewport.offsetX
                translationY = viewport.offsetY
            }
            .testTag(TestTags.FROZEN_IMAGE),
    )
}

/** Через положенное время UI сообщает VM, что сообщение показано. Новое сообщение запускает отсчёт заново. */
@Composable
private fun MessageTimer(message: UiMessage, onShown: () -> Unit) {
    val currentOnShown by rememberUpdatedState(onShown)
    LaunchedEffect(message) {
        delay(message.durationMillis())
        currentOnShown()
    }
}

/** Название фильтра крупно по центру экрана (≥ 32 sp). */
@Composable
private fun FilterNameOverlay(filter: Filter, modifier: Modifier) {
    Text(
        text = stringResource(filter.nameRes()),
        color = LupaColors.OnBackground,
        fontSize = FILTER_NAME_SIZE,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .padding(horizontal = 24.dp)
            .background(ZOOM_INDICATOR_BACKGROUND, RoundedCornerShape(24.dp))
            .padding(horizontal = 28.dp, vertical = 16.dp)
            .testTag(TestTags.FILTER_NAME),
    )
}

private val FILTER_NAME_SIZE = 40.sp

@StringRes
private fun Filter.nameRes(): Int = when (this) {
    Filter.NORMAL -> R.string.filter_normal
    Filter.HIGH_CONTRAST -> R.string.filter_high_contrast
    Filter.GRAYSCALE -> R.string.filter_grayscale
    Filter.INVERT -> R.string.filter_invert
    Filter.YELLOW_ON_BLACK -> R.string.filter_yellow_on_black
}

/** Короткое сообщение над блоком управления («Сохранено», ошибки). */
@Composable
private fun MessageBanner(message: UiMessage) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(message.textRes()),
            color = LupaColors.OnBackground,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(ZOOM_INDICATOR_BACKGROUND, RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .testTag(TestTags.MESSAGE),
        )
    }
}

@StringRes
private fun UiMessage.textRes(): Int = when (this) {
    UiMessage.CaptureFailed -> R.string.message_capture_failed
    UiMessage.Saved -> R.string.message_saved
    UiMessage.SaveFailed -> R.string.message_save_failed
    is UiMessage.FilterName -> filter.nameRes()
}

private fun UiMessage.durationMillis(): Long = when (this) {
    UiMessage.CaptureFailed -> 1_000L
    UiMessage.Saved, UiMessage.SaveFailed -> 1_500L
    is UiMessage.FilterName -> 1_000L
}

/**
 * Прозрачный слой над превью. Живой режим: щипок меняет зум, двойной тап возвращает зум по умолчанию.
 * Стоп-кадр: щипок и перетаскивание двигают снимок, двойной тап — вид как в момент заморозки.
 */
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
                    if (zoom != 1f || pan != Offset.Zero) viewModel.onPinch(zoom, centroid.x, centroid.y, pan.x, pan.y)
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
 * Ползунок зума (на стоп-кадре — его масштаб ×1…×8): сам ползунок (с ручкой) занимает ширину экрана минус поля 16 dp, зона касания — 64 dp
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
        val frozen = state.frozen
        Slider(
            value = if (frozen != null) {
                ZoomMath.toSlider(frozen.viewport.scale, 1f, ZoomMath.FROZEN_MAX)
            } else {
                ZoomMath.toSlider(state.zoom, state.minZoom, state.maxZoom)
            },
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
private fun CameraPreview(bindPreview: (PreviewView) -> Unit, modifier: Modifier, filter: Filter) {
    val currentBindPreview by rememberUpdatedState(bindPreview)
    AndroidView(
        factory = { context -> PreviewView(context).also { currentBindPreview(it) } },
        modifier = modifier,
        // Фильтр считает видеокарта (RenderEffect / аппаратный слой), покадровой обработки на CPU нет.
        update = { Filters.applyTo(it, filter) },
    )
}
