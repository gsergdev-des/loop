package dev.lupa.ui

import dev.lupa.camera.CameraCapabilities
import dev.lupa.camera.CameraError
import dev.lupa.camera.CameraStatus
import dev.lupa.camera.FakeCameraController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Состояние камеры в [MagnifierViewModel]: Starting / Ready / Error и «Повторить». */
@OptIn(ExperimentalCoroutinesApi::class)
class MagnifierViewModelCameraTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(UnconfinedTestDispatcher() + Job())
    private val camera = FakeCameraController()
    private lateinit var viewModel: MagnifierViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = MagnifierViewModel(camera, testSettingsRepository(tmp.root, scope), FakeSnapshotSaver())
    }

    @After
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `ready camera gives the total zoom range and the flash`() {
        // Камера ×0.6…×10 со вспышкой: итоговый зум от max(1, 0.6) = ×1 до 10 × 2 (программный ×2 сверху) = ×20.
        val ready = CameraStatus.Ready(CameraCapabilities(minZoom = 0.6f, maxZoom = 10f, hasFlash = true))
        camera.emit(ready)

        val state = viewModel.state.value
        assertEquals(ready, state.camera)
        assertEquals(1f, state.minZoom, 0f)
        assertEquals(20f, state.maxZoom, 0f)
        assertTrue(state.hasFlash)
    }

    @Test
    fun `every ready brings the capabilities anew - camera without zoom and flash still gets the soft zoom on top`() {
        camera.emit(CameraStatus.Ready(CameraCapabilities(minZoom = 1f, maxZoom = 10f, hasFlash = true)))
        camera.emit(CameraStatus.Starting)
        val reconnected = CameraStatus.Ready(CameraCapabilities(minZoom = 1f, maxZoom = 1f, hasFlash = false))
        camera.emit(reconnected)

        val state = viewModel.state.value
        assertEquals(reconnected, state.camera)
        assertEquals(1f, state.minZoom, 0f)
        assertEquals(2f, state.maxZoom, 0f)
        assertFalse(state.hasFlash)
    }

    @Test
    fun `camera error is shown with its reason`() {
        camera.emit(CameraStatus.Ready(CameraCapabilities(1f, 10f, true)))
        camera.emit(CameraStatus.Error(CameraError.IN_USE))

        assertEquals(CameraStatus.Error(CameraError.IN_USE), viewModel.state.value.camera)
    }

    @Test
    fun `retry asks the camera to reconnect and the next ready replaces the error`() {
        camera.emit(CameraStatus.Error(CameraError.FAILED))

        viewModel.onRetry()
        assertEquals(1, camera.retryCount)

        camera.emit(CameraStatus.Starting)
        assertEquals(CameraStatus.Starting, viewModel.state.value.camera)
        camera.emit(CameraStatus.Ready(CameraCapabilities(1f, 8f, true)))
        assertEquals(CameraStatus.Ready(CameraCapabilities(1f, 8f, true)), viewModel.state.value.camera)
        assertEquals(16f, viewModel.state.value.maxZoom, 0f)
    }
}
