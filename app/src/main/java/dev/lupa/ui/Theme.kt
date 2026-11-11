package dev.lupa.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Цвета «Лупы»: чёрный фон, жёлтый акцент, чёрное на акценте. */
object LupaColors {
    val Background = Color.Black
    val OnBackground = Color.White
    val Accent = Color(0xFFFFD600)
    val OnAccent = Color.Black
    /** Неактивная кнопка поверх превью; содержимое на ней — [OnInactive]. */
    val Inactive = Color(0xCC2A2A2A)
    val OnInactive = Color.White
}

/**
 * Все роли схемы — из палитры проекта: базовые фиолетовые цвета M3 не должны попасть ни в один компонент.
 * Акцент — только у «активных» ролей; контейнеры и неактивные состояния (дорожка Slider, выключенный
 * Switch, диалоги) — тёмная [LupaColors.Inactive]; outline — нейтральный белый.
 */
private val LupaColorScheme = darkColorScheme(
    primary = LupaColors.Accent,
    onPrimary = LupaColors.OnAccent,
    primaryContainer = LupaColors.Accent,
    onPrimaryContainer = LupaColors.OnAccent,
    inversePrimary = LupaColors.OnAccent,
    secondary = LupaColors.Accent,
    onSecondary = LupaColors.OnAccent,
    secondaryContainer = LupaColors.Inactive,
    onSecondaryContainer = LupaColors.OnInactive,
    tertiary = LupaColors.Accent,
    onTertiary = LupaColors.OnAccent,
    tertiaryContainer = LupaColors.Inactive,
    onTertiaryContainer = LupaColors.OnInactive,
    background = LupaColors.Background,
    onBackground = LupaColors.OnBackground,
    surface = LupaColors.Background,
    onSurface = LupaColors.OnBackground,
    surfaceVariant = LupaColors.Inactive,
    onSurfaceVariant = LupaColors.OnInactive,
    // Чёрный tint — без цветного оттенка на приподнятых поверхностях.
    surfaceTint = LupaColors.Background,
    inverseSurface = LupaColors.OnBackground,
    inverseOnSurface = LupaColors.Background,
    error = LupaColors.Accent,
    onError = LupaColors.OnAccent,
    errorContainer = LupaColors.Inactive,
    onErrorContainer = LupaColors.OnInactive,
    outline = LupaColors.OnBackground,
    outlineVariant = LupaColors.Inactive,
    scrim = LupaColors.Background,
    surfaceBright = LupaColors.Inactive,
    surfaceDim = LupaColors.Background,
    surfaceContainer = LupaColors.Inactive,
    surfaceContainerHigh = LupaColors.Inactive,
    surfaceContainerHighest = LupaColors.Inactive,
    surfaceContainerLow = LupaColors.Background,
    surfaceContainerLowest = LupaColors.Background,
    primaryFixed = LupaColors.Accent,
    primaryFixedDim = LupaColors.Accent,
    onPrimaryFixed = LupaColors.OnAccent,
    onPrimaryFixedVariant = LupaColors.OnAccent,
    secondaryFixed = LupaColors.Accent,
    secondaryFixedDim = LupaColors.Accent,
    onSecondaryFixed = LupaColors.OnAccent,
    onSecondaryFixedVariant = LupaColors.OnAccent,
    tertiaryFixed = LupaColors.Accent,
    tertiaryFixedDim = LupaColors.Accent,
    onTertiaryFixed = LupaColors.OnAccent,
    onTertiaryFixedVariant = LupaColors.OnAccent,
)

/** Тема приложения: всегда тёмная, независимо от системной темы. */
@Composable
fun LupaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LupaColorScheme, content = content)
}
