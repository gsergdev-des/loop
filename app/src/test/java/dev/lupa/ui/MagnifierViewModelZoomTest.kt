package dev.lupa.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.lupa.camera.CameraCapabilities
import dev.lupa.camera.CameraStatus
import dev.lupa.camera.FakeCameraController
import dev.lupa.settings.LaunchZoomMode
import dev.lupa.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Зум в [MagnifierViewModel]: ползунок, щипок, двойной тап, кнопки громкости, зум при запуске, запоминание.
 * Камера и DataStore — на одном виртуальном времени, поэтому «~300 мс» проверяется точно.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MagnifierViewModelZoomTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = UnconfinedTestDispatcher(scheduler)
    private val job = Job()
    private val camera = FakeCameraController()
    private lateinit var repository: SettingsRepository

    // Камера ×1…×10: итоговый диапазон ×1…×20.
    private val ready = CameraStatus.Ready(CameraCapabilities(minZoom = 1f, maxZoom = 10f, hasFlash = true))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher + job),
            produceFile = { File(tmp.root, "settings.preferences_pb") },
        )
        repository = SettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        job.cancel()
        Dispatchers.resetMain()
    }

    private fun createViewModel() = MagnifierViewModel(camera, repository)

    private fun readyViewModel(): MagnifierViewModel =
        createViewModel().also { camera.emit(ready) }

    private val MagnifierViewModel.zoom get() = state.value.zoom

    // --- шкала ---

    @Test
    fun `zoom above the camera max sets the camera to max and stretches the preview`() {
        val vm = readyViewModel()

        vm.onSliderChange(ZoomPosition(15f))

        assertEquals(15f, vm.zoom, 1e-3f)
        assertEquals(10f, camera.zoomRatios.last(), 1e-3f)
        assertEquals(1.5f, vm.state.value.softScale, 1e-3f)
    }

    @Test
    fun `zoom below the camera max goes to the camera whole and the preview is not stretched`() {
        val vm = readyViewModel()

        vm.onSliderChange(ZoomPosition(4f))

        assertEquals(4f, camera.zoomRatios.last(), 1e-3f)
        assertEquals(1f, vm.state.value.softScale, 1e-3f)
    }

    @Test
    fun `slider ends are the ends of the total range`() {
        val vm = readyViewModel()

        vm.onSliderChange(1f)
        assertEquals(20f, vm.zoom, 1e-3f)
        assertEquals(2f, vm.state.value.softScale, 1e-3f)

        vm.onSliderChange(0f)
        assertEquals(1f, vm.zoom, 1e-3f)
        assertEquals(1f, camera.zoomRatios.last(), 1e-3f)
    }

    /** Положение ползунка, при котором зум равен [zoom] в диапазоне ×1…×20. */
    private fun ZoomPosition(zoom: Float): Float = (Math.log(zoom.toDouble()) / Math.log(20.0)).toFloat()

    // --- щипок, двойной тап ---

    @Test
    fun `pinch multiplies the zoom by the gesture factor within the range`() {
        val vm = readyViewModel()
        vm.onDoubleTap() // ×2

        vm.onPinch(1.5f)
        assertEquals(3f, vm.zoom, 1e-3f)

        vm.onPinch(100f)
        assertEquals(20f, vm.zoom, 1e-3f)

        vm.onPinch(0.001f)
        assertEquals(1f, vm.zoom, 1e-3f)
    }

    @Test
    fun `double tap sets the fixed zoom from the settings`() = runTest(dispatcher) {
        val vm = readyViewModel()
        repository.update { it.copy(fixedZoom = 4f) }
        vm.onSliderChange(1f)

        vm.onDoubleTap()

        assertEquals(4f, vm.zoom, 1e-3f)
        assertEquals(4f, camera.zoomRatios.last(), 1e-3f)
    }

    @Test
    fun `double tap is pinned to the maximum of this camera`() = runTest(dispatcher) {
        repository.update { it.copy(fixedZoom = 4f) }
        val vm = createViewModel()
        camera.emit(CameraStatus.Ready(CameraCapabilities(1f, 1f, false))) // итого ×1…×2

        vm.onPinch(0.5f)
        vm.onDoubleTap()

        assertEquals(2f, vm.zoom, 1e-3f)
        assertEquals(1f, camera.zoomRatios.last(), 1e-3f)
        assertEquals(2f, vm.state.value.softScale, 1e-3f)
    }

    // --- кнопки громкости ---

    @Test
    fun `volume keys step the zoom and eat the event when the option is on`() {
        val vm = readyViewModel()
        vm.onSliderChange(0f) // ×1

        val eaten = mutableListOf<Boolean>()
        val zooms = mutableListOf<Float>()
        repeat(3) { eaten += vm.onVolumeKey(up = true); zooms += vm.zoom }
        assertEquals(listOf(true, true, true), eaten)
        assertEquals(listOf(1.5f, 2f, 3f), zooms)

        assertTrue(vm.onVolumeKey(up = false))
        assertEquals(2f, vm.zoom, 1e-3f)
        assertEquals(2f, camera.zoomRatios.last(), 1e-3f)
    }

    @Test
    fun `holding a volume key repeats the step`() {
        val vm = readyViewModel()
        vm.onSliderChange(0f)

        repeat(6) { vm.onVolumeKey(up = true) } // 1.5 2 3 4 5 6

        assertEquals(6f, vm.zoom, 1e-3f)
    }

    @Test
    fun `volume keys are left to the system when the option is off`() = runTest(dispatcher) {
        repository.update { it.copy(volumeKeysZoom = false) }
        val vm = readyViewModel()
        val before = vm.zoom

        assertFalse(vm.onVolumeKey(up = true))
        assertFalse(vm.onVolumeKey(up = false))

        assertEquals(before, vm.zoom, 0f)
    }

    // --- зум при запуске ---

    @Test
    fun `last mode brings back the remembered zoom`() = runTest(dispatcher) {
        repository.rememberZoom(5f)
        val vm = createViewModel()
        camera.emit(ready)

        assertEquals(5f, vm.zoom, 1e-3f)
        assertEquals(5f, camera.zoomRatios.last(), 1e-3f)
    }

    @Test
    fun `first launch starts at times two`() {
        val vm = readyViewModel()

        assertEquals(2f, vm.zoom, 1e-3f)
        assertEquals(2f, camera.zoomRatios.last(), 1e-3f)
    }

    @Test
    fun `fixed mode starts at the fixed zoom whatever was remembered`() = runTest(dispatcher) {
        repository.rememberZoom(5f)
        repository.update { it.copy(launchZoomMode = LaunchZoomMode.FIXED, fixedZoom = 3f) }
        val vm = createViewModel()
        camera.emit(ready)

        assertEquals(3f, vm.zoom, 1e-3f)
    }

    @Test
    fun `remembered zoom above what this camera can do is pinned to the maximum`() = runTest(dispatcher) {
        repository.rememberZoom(50f)
        val vm = createViewModel()
        camera.emit(ready)

        assertEquals(20f, vm.zoom, 1e-3f)
        assertEquals(10f, camera.zoomRatios.last(), 1e-3f)
        assertEquals(2f, vm.state.value.softScale, 1e-3f)
    }

    @Test
    fun `every ready applies the zoom to the camera again`() {
        val vm = readyViewModel()
        vm.onSliderChange(ZoomPosition(5f))
        val applied = camera.zoomRatios.size

        camera.emit(CameraStatus.Starting)
        camera.emit(ready)

        assertEquals(applied + 1, camera.zoomRatios.size)
        assertEquals(5f, camera.zoomRatios.last(), 1e-3f)
        assertEquals(5f, vm.zoom, 1e-3f)
    }

    // --- запоминание ---

    @Test
    fun `a zoom change is written about 300 ms later, and a quick series only once as the last value`() = runTest(dispatcher) {
        val vm = readyViewModel()

        vm.onSliderChange(ZoomPosition(3f))
        scheduler.advanceTimeBy(200)
        vm.onSliderChange(ZoomPosition(7f))
        scheduler.advanceTimeBy(250)
        scheduler.runCurrent()
        assertNull(repository.remembered.first().lastZoom)

        scheduler.advanceTimeBy(100)
        scheduler.runCurrent()
        assertEquals(7f, repository.remembered.first().lastZoom!!, 1e-3f)
    }
}
