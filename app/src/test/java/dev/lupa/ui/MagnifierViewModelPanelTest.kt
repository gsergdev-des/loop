package dev.lupa.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.lupa.camera.CameraCapabilities
import dev.lupa.camera.CameraController
import dev.lupa.camera.CameraStatus
import dev.lupa.camera.FakeCameraController
import dev.lupa.settings.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Фонарик и фокус по тапу в [MagnifierViewModel]; камера и DataStore — на одном виртуальном времени. */
@OptIn(ExperimentalCoroutinesApi::class)
class MagnifierViewModelPanelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = UnconfinedTestDispatcher(scheduler)
    private val job = Job()
    private val fake = FakeCameraController()
    private lateinit var repository: SettingsRepository

    /** Фокус, который не завершается, пока тест не скажет. */
    private val focusGate = CompletableDeferred<Boolean>()
    private val camera: CameraController = object : CameraController by fake {
        override suspend fun focusAt(x: Float, y: Float): Boolean {
            fake.focusAt(x, y)
            return focusGate.await()
        }
    }

    private val withFlash = CameraStatus.Ready(CameraCapabilities(1f, 10f, hasFlash = true))
    private val noFlash = CameraStatus.Ready(CameraCapabilities(1f, 10f, hasFlash = false))

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

    private fun readyViewModel(status: CameraStatus = withFlash): MagnifierViewModel =
        MagnifierViewModel(camera, repository).also { fake.emit(status) }

    // --- фонарик ---

    @Test
    fun `torch click turns the torch on and the next click turns it off`() {
        val vm = readyViewModel()

        vm.onTorchClick()
        assertEquals(listOf(true), fake.torchCalls)
        assertTrue(vm.state.value.torchOn)

        vm.onTorchClick()
        assertEquals(listOf(true, false), fake.torchCalls)
        assertEquals(false, vm.state.value.torchOn)
    }

    @Test
    fun `torch click without a flash does nothing`() {
        val vm = readyViewModel(noFlash)

        vm.onTorchClick()

        assertTrue(fake.torchCalls.isEmpty())
        assertEquals(false, vm.state.value.torchOn)
    }

    @Test
    fun `first ready lights the torch when the launch option is on`() = runTest(dispatcher) {
        repository.update { it.copy(torchOnLaunch = true) }
        val vm = readyViewModel()

        assertEquals(listOf(true), fake.torchCalls)
        assertTrue(vm.state.value.torchOn)
    }

    @Test
    fun `torch stays off at launch by default`() {
        val vm = readyViewModel()

        assertTrue(fake.torchCalls.isEmpty())
        assertEquals(false, vm.state.value.torchOn)
    }

    @Test
    fun `every next ready brings back the torch and the zoom as they were`() {
        val vm = readyViewModel()
        vm.onTorchClick()
        vm.onSliderChange(0.5f)
        val zoom = vm.state.value.zoom
        fake.torchCalls.clear()

        fake.emit(CameraStatus.Starting)
        fake.emit(withFlash)

        assertEquals(listOf(true), fake.torchCalls)
        assertEquals(zoom, vm.state.value.zoom, 1e-3f)
    }

    @Test
    fun `a torch the user turned off is not lit again by a later ready`() = runTest(dispatcher) {
        repository.update { it.copy(torchOnLaunch = true) }
        val vm = readyViewModel()
        vm.onTorchClick()
        fake.torchCalls.clear()

        fake.emit(CameraStatus.Starting)
        fake.emit(withFlash)

        assertTrue(fake.torchCalls.isEmpty())
        assertEquals(false, vm.state.value.torchOn)
    }

    // --- фокус ---

    @Test
    fun `tap with a soft zoom reaches the camera in preview coordinates`() {
        val vm = readyViewModel()
        vm.onViewSize(1000f, 2000f)
        vm.onSliderChange(1f) // ×20: камера ×10, программный масштаб ×2

        vm.onTap(600f, 1400f)

        // Вокруг центра (500, 1000) масштаб ×2: 500 + 100 / 2, 1000 + 400 / 2.
        val (x, y) = fake.focusCalls.single()
        assertEquals(550f, x, 1e-3f)
        assertEquals(1200f, y, 1e-3f)
    }

    @Test
    fun `focus ring sits where the finger was while the focus is running`() {
        val vm = readyViewModel()
        vm.onViewSize(1000f, 2000f)

        vm.onTap(300f, 700f)

        assertEquals(300f to 700f, vm.state.value.focusPoint)
    }

    @Test
    fun `focus ring goes away when the focus is done`() {
        val vm = readyViewModel()
        vm.onViewSize(1000f, 2000f)
        vm.onTap(300f, 700f)

        focusGate.complete(true)

        assertNull(vm.state.value.focusPoint)
    }

    @Test
    fun `focus ring goes away after 1_5 s even if the focus is still running`() {
        val vm = readyViewModel()
        vm.onViewSize(1000f, 2000f)
        vm.onTap(300f, 700f)

        scheduler.advanceTimeBy(1400)
        assertNotNull(vm.state.value.focusPoint)
        scheduler.advanceTimeBy(200)

        assertNull(vm.state.value.focusPoint)
    }

    @Test
    fun `tap while the camera is not ready does not focus`() {
        val vm = MagnifierViewModel(camera, repository)
        vm.onViewSize(1000f, 2000f)

        vm.onTap(300f, 700f)

        assertTrue(fake.focusCalls.isEmpty())
        assertNull(vm.state.value.focusPoint)
    }

    @Test
    fun `filter and snapshot buttons without an action change nothing yet`() {
        val vm = readyViewModel()
        val before = vm.state.value

        vm.onFilterClick()
        vm.onSnapshotClick()

        assertEquals(before, vm.state.value)
    }
}
