package com.example.skydex.data.remote

import com.example.skydex.data.remote.dto.CreateWeatherEventRequest
import com.example.skydex.data.remote.dto.FriendRequestBody
import com.example.skydex.data.remote.dto.FriendRequestResponse
import com.example.skydex.data.remote.dto.FriendResponse
import com.example.skydex.data.remote.dto.LoginRequest
import com.example.skydex.data.remote.dto.LoginResponse
import com.example.skydex.data.remote.dto.NearbyPhenomenonResponse
import com.example.skydex.data.remote.dto.PhotoUploadResponse
import com.example.skydex.data.remote.dto.RegisterRequest
import com.example.skydex.data.remote.dto.SkyDexResponse
import com.example.skydex.data.remote.dto.UserResponse
import com.example.skydex.data.remote.dto.WeatherEventResponse
import okhttp3.MultipartBody
import retrofit2.Response

/**
 * A hand-written stand-in for the Retrofit interface.
 *
 * Faking at the API boundary rather than at the repository keeps the *real*
 * [com.example.skydex.data.repository.CaptureRepository] inside the test, so its error mapping —
 * notably turning an unsuccessful `Response<Unit>` from `deleteCapture` into a failure — is
 * exercised for real instead of being stubbed away.
 *
 * Endpoints a test has not stubbed throw, so an unexpected call is loud rather than silent.
 */
class FakeSkyDexApi : SkyDexApi {

    var myCapturesResponse: () -> List<WeatherEventResponse> = { unsupported("myCaptures") }
    var nearbyResponse: () -> List<NearbyPhenomenonResponse> = { unsupported("nearbyPhenomena") }
    var createResponse: (CreateWeatherEventRequest) -> WeatherEventResponse = { unsupported("createCapture") }
    var deleteResponse: () -> Response<Unit> = { unsupported("deleteCapture") }
    var uploadPhotoResponse: () -> PhotoUploadResponse = { unsupported("uploadPhoto") }
    var skyDexResponse: () -> SkyDexResponse = { unsupported("skyDex") }
    var sendFriendRequestResponse: (FriendRequestBody) -> FriendRequestResponse = { unsupported("sendFriendRequest") }
    var incomingFriendRequestsResponse: () -> List<FriendRequestResponse> = { unsupported("incomingFriendRequests") }
    var acceptFriendRequestResponse: (String) -> FriendResponse = { unsupported("acceptFriendRequest") }
    var declineFriendRequestResponse: (String) -> Unit = { unsupported("declineFriendRequest") }
    var friendsResponse: () -> List<FriendResponse> = { unsupported("friends") }
    var feedResponse: (Int, Int) -> List<WeatherEventResponse> = { _, _ -> unsupported("feed") }

    /** Coordinates of every `nearbyPhenomena` call, in order. */
    val nearbyCalls = mutableListOf<Pair<Double, Double>>()

    /** Ids passed to `deleteCapture`, in order. */
    val deletedIds = mutableListOf<String>()

    /** Bodies passed to `createCapture`, in order. */
    val createdRequests = mutableListOf<CreateWeatherEventRequest>()

    /** Parts passed to `uploadPhoto`, in order. */
    val uploadedParts = mutableListOf<MultipartBody.Part>()

    override suspend fun login(request: LoginRequest): LoginResponse = unsupported("login")

    override suspend fun register(request: RegisterRequest): UserResponse = unsupported("register")

    override suspend fun me(): UserResponse = unsupported("me")

    override suspend fun myCaptures(): List<WeatherEventResponse> = myCapturesResponse()

    override suspend fun createCapture(request: CreateWeatherEventRequest): WeatherEventResponse {
        createdRequests += request
        return createResponse(request)
    }

    override suspend fun deleteCapture(id: String): Response<Unit> {
        deletedIds += id
        return deleteResponse()
    }

    override suspend fun uploadPhoto(file: MultipartBody.Part): PhotoUploadResponse {
        uploadedParts += file
        return uploadPhotoResponse()
    }

    override suspend fun nearbyPhenomena(
        latitude: Double,
        longitude: Double
    ): List<NearbyPhenomenonResponse> {
        nearbyCalls += latitude to longitude
        return nearbyResponse()
    }

    override suspend fun skyDex(): SkyDexResponse = skyDexResponse()

    override suspend fun sendFriendRequest(body: FriendRequestBody): FriendRequestResponse =
        sendFriendRequestResponse(body)

    override suspend fun incomingFriendRequests(): List<FriendRequestResponse> = incomingFriendRequestsResponse()

    override suspend fun acceptFriendRequest(id: String): FriendResponse = acceptFriendRequestResponse(id)

    override suspend fun declineFriendRequest(id: String) = declineFriendRequestResponse(id)

    override suspend fun friends(): List<FriendResponse> = friendsResponse()

    override suspend fun feed(page: Int, size: Int): List<WeatherEventResponse> = feedResponse(page, size)
}

private fun unsupported(endpoint: String): Nothing =
    throw UnsupportedOperationException("FakeSkyDexApi.$endpoint was called but never stubbed")
