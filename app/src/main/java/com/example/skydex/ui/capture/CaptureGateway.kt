package com.example.skydex.ui.capture

import com.example.skydex.data.remote.dto.CreateWeatherEventRequest
import com.example.skydex.data.remote.dto.WeatherEventResponse
import java.io.File

/**
 * The two-method slice of [com.example.skydex.data.repository.CaptureRepository] the capture
 * screen needs. Narrowing the dependency down to an interface keeps [CaptureViewModel] testable
 * against a fake that never touches Retrofit or a real file system.
 */
interface CaptureGateway {
    suspend fun uploadPhoto(file: File): Result<String>
    suspend fun create(request: CreateWeatherEventRequest): Result<WeatherEventResponse>
}
