package com.example.skydex.ui.captures

import com.example.skydex.data.remote.FakeSkyDexApi
import com.example.skydex.data.remote.dto.WeatherEventResponse
import com.example.skydex.data.repository.CaptureRepository
import com.example.skydex.ui.common.RecordingLogWarning
import com.example.skydex.ui.common.UiState
import com.example.skydex.ui.common.noLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MyCapturesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val api = FakeSkyDexApi()
    private val repository = CaptureRepository(api)

    private val capture = WeatherEventResponse(
        id = "1",
        title = "Cumulonimbus",
        description = "Uma torre de nuvens",
        photoUrl = "https://example.test/cb.jpg",
        capturedAt = "2026-08-07T10:00:00Z",
        latitude = -23.55,
        longitude = -46.63,
        userId = "u1",
        authorName = "Pilot"
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the captures are loaded as soon as the view model exists`() = runTest(dispatcher) {
        api.myCapturesResponse = { listOf(capture) }

        val viewModel = MyCapturesViewModel(repository, noLogging)
        assertEquals(UiState.Loading, viewModel.state.value)

        advanceUntilIdle()
        assertEquals(UiState.Success(listOf(capture)), viewModel.state.value)
    }

    @Test
    fun `a failure becomes an error message and logs the cause`() = runTest(dispatcher) {
        val cause = IOException("offline")
        api.myCapturesResponse = { throw cause }
        val logWarning = RecordingLogWarning()

        val viewModel = MyCapturesViewModel(repository, logWarning)
        advanceUntilIdle()

        assertEquals(
            UiState.Error("Não foi possível carregar seus registros."),
            viewModel.state.value
        )
        assertEquals(cause, logWarning.warnings.single().cause)
    }

    @Test
    fun `refresh asks the api again`() = runTest(dispatcher) {
        api.myCapturesResponse = { throw IOException("offline") }
        val viewModel = MyCapturesViewModel(repository, noLogging)
        advanceUntilIdle()

        api.myCapturesResponse = { listOf(capture) }
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(UiState.Success(listOf(capture)), viewModel.state.value)
    }
}
