package com.example.skydex.ui.capture

import com.example.skydex.data.remote.dto.CreateWeatherEventRequest
import com.example.skydex.data.remote.dto.WeatherEventResponse
import java.io.File

/**
 * The slice of [com.example.skydex.data.repository.CaptureRepository] the capture screen needs.
 * Narrowing the dependency down to an interface keeps [CaptureViewModel] testable against a fake
 * that never touches Retrofit or a real file system.
 */
interface CaptureGateway {
    suspend fun uploadPhoto(file: File): Result<String>
    suspend fun create(request: CreateWeatherEventRequest): Result<WeatherEventResponse>

    /**
     * `DELETE api/events/{id}`.
     *
     * The capture screen is not a management surface and offers no delete button — this exists
     * for exactly one caller, [CaptureViewModel.discardUnconfirmed], which takes back a capture the
     * backend declined to confirm. See that function for the whole contract, including why its
     * failure is never shown to anyone.
     */
    suspend fun delete(id: String): Result<Unit>
}
