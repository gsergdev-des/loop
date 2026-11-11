package dev.lupa.ui

import android.graphics.Bitmap
import android.graphics.Color
import dev.lupa.camera.CameraCapabilities
import dev.lupa.camera.CameraStatus
import dev.lupa.camera.CaptureException
import dev.lupa.camera.FakeCameraController
import dev.lupa.filters.Filter
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Кнопка «Снимок» в [MagnifierViewModel]. Камера ×1…×10 (итоговый зум до ×20), экран 1000×2000 px,
 * снимок камеры 40×80. Robolectric NATIVE — ради настоящих пикселей.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MagnifierViewModelSnapshotTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = UnconfinedTestDispatcher(scheduler)
    private val job = Job()
    private val camera = FakeCameraController()
    private val saver = FakeSnapshotSaver()

    /** Края (по 10 px слева и справа) красные, середина белая. */
    private val shot: Bitmap = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until width) for (y in 0 until height) {
            setPixel(x, y, if (x < 10 || x >= 30) Color.RED else Color.WHITE)
        }
    }

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

    private fun viewModel(): MagnifierViewModel =
        MagnifierViewModel(
            camera,
            testSettingsRepository(tmp.root, CoroutineScope(dispatcher + job)),
            saver,
            workDispatcher = dispatcher,
        ).also {
            camera.emit(CameraStatus.Ready(CameraCapabilities(1f, 10f, hasFlash = true)))
            it.onViewSize(1000f, 2000f)
        }

    private fun MagnifierViewModel.zoomTo(value: Float) {
        onSliderChange(ZoomMath.toSlider(value, 1f, 20f))
    }

    private fun Bitmap.allPixels(color: Int) =
        (0 until width).all { x -> (0 until height).all { y -> getPixel(x, y) == color } }

    @Test
    fun `live snapshot is cut to the soft zoom and saved with a thanks message`() {
        val vm = viewModel()
        vm.zoomTo(20f) // камера ×10, программный ×2
        assertEquals(2f, vm.state.value.softScale, 1e-3f)

        vm.onSnapshotClick()

        assertEquals(1, camera.captureCount)
        val saved = saver.saved.single()
        assertEquals(20, saved.width)
        assertEquals(40, saved.height)
        assertEquals("в кадре осталась только белая середина", true, saved.allPixels(Color.WHITE))
        assertEquals(UiMessage.Saved, vm.state.value.message)
        assertEquals(Busy.NONE, vm.state.value.busy)
    }

    @Test
    fun `live snapshot without soft zoom keeps the whole shot`() {
        val vm = viewModel()
        vm.zoomTo(10f)

        vm.onSnapshotClick()

        val saved = saver.saved.single()
        assertEquals(40, saved.width)
        assertEquals(80, saved.height)
    }

    @Test
    fun `current filter is burned into the saved picture`() {
        val vm = viewModel()
        vm.zoomTo(20f)
        vm.onFilterClick() // высокий контраст
        vm.onFilterClick() // Ч/Б
        vm.onFilterClick() // инверсия
        assertEquals(Filter.INVERT, vm.state.value.filter)

        vm.onSnapshotClick()

        // Белая середина после инверсии — чёрная.
        assertEquals(true, saver.saved.single().allPixels(Color.BLACK))
    }

    @Test
    fun `frozen snapshot saves the visible part of the frozen picture`() {
        val vm = viewModel()
        vm.onFreezeClick()
        vm.onSliderChange(ZoomMath.toSlider(2f, 1f, ZoomMath.FROZEN_MAX))
        assertEquals(2f, vm.state.value.frozen!!.viewport.scale, 1e-3f)

        vm.onSnapshotClick()

        assertEquals(1, camera.captureCount) // новый снимок не делается
        val saved = saver.saved.single()
        assertEquals(20, saved.width)
        assertEquals(40, saved.height)
        assertEquals(true, saved.allPixels(Color.WHITE))
        assertEquals(UiMessage.Saved, vm.state.value.message)
    }

    @Test
    fun `while saving the screen is busy and more presses make no copies`() {
        val gate = CompletableDeferred<Unit>()
        saver.gate = gate
        val vm = viewModel()

        vm.onSnapshotClick()
        vm.onSnapshotClick()
        vm.onSnapshotClick()

        assertEquals(Busy.SAVING, vm.state.value.busy)
        assertEquals(1, saver.saved.size)
        assertEquals(1, camera.captureCount)

        gate.complete(Unit)

        assertEquals(Busy.NONE, vm.state.value.busy)
        assertEquals(UiMessage.Saved, vm.state.value.message)
        assertEquals(1, saver.saved.size)
    }

    @Test
    fun `write failure says it could not save`() {
        saver.failure = java.io.IOException("диск полон")
        val vm = viewModel()

        vm.onSnapshotClick()

        assertEquals(UiMessage.SaveFailed, vm.state.value.message)
        assertEquals(Busy.NONE, vm.state.value.busy)
    }

    @Test
    fun `capture failure says it could not save and writes nothing`() {
        camera.captureError = CaptureException("нет снимка")
        val vm = viewModel()

        vm.onSnapshotClick()

        assertEquals(UiMessage.SaveFailed, vm.state.value.message)
        assertEquals(Busy.NONE, vm.state.value.busy)
        assertEquals(0, saver.saved.size)
    }

    @Test
    fun `live zoom does not move while saving`() {
        saver.gate = CompletableDeferred()
        val vm = viewModel()
        vm.onSnapshotClick()

        vm.onSliderChange(1f)

        assertEquals(ZoomMath.DEFAULT_ZOOM, vm.state.value.zoom, 1e-3f)
    }

    @Test
    fun `snapshot does nothing before the camera is ready`() {
        val vm = MagnifierViewModel(
            camera,
            testSettingsRepository(tmp.root, CoroutineScope(dispatcher + job)),
            saver,
            workDispatcher = dispatcher,
        )

        vm.onSnapshotClick()

        assertNull(vm.state.value.message)
        assertEquals(0, camera.captureCount)
    }
}
