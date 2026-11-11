package dev.lupa.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.lupa.filters.Filter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class LaunchZoomMode { LAST, FIXED }

enum class ButtonSize { NORMAL, LARGE }

/** Пользовательские настройки. Значения по умолчанию — из спецификации (история 43). */
data class Settings(
    val torchOnLaunch: Boolean = false,
    val volumeKeysZoom: Boolean = true,
    val launchZoomMode: LaunchZoomMode = LaunchZoomMode.LAST,
    /** Один из [SettingsRepository.FIXED_ZOOM_CHOICES]. */
    val fixedZoom: Float = 2f,
    val keepScreenOn: Boolean = true,
    val haptics: Boolean = true,
    val buttonSize: ButtonSize = ButtonSize.NORMAL,
)

/** Запомненное между запусками. `lastZoom == null` — первый запуск. */
data class Remembered(val lastZoom: Float?, val lastFilter: Filter)

/** Настройки и запомненное состояние в одном DataStore Preferences. */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    /** Данные файла; сбой чтения — как пустой файл (значения по умолчанию). */
    private val preferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val settings: Flow<Settings> = preferences.map { it.toSettings() }.distinctUntilChanged()

    val remembered: Flow<Remembered> = preferences.map { it.toRemembered() }.distinctUntilChanged()

    suspend fun update(transform: (Settings) -> Settings) {
        dataStore.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    suspend fun rememberZoom(zoom: Float) {
        dataStore.edit { it[Keys.LAST_ZOOM] = zoom }
    }

    suspend fun rememberFilter(filter: Filter) {
        dataStore.edit { it[Keys.LAST_FILTER] = filter.name }
    }

    companion object {
        val FIXED_ZOOM_CHOICES = listOf(1.5f, 2f, 3f, 4f)

        /** Репозиторий над единственным на процесс DataStore «settings». */
        fun create(context: Context): SettingsRepository =
            SettingsRepository(context.applicationContext.settingsDataStore)
    }
}

/** Зум при запуске: FIXED → fixedZoom; LAST → lastZoom ?: ×2. */
fun launchZoom(settings: Settings, remembered: Remembered): Float = when (settings.launchZoomMode) {
    LaunchZoomMode.FIXED -> settings.fixedZoom
    LaunchZoomMode.LAST -> remembered.lastZoom ?: FIRST_LAUNCH_ZOOM
}

/** Зум самого первого запуска; равен `ZoomMath.DEFAULT_ZOOM` (история 16). */
private const val FIRST_LAUNCH_ZOOM = 2f

/** Делегат держит один экземпляр DataStore на процесс. */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

private object Keys {
    val TORCH_ON_LAUNCH = booleanPreferencesKey("torch_on_launch")
    val VOLUME_KEYS_ZOOM = booleanPreferencesKey("volume_keys_zoom")
    val LAUNCH_ZOOM_MODE = stringPreferencesKey("launch_zoom_mode")
    val FIXED_ZOOM = floatPreferencesKey("fixed_zoom")
    val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    val HAPTICS = booleanPreferencesKey("haptics")
    val BUTTON_SIZE = stringPreferencesKey("button_size")
    val LAST_ZOOM = floatPreferencesKey("last_zoom")
    val LAST_FILTER = stringPreferencesKey("last_filter")
}

private fun Preferences.toSettings(): Settings {
    val defaults = Settings()
    return Settings(
        torchOnLaunch = this[Keys.TORCH_ON_LAUNCH] ?: defaults.torchOnLaunch,
        volumeKeysZoom = this[Keys.VOLUME_KEYS_ZOOM] ?: defaults.volumeKeysZoom,
        launchZoomMode = enumOrDefault(this[Keys.LAUNCH_ZOOM_MODE], defaults.launchZoomMode),
        fixedZoom = this[Keys.FIXED_ZOOM] ?: defaults.fixedZoom,
        keepScreenOn = this[Keys.KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
        haptics = this[Keys.HAPTICS] ?: defaults.haptics,
        buttonSize = enumOrDefault(this[Keys.BUTTON_SIZE], defaults.buttonSize),
    )
}

private fun MutablePreferences.write(settings: Settings) {
    this[Keys.TORCH_ON_LAUNCH] = settings.torchOnLaunch
    this[Keys.VOLUME_KEYS_ZOOM] = settings.volumeKeysZoom
    this[Keys.LAUNCH_ZOOM_MODE] = settings.launchZoomMode.name
    this[Keys.FIXED_ZOOM] = settings.fixedZoom
    this[Keys.KEEP_SCREEN_ON] = settings.keepScreenOn
    this[Keys.HAPTICS] = settings.haptics
    this[Keys.BUTTON_SIZE] = settings.buttonSize.name
}

private fun Preferences.toRemembered() = Remembered(
    lastZoom = this[Keys.LAST_ZOOM],
    lastFilter = enumOrDefault(this[Keys.LAST_FILTER], Filter.NORMAL),
)

/** Имя константы из файла; неизвестное (файл от другой версии) — значение по умолчанию. */
private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default
