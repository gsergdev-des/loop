package dev.lupa.ui

import dev.lupa.settings.ButtonSize
import dev.lupa.settings.LaunchZoomMode
import dev.lupa.settings.SettingsRepository
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.lupa.camera.FakeCameraController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** `updateSettings`: изменение видно в состоянии сразу и лежит в репозитории. */
@OptIn(ExperimentalCoroutinesApi::class)
class MagnifierViewModelSettingsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val job = Job()
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = SettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(dispatcher + job),
                produceFile = { File(tmp.root, "settings.preferences_pb") },
            ),
        )
    }

    @After
    fun tearDown() {
        job.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `update settings reaches the state and the repository`() = runTest(dispatcher) {
        val vm = MagnifierViewModel(FakeCameraController(), repository, FakeSnapshotSaver())

        vm.updateSettings { it.copy(buttonSize = ButtonSize.LARGE, launchZoomMode = LaunchZoomMode.FIXED, haptics = false) }

        assertEquals(ButtonSize.LARGE, vm.state.value.settings.buttonSize)
        val saved = repository.settings.first()
        assertEquals(ButtonSize.LARGE, saved.buttonSize)
        assertEquals(LaunchZoomMode.FIXED, saved.launchZoomMode)
        assertEquals(false, saved.haptics)
    }
}
