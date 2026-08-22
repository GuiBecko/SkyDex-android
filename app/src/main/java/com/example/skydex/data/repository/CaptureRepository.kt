package com.example.skydex.data.repository

import com.example.skydex.data.remote.SkyDexApi
import com.example.skydex.data.remote.dto.CreateWeatherEventRequest
import com.example.skydex.data.remote.dto.NearbyPhenomenonResponse
import com.example.skydex.data.remote.dto.WeatherEventResponse
import com.example.skydex.ui.capture.CaptureGateway
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File

class CaptureRepository(private val api: SkyDexApi) : CaptureGateway {

    suspend fun myCaptures(): Result<List<WeatherEventResponse>> =
        resultOf { api.myCaptures() }

    suspend fun nearby(latitude: Double, longitude: Double): Result<List<NearbyPhenomenonResponse>> =
        resultOf { api.nearbyPhenomena(latitude, longitude) }

    override suspend fun create(request: CreateWeatherEventRequest): Result<WeatherEventResponse> =
        resultOf { api.createCapture(request) }

    /**
     * Uploads a local JPEG and returns the path the backend assigned to it.
     *
     * The returned value is **relative** (`/api/photos/<uuid>.jpg`) and is meant to be handed to
     * [create] unchanged — the backend persists it as given and composes the host on the way back
     * out, so a stored capture never carries an address that can go stale.
     */
    override suspend fun uploadPhoto(file: File): Result<String> = resultOf {
        val body = file.asRequestBody("image/jpeg".toMediaType())
        val part = MultipartBody.Part.createFormData("file", file.name, body)
        api.uploadPhoto(part).photoUrl
    }

    /**
     * `DELETE api/events/{id}`.
     *
     * Currently uncalled. It lost its only caller when the capture screen stopped destroying
     * unconfirmed captures behind the user's back — an unconfirmed capture is now kept and
     * explained rather than deleted. Kept because the endpoint exists and a user-driven delete on
     * Meus Registros is the obvious next use.
     *
     * `deleteCapture` returns the raw [retrofit2.Response], not `Unit`: Retrofit 2.9.0 throws
     * KotlinNullPointerException trying to map the backend's empty 204 body onto a non-null `Unit`
     * return type. That means an unsuccessful status arrives here as a perfectly normal value, so
     * [resultOf] alone would report a 403 or a 404 as a *successful* delete.
     */
    suspend fun delete(id: String): Result<Unit> = resultOf {
        val response = api.deleteCapture(id)
        if (!response.isSuccessful) throw HttpException(response)
    }
}
