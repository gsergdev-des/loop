package dev.lupa.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.lupa.filters.Filter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val jobs = mutableListOf<Job>()
    private val file: File by lazy { File(tmp.root, "settings.preferences_pb") }

    /** Новый DataStore на том же файле — как новый запуск приложения. */
    private fun openRepository(): Pair<SettingsRepository, Job> {
        val job = Job()
        jobs += job
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { file },
        )
        return SettingsRepository(dataStore) to job
    }

    @After
    fun tearDown() {
        jobs.forEach { it.cancel() }
    }

    @Test
    fun `defaults match the spec`() = runTest {
        val (repo, _) = openRepository()
        val s = repo.settings.first()
        // История 43: фонарик при запуске выкл., громкость вкл., «последний», фиксированный ×2,
        // не гасить экран вкл., вибрация вкл., обычные кнопки.
        assertFalse(s.torchOnLaunch)
        assertTrue(s.volumeKeysZoom)
        assertEquals(LaunchZoomMode.LAST, s.launchZoomMode)
        assertEquals(2f, s.fixedZoom)
        assertTrue(s.keepScreenOn)
        assertTrue(s.haptics)
        assertEquals(ButtonSize.NORMAL, s.buttonSize)
    }

    @Test
    fun `nothing is remembered on first launch`() = runTest {
        val (repo, _) = openRepository()
        val r = repo.remembered.first()
        assertNull(r.lastZoom)
        assertEquals(Filter.NORMAL, r.lastFilter)
    }

    @Test
    fun `every settings field survives a restart`() = runTest {
        val (repo, job) = openRepository()
        val changed = Settings(
            torchOnLaunch = true,
            volumeKeysZoom = false,
            launchZoomMode = LaunchZoomMode.FIXED,
            fixedZoom = 3f,
            keepScreenOn = false,
            haptics = false,
            buttonSize = ButtonSize.LARGE,
        )
        repo.update { changed }
        assertEquals(changed, repo.settings.first())

        job.cancelAndJoin()
        val (reopened, _) = openRepository()
        assertEquals(changed, reopened.settings.first())
    }

    @Test
    fun `update sees the current settings`() = runTest {
        val (repo, _) = openRepository()
        repo.update { it.copy(haptics = false) }
        repo.update { it.copy(buttonSize = ButtonSize.LARGE) }
        repo.update { it.copy(fixedZoom = 1.5f) }
        assertEquals(
            Settings(haptics = false, buttonSize = ButtonSize.LARGE, fixedZoom = 1.5f),
            repo.settings.first(),
        )
    }

    @Test
    fun `last zoom and filter survive a restart`() = runTest {
        val (repo, job) = openRepository()
        repo.rememberZoom(4.5f)
        repo.rememberFilter(Filter.YELLOW_ON_BLACK)
        assertEquals(Remembered(4.5f, Filter.YELLOW_ON_BLACK), repo.remembered.first())

        job.cancelAndJoin()
        val (reopened, _) = openRepository()
        assertEquals(Remembered(4.5f, Filter.YELLOW_ON_BLACK), reopened.remembered.first())
    }

    @Test
    fun `remembering does not touch settings`() = runTest {
        val (repo, _) = openRepository()
        repo.update { it.copy(torchOnLaunch = true) }
        repo.rememberZoom(6f)
        repo.rememberFilter(Filter.INVERT)
        assertEquals(Settings(torchOnLaunch = true), repo.settings.first())
    }
}
