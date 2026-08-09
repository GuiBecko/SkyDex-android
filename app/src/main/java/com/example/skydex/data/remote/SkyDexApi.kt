package com.example.skydex.data.remote

import com.example.skydex.data.remote.dto.CreateWeatherEventRequest
import com.example.skydex.data.remote.dto.FriendRequestBody
import com.example.skydex.data.remote.dto.FriendRequestResponse
import com.example.skydex.data.remote.dto.FriendResponse
import com.example.skydex.data.remote.dto.LoginRequest
import com.example.skydex.data.remote.dto.LoginResponse
import com.example.skydex.data.remote.dto.NearbyPhenomenonResponse
import com.example.skydex.data.remote.dto.PhotoUploadResponse
import com.example.skydex.data.remote.dto.ProfileResponse
import com.example.skydex.data.remote.dto.RegisterRequest
import com.example.skydex.data.remote.dto.SkyDexResponse
import com.example.skydex.data.remote.dto.UserResponse
import com.example.skydex.data.remote.dto.WeatherEventResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface SkyDexApi {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): UserResponse

    @GET("api/users/me")
    suspend fun me(): UserResponse

    @GET("api/events/mine")
    suspend fun myCaptures(): List<WeatherEventResponse>

    @POST("api/events")
    suspend fun createCapture(@Body request: CreateWeatherEventRequest): WeatherEventResponse

    /**
     * The backend answers 204 No Content. Retrofit 2.9.0 cannot map an empty body onto a
     * `Unit` return type — it raises KotlinNullPointerException — so the raw [Response] is
     * returned and callers check [Response.isSuccessful].
     */
    @DELETE("api/events/{id}")
    suspend fun deleteCapture(@Path("id") id: String): Response<Unit>

    /** The backend expects the part to be named `file` and answers 201 with the public URL. */
    @Multipart
    @POST("api/photos")
    suspend fun uploadPhoto(@Part file: MultipartBody.Part): PhotoUploadResponse

    @GET("api/weather/nearby")
    suspend fun nearbyPhenomena(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double
    ): List<NearbyPhenomenonResponse>

    @GET("api/skydex")
    suspend fun skyDex(): SkyDexResponse

    @POST("api/friends/requests")
    suspend fun sendFriendRequest(@Body body: FriendRequestBody): FriendRequestResponse

    @GET("api/friends/requests")
    suspend fun incomingFriendRequests(): List<FriendRequestResponse>

    @POST("api/friends/requests/{id}/accept")
    suspend fun acceptFriendRequest(@Path("id") id: String): FriendResponse

    @DELETE("api/friends/requests/{id}")
    suspend fun declineFriendRequest(@Path("id") id: String)

    @GET("api/friends")
    suspend fun friends(): List<FriendResponse>

    @GET("api/feed")
    suspend fun feed(@Query("page") page: Int, @Query("size") size: Int): List<WeatherEventResponse>

    @GET("api/users/me/profile")
    suspend fun profile(): ProfileResponse
}
