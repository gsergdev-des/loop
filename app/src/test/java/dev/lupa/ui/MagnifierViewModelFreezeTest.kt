package dev.lupa.ui

import android.graphics.Bitmap
import dev.lupa.camera.CameraCapabilities
import dev.lupa.camera.CameraStatus
import dev.lupa.camera.CaptureException
import dev.lupa.camera.FakeCameraController
import dev.lupa.snapshot.FrozenViewport
import dev.lupa.zoom.ZoomMath
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Стоп-кадр в [MagnifierViewModel]: съёмка, занятость, ошибка, возврат к живой камере.
 * Камера ×1…×10 (итоговый зум ×1…×20), область превью 1000×2000 px. Robolectric — ради настоящего Bitmap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MagnifierViewModelFreezeTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = UnconfinedTestDispatcher(scheduler)
    private val job = Job()
    private val camera = FakeCameraController()
    private val shot: Bitmap = Bitmap.createBitmap(30, 60, Bitmap.Config.ARGB_8888)
    private val ready = CameraStatus.Ready(CameraCapabilities(minZoom = 1f, maxZoom = 10f, hasFlash = true))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        camera.captureBitmap = shot
    }

    @After
    fun tearDown() {
        job.cancel()
        Dispatchers.resetMain()
    }

    private fun readyViewModel(): MagnifierViewModel =
        MagnifierViewModel(camera, testSettingsRepository(tmp.root, CoroutineScope(dispatcher + job)), FakeSnapshotSaver()).also {
            camera.emit(ready)
            it.onViewSize(1000f, 2000f)
        }

    private fun assertViewport(expected: FrozenViewport, actual: FrozenViewport?) {
        assertNotNull("стоп-кадра нет", actual)
        assertEquals("scale", expected.scale, actual!!.scale, 1e-3f)
        assertEquals("offsetX", expected.offsetX, actual.offsetX, 1e-2f)
        assertEquals("offsetY", expected.offsetY, actual.offsetY, 1e-2f)
    }

    /** Итоговый зум 15: камера ×10, программный масштаб ×1.5. */
    private fun MagnifierViewModel.zoomTo15() {
        onSliderChange(ZoomMath.toSlider(15f, 1f, 20f))
        assertEquals(15f, state.value.zoom, 1e-3f)
        assertEquals(1.5f, state.value.softScale, 1e-3f)
    }

    @Test
    fun `freeze shows the shot at the preview soft scale with the camera zoom as base`() {
        val vm = readyViewModel()
        vm.zoomTo15()

        vm.onFreezeClick()

        val frozen = vm.state.value.frozen
        assertNotNull(frozen)
        assertSame(shot, frozen!!.bitmap)
        assertEquals(10f, frozen.baseZoom, 1e-3f)
        assertViewport(FrozenViewport(scale = 1.5f), frozen.viewport)
        assertEquals(Busy.NONE, vm.state.value.busy)
        assertEquals(15f, vm.state.value.indicatorZoom, 1e-3f)
        assertEquals(1, camera.captureCount)
    }

    @Test
    fun `while the shot is being taken the screen is busy and more presses are ignored`() {
        val gate = CompletableDeferred<Unit>()
        camera.captureGate = gate
        val vm = readyViewModel()

        vm.onFreezeClick()
        vm.onFreezeClick()
        vm.onFreezeClick()

        assertEquals(Busy.FREEZING, vm.state.value.busy)
        assertNull(vm.state.value.frozen)
        assertEquals(1, camera.captureCount)

        gate.complete(Unit)

        assertEquals(Busy.NONE, vm.state.value.busy)
        assertSame(shot, vm.state.value.frozen?.bitmap)
        assertEquals(1, camera.captureCount)
    }

    @Test
    fun `live zoom does not move while the shot is being taken`() {
        camera.captureGate = CompletableDeferred()
        val vm = readyViewModel()
        vm.onFreezeClick()
        val ratios = camera.zoomRatios.size

        vm.onSliderChange(1f)
        vm.onPinch(3f)
        vm.onVolumeKey(up = true)

        assertEquals(ZoomMath.DEFAULT_ZOOM, vm.state.value.zoom, 1e-3f)
        assertEquals(ratios, camera.zoomRatios.size)
    }

    @Test
    fun `failed shot says so until the screen has shown it and the live picture stays`() {
        camera.captureError = CaptureException("нет снимка")
        val vm = readyViewModel()
        vm.zoomTo15()

        vm.onFreezeClick()

        assertEquals(UiMessage.CaptureFailed, vm.state.value.message)
        assertNull(vm.state.value.frozen)
        assertEquals(Busy.NONE, vm.state.value.busy)
        assertEquals(15f, vm.state.value.zoom, 1e-3f)

        vm.onMessageShown()

        assertNull(vm.state.value.message)
    }

    @Test
    fun `second press brings back the live camera with the zoom it had`() {
        val vm = readyViewModel()
        vm.zoomTo15()
        vm.onFreezeClick()
        val ratios = camera.zoomRatios.toList()

        vm.onFreezeClick()

        assertNull(vm.state.value.frozen)
        assertEquals(15f, vm.state.value.zoom, 1e-3f)
        assertEquals(1.5f, vm.state.value.softScale, 1e-3f)
        assertEquals(15f, vm.state.value.indicatorZoom, 1e-3f)
        assertEquals(ratios, camera.zoomRatios)
        assertEquals(1, camera.captureCount)
    }

    // --- на стоп-кадре ---

    /** Стоп-кадр при зуме по умолчанию ×2: камера ×2, масштаб снимка ×1. */
    private fun frozenAtDefaultZoom(): MagnifierViewModel = readyViewModel().also {
        it.onFreezeClick()
        assertViewport(FrozenViewport(scale = 1f), it.state.value.frozen?.viewport)
    }

    @Test
    fun `pinch and drag on a frozen frame move the picture and leave the camera alone`() {
        val vm = frozenAtDefaultZoom()
        val ratios = camera.zoomRatios.toList()

        // Щипок ×2 вокруг (250, 500): см. FrozenMathTest — точка под пальцами остаётся на месте.
        vm.onPinch(2f, 250f, 500f)
        assertViewport(FrozenViewport(2f, 250f, 500f), vm.state.value.frozen?.viewport)

        // Тянем вправо на 1000 px: при ×2 запас по краю только 500.
        vm.onPinch(1f, 250f, 500f, panX = 1000f, panY = 0f)
        assertViewport(FrozenViewport(2f, 500f, 500f), vm.state.value.frozen?.viewport)

        assertEquals(ZoomMath.DEFAULT_ZOOM, vm.state.value.zoom, 1e-3f)
        assertEquals(ratios, camera.zoomRatios)
    }

    @Test
    fun `frozen scale is held in 1 to 8 and the indicator is camera zoom times that scale`() {
        val vm = frozenAtDefaultZoom()

        vm.onPinch(100f, 500f, 1000f)
        assertEquals(8f, vm.state.value.frozen!!.viewport.scale, 1e-3f)
        assertEquals(16f, vm.state.value.indicatorZoom, 1e-3f)

        vm.onPinch(0.001f, 500f, 1000f)
        assertEquals(1f, vm.state.value.frozen!!.viewport.scale, 1e-3f)
        assertEquals(2f, vm.state.value.indicatorZoom, 1e-3f)
    }

    @Test
    fun `indicator at the moment of freezing shows the same zoom as the live picture`() {
        val vm = readyViewModel()
        vm.zoomTo15()
        vm.onFreezeClick()
        assertEquals(15f, vm.state.value.indicatorZoom, 1e-3f)

        vm.onSliderChange(1f)

        // Камера ×10 в момент снимка × масштаб стоп-кадра ×8.
        assertEquals(80f, vm.state.value.indicatorZoom, 1e-3f)
        assertEquals(15f, vm.state.value.zoom, 1e-3f)
    }

    @Test
    fun `slider on a frozen frame runs its scale from 1 to 8 on a log scale`() {
        val vm = frozenAtDefaultZoom()

        vm.onSliderChange(1f)
        assertEquals(8f, vm.state.value.frozen!!.viewport.scale, 1e-3f)
        vm.onSliderChange(0.5f)
        assertEquals(2.8284f, vm.state.value.frozen!!.viewport.scale, 1e-3f) // √8
        vm.onSliderChange(0f)
        assertEquals(1f, vm.state.value.frozen!!.viewport.scale, 1e-3f)

        assertEquals(ZoomMath.DEFAULT_ZOOM, vm.state.value.zoom, 1e-3f)
    }

    @Test
    fun `volume keys on a frozen frame step its scale along the zoom ladder`() {
        val vm = frozenAtDefaultZoom()

        assertEquals(true, vm.onVolumeKey(up = true))
        assertEquals(1.5f, vm.state.value.frozen!!.viewport.scale, 1e-3f)
        vm.onVolumeKey(up = true)
        vm.onVolumeKey(up = true)
        assertEquals(3f, vm.state.value.frozen!!.viewport.scale, 1e-3f)
        assertEquals(true, vm.onVolumeKey(up = false))
        assertEquals(2f, vm.state.value.frozen!!.viewport.scale, 1e-3f)

        assertEquals(ZoomMath.DEFAULT_ZOOM, vm.state.value.zoom, 1e-3f)
    }

    @Test
    fun `double tap on a frozen frame brings back the view it was frozen with`() {
        val vm = readyViewModel()
        vm.zoomTo15()
        vm.onFreezeClick()
        vm.onPinch(3f, 100f, 100f, panX = 40f, panY = -70f)

        vm.onDoubleTap()

        assertViewport(FrozenViewport(scale = 1.5f), vm.state.value.frozen?.viewport)
        assertEquals(15f, vm.state.value.zoom, 1e-3f)
    }

    @Test
    fun `tap on a frozen frame does not focus`() {
        val vm = frozenAtDefaultZoom()

        vm.onTap(300f, 700f)

        assertEquals(emptyList<Pair<Float, Float>>(), camera.focusCalls)
        assertNull(vm.state.value.focusPoint)
    }
}
