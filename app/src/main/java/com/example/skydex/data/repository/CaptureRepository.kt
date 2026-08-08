package com.example.skydex.data.repository

import com.example.skydex.data.remote.SkyDexApi
import com.example.skydex.data.remote.dto.CreateWeatherEventRequest
import com.example.skydex.data.remote.dto.NearbyPhenomenonResponse
import com.example.skydex.data.remote.dto.WeatherEventResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File

class CaptureRepository(private val api: SkyDexApi) {

    suspend fun myCaptures(): Result<List<WeatherEventResponse>> =
        resultOf { api.myCaptures() }

    suspend fun nearby(latitude: Double, longitude: Double): Result<List<NearbyPhenomenonResponse>> =
        resultOf { api.nearbyPhenomena(latitude, longitude) }

    suspend fun create(request: CreateWeatherEventRequest): Result<WeatherEventResponse> =
        resultOf { api.createCapture(request) }

    /** Uploads a local JPEG and returns the public URL the backend assigned to it. */
    suspend fun uploadPhoto(file: File): Result<String> = resultOf {
        val body = file.asRequestBody("image/jpeg".toMediaType())
        val part = MultipartBody.Part.createFormData("file", file.name, body)
        api.uploadPhoto(part).photoUrl
    }

    /**
     * `deleteCapture` returns the raw [retrofit2.Response], not `Unit`: Retrofit 2.9.0 throws
     * KotlinNullPointerException trying to map the backend's empty 204 body onto a non-null `Unit`
     * return type. That means an unsuccessful status arrives here as a perfectly normal value, so
     * [resultOf] alone would report a 403 or a 404 as a *successful* delete. Convert it to a
     * failure explicitly.
     */
    suspend fun delete(id: String): Result<Unit> = resultOf {
        val response = api.deleteCapture(id)
        if (!response.isSuccessful) throw HttpException(response)
    }
}
