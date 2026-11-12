package dev.lupa.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lupa.R
import dev.lupa.filters.Filter
import dev.lupa.settings.ButtonSize

/** Размеры кнопки нижней панели; настройка «Размер кнопок» выбирает [Normal] или [Large]. */
data class ButtonMetrics(val height: Dp, val icon: Dp, val label: TextUnit) {
    companion object {
        val Normal = ButtonMetrics(height = 80.dp, icon = 36.dp, label = 16.sp)
        val Large = ButtonMetrics(height = 96.dp, icon = 44.dp, label = 18.sp)
    }
}

/** Размеры кнопок по настройке. */
fun ButtonSize.metrics(): ButtonMetrics = when (this) {
    ButtonSize.NORMAL -> ButtonMetrics.Normal
    ButtonSize.LARGE -> ButtonMetrics.Large
}

/**
 * Ряд из четырёх кнопок: Фонарик · Стоп-кадр · Фильтр · Снимок. Ширина делится поровну.
 * «Стоп-кадр» залит, пока кадр заморожен, и крутит «занято», пока снимок делается.
 * «Фильтр» залит, пока фильтр не «Обычный»; «Снимок» крутит «занято», пока снимок сохраняется.
 */
@Composable
fun ControlPanel(
    state: MagnifierUiState,
    viewModel: MagnifierViewModel,
    modifier: Modifier = Modifier,
    metrics: ButtonMetrics = ButtonMetrics.Normal,
) {
    val haptic = rememberHaptic(state.settings.haptics)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state.hasFlash) {
            PanelButton(
                tag = TestTags.BTN_TORCH, icon = R.drawable.ic_torch, label = R.string.button_torch,
                active = state.torchOn, metrics = metrics, onClick = { haptic(); viewModel.onTorchClick() },
            )
        }
        PanelButton(
            tag = TestTags.BTN_FREEZE, icon = R.drawable.ic_freeze, label = R.string.button_freeze,
            active = state.frozen != null, busy = state.busy == Busy.FREEZING,
            metrics = metrics, onClick = { haptic(); viewModel.onFreezeClick() },
        )
        PanelButton(
            tag = TestTags.BTN_FILTER, icon = R.drawable.ic_filter, label = R.string.button_filter,
            active = state.filter != Filter.NORMAL, metrics = metrics, onClick = { haptic(); viewModel.onFilterClick() },
        )
        PanelButton(
            tag = TestTags.BTN_SNAPSHOT, icon = R.drawable.ic_camera, label = R.string.button_snapshot,
            active = false, busy = state.busy == Busy.SAVING, metrics = metrics, onClick = { haptic(); viewModel.onSnapshotClick() },
        )
    }
}

/**
 * Кнопка: иконка и подпись. Активная залита жёлтым с чёрным содержимым, неактивная — тёмная со светлым.
 * Занятая ([busy]) вместо иконки крутит индикатор: нажатие принято, повторные VM пропускает.
 */
@Composable
private fun RowScope.PanelButton(
    tag: String,
    @DrawableRes icon: Int,
    @StringRes label: Int,
    active: Boolean,
    metrics: ButtonMetrics,
    onClick: () -> Unit,
    busy: Boolean = false,
) {
    val fill = if (active) LupaColors.Accent else LupaColors.Inactive
    val content = if (active) LupaColors.OnAccent else LupaColors.OnInactive
    Column(
        modifier = Modifier
            .weight(1f)
            .height(metrics.height)
            .clip(RoundedCornerShape(16.dp))
            .background(fill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { selected = active }
            .testTag(tag),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (busy) {
            CircularProgressIndicator(
                color = content,
                strokeWidth = 4.dp,
                modifier = Modifier.size(metrics.icon).padding(2.dp),
            )
        } else {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(metrics.icon).testTag(TestTags.iconOf(tag)),
            )
        }
        Text(
            text = stringResource(label),
            color = content,
            fontSize = metrics.label,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
        )
    }
}
