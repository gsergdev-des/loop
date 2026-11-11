package dev.lupa.ui

import dev.lupa.camera.CameraCapabilities
import dev.lupa.camera.CameraStatus
import dev.lupa.camera.FakeCameraController
import dev.lupa.filters.Filter
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Кнопка «Фильтр» в [MagnifierViewModel]: порядок, сообщение, запоминание и восстановление. */
@OptIn(ExperimentalCoroutinesApi::class)
class MagnifierViewModelFilterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = UnconfinedTestDispatcher(scheduler)
    private val job = Job()
    private val camera = FakeCameraController()
    private val repository by lazy { testSettingsRepository(tmp.root, CoroutineScope(dispatcher + job)) }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        job.cancel()
        Dispatchers.resetMain()
    }

    private fun viewModel(): MagnifierViewModel =
        MagnifierViewModel(camera, repository, FakeSnapshotSaver()).also {
            camera.emit(CameraStatus.Ready(CameraCapabilities(1f, 10f, hasFlash = true)))
        }

    @Test
    fun `filter button goes around in the spec order and announces each filter`() {
        val vm = viewModel()
        assertEquals(Filter.NORMAL, vm.state.value.filter)

        val expected = listOf(
            Filter.HIGH_CONTRAST, Filter.GRAYSCALE, Filter.INVERT, Filter.YELLOW_ON_BLACK, Filter.NORMAL,
        )
        for (filter in expected) {
            vm.onFilterClick()
            assertEquals(filter, vm.state.value.filter)
            assertEquals(UiMessage.FilterName(filter), vm.state.value.message)
        }
    }

    @Test
    fun `filter name goes away when the screen has shown it`() {
        val vm = viewModel()
        vm.onFilterClick()

        vm.onMessageShown()

        assertNull(vm.state.value.message)
        assertEquals(Filter.HIGH_CONTRAST, vm.state.value.filter)
    }

    @Test
    fun `chosen filter is remembered after a short delay`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onFilterClick()
        vm.onFilterClick()

        assertEquals(Filter.NORMAL, repository.remembered.first().lastFilter)
        scheduler.advanceTimeBy(400)
        scheduler.runCurrent()

        assertEquals(Filter.GRAYSCALE, repository.remembered.first().lastFilter)
    }

    @Test
    fun `last filter comes back at launch`() = runTest(dispatcher) {
        repository.rememberFilter(Filter.INVERT)

        val vm = viewModel()

        assertEquals(Filter.INVERT, vm.state.value.filter)
        assertNull(vm.state.value.message)
        vm.onFilterClick()
        assertEquals(Filter.YELLOW_ON_BLACK, vm.state.value.filter)
    }
}
