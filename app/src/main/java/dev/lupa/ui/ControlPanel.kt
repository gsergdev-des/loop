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

/** Размеры кнопки нижней панели; крупный режим (таск 07) подставит [Large]. */
data class ButtonMetrics(val height: Dp, val icon: Dp, val label: TextUnit) {
    companion object {
        val Normal = ButtonMetrics(height = 80.dp, icon = 36.dp, label = 16.sp)
        val Large = ButtonMetrics(height = 104.dp, icon = 48.dp, label = 20.sp)
    }
}

/**
 * Ряд из четырёх кнопок: Фонарик · Стоп-кадр · Фильтр · Снимок. Ширина делится поровну.
 * Кнопки «Стоп-кадр», «Фильтр» и «Снимок» пока без действия на стороне VM (таски 05–06).
 */
@Composable
fun ControlPanel(
    state: MagnifierUiState,
    viewModel: MagnifierViewModel,
    modifier: Modifier = Modifier,
    metrics: ButtonMetrics = ButtonMetrics.Normal,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state.hasFlash) {
            PanelButton(
                tag = TestTags.BTN_TORCH, icon = R.drawable.ic_torch, label = R.string.button_torch,
                active = state.torchOn, metrics = metrics, onClick = viewModel::onTorchClick,
            )
        }
        PanelButton(
            tag = TestTags.BTN_FREEZE, icon = R.drawable.ic_freeze, label = R.string.button_freeze,
            active = false, metrics = metrics, onClick = viewModel::onFreezeClick,
        )
        PanelButton(
            tag = TestTags.BTN_FILTER, icon = R.drawable.ic_filter, label = R.string.button_filter,
            active = false, metrics = metrics, onClick = viewModel::onFilterClick,
        )
        PanelButton(
            tag = TestTags.BTN_SNAPSHOT, icon = R.drawable.ic_camera, label = R.string.button_snapshot,
            active = false, metrics = metrics, onClick = viewModel::onSnapshotClick,
        )
    }
}

/** Кнопка: иконка и подпись. Активная залита жёлтым с чёрным содержимым, неактивная — тёмная со светлым. */
@Composable
private fun RowScope.PanelButton(
    tag: String,
    @DrawableRes icon: Int,
    @StringRes label: Int,
    active: Boolean,
    metrics: ButtonMetrics,
    onClick: () -> Unit,
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
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(metrics.icon).testTag(TestTags.iconOf(tag)),
        )
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
