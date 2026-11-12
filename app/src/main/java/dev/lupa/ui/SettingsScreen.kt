package dev.lupa.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lupa.R
import dev.lupa.settings.ButtonSize
import dev.lupa.settings.LaunchZoomMode
import dev.lupa.settings.Settings
import dev.lupa.settings.SettingsRepository

/**
 * Экран настроек: один список из шести пунктов в порядке брифа. Изменения уходят в [onChange] сразу.
 * Строки не ниже 64 dp, тексты не меньше 16 sp.
 */
@Composable
fun SettingsScreen(settings: Settings, onChange: ((Settings) -> Settings) -> Unit, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(LupaColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag(TestTags.SETTINGS_SCREEN),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            val back = stringResource(R.string.settings_back)
            Box(
                Modifier
                    .size(64.dp)
                    .clickable(role = Role.Button, onClickLabel = back, onClick = onBack)
                    .testTag(TestTags.BTN_BACK),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = back, tint = LupaColors.OnBackground)
            }
            Text(
                text = stringResource(R.string.settings_title),
                color = LupaColors.OnBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SwitchRow(R.string.setting_torch_on_launch, settings.torchOnLaunch, TestTags.SETTING_TORCH_ON_LAUNCH) { on ->
                onChange { it.copy(torchOnLaunch = on) }
            }
            SwitchRow(R.string.setting_volume_keys, settings.volumeKeysZoom, TestTags.SETTING_VOLUME_KEYS) { on ->
                onChange { it.copy(volumeKeysZoom = on) }
            }
            ChoiceRow(R.string.setting_launch_zoom, TestTags.SETTING_LAUNCH_ZOOM) {
                OptionRow {
                    Option(
                        R.string.setting_launch_zoom_last, settings.launchZoomMode == LaunchZoomMode.LAST,
                        TestTags.OPTION_ZOOM_LAST,
                    ) { onChange { it.copy(launchZoomMode = LaunchZoomMode.LAST) } }
                    Option(
                        R.string.setting_launch_zoom_fixed, settings.launchZoomMode == LaunchZoomMode.FIXED,
                        TestTags.OPTION_ZOOM_FIXED,
                    ) { onChange { it.copy(launchZoomMode = LaunchZoomMode.FIXED) } }
                }
                if (settings.launchZoomMode == LaunchZoomMode.FIXED) {
                    OptionRow {
                        for (zoom in SettingsRepository.FIXED_ZOOM_CHOICES) {
                            Option(
                                label = zoomLabel(zoom), selected = settings.fixedZoom == zoom,
                                tag = TestTags.fixedZoomOption(zoom),
                            ) { onChange { it.copy(fixedZoom = zoom) } }
                        }
                    }
                }
            }
            SwitchRow(R.string.setting_keep_screen_on, settings.keepScreenOn, TestTags.SETTING_KEEP_SCREEN_ON) { on ->
                onChange { it.copy(keepScreenOn = on) }
            }
            SwitchRow(R.string.setting_haptics, settings.haptics, TestTags.SETTING_HAPTICS) { on ->
                onChange { it.copy(haptics = on) }
            }
            ChoiceRow(R.string.setting_button_size, TestTags.SETTING_BUTTON_SIZE) {
                OptionRow {
                    Option(
                        R.string.setting_button_size_normal, settings.buttonSize == ButtonSize.NORMAL,
                        TestTags.OPTION_BUTTONS_NORMAL,
                    ) { onChange { it.copy(buttonSize = ButtonSize.NORMAL) } }
                    Option(
                        R.string.setting_button_size_large, settings.buttonSize == ButtonSize.LARGE,
                        TestTags.OPTION_BUTTONS_LARGE,
                    ) { onChange { it.copy(buttonSize = ButtonSize.LARGE) } }
                }
            }
        }
    }
}

/** «×1.5», «×2»: так, как в брифе. */
private fun zoomLabel(zoom: Float): String =
    if (zoom == zoom.toInt().toFloat()) "×${zoom.toInt()}" else "×$zoom"

private val ROW_MIN_HEIGHT = 72.dp
private val ROW_TEXT_SIZE = 20.sp

/** Строка с переключателем: нажатие в любом месте строки. */
@Composable
private fun SwitchRow(@StringRes title: Int, checked: Boolean, tag: String, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(title),
            color = LupaColors.OnBackground,
            fontSize = ROW_TEXT_SIZE,
            modifier = Modifier.weight(1f),
        )
        // Цвета заданы явно: у включённого Switch ручка в нажатии берётся из primaryContainer — жёлтая на жёлтом.
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = LupaColors.OnAccent,
                checkedTrackColor = LupaColors.Accent,
                checkedBorderColor = LupaColors.Accent,
                checkedIconColor = LupaColors.Accent,
                uncheckedThumbColor = LupaColors.OnInactive,
                uncheckedTrackColor = LupaColors.Inactive,
                uncheckedBorderColor = LupaColors.OnInactive,
                uncheckedIconColor = LupaColors.Inactive,
            ),
        )
    }
    HorizontalDivider(color = LupaColors.Inactive)
}

/** Пункт с выбором: заголовок и под ним ряды вариантов. */
@Composable
private fun ChoiceRow(@StringRes title: Int, tag: String, options: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = stringResource(title), color = LupaColors.OnBackground, fontSize = ROW_TEXT_SIZE)
        options()
    }
    HorizontalDivider(color = LupaColors.Inactive)
}

@Composable
private fun OptionRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Option(
    @StringRes label: Int, selected: Boolean, tag: String, onClick: () -> Unit,
) = Option(stringResource(label), selected, tag, onClick)

/** Вариант выбора: выбранный залит жёлтым с чёрным текстом, остальные тёмные. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.Option(
    label: String, selected: Boolean, tag: String, onClick: () -> Unit,
) {
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) LupaColors.Accent else LupaColors.Inactive)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) LupaColors.OnAccent else LupaColors.OnInactive,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}
