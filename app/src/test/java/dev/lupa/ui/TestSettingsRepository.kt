package dev.lupa.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.lupa.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import java.io.File

/** Репозиторий настроек на временном файле; [scope] тест отменяет в конце. */
fun testSettingsRepository(dir: File, scope: CoroutineScope): SettingsRepository =
    SettingsRepository(
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(dir, "settings.preferences_pb") }),
    )
