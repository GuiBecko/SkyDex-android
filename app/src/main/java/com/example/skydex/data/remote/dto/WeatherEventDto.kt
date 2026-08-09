package com.example.skydex.data.remote.dto

data class CreateWeatherEventRequest(
    val title: String,
    val description: String,
    val photoUrl: String,
    val latitude: Double,
    val longitude: Double,
    // No `capturedAt`. The server stamps the capture time; a client that could name the hour
    // could backdate to a past storm and farm the rare badges. `WeatherEventResponse` below
    // still reads it back as a String, since Gson has no Instant adapter.
    /** Species name from the backend catalog, e.g. "THUNDERSTORM". */
    val phenomenon: String,
    /**
     * Whether the platform reported this fix as coming from a mock provider. Client-asserted, so
     * it stops a casual mock-GPS app and not a modified client — see `CaptureValidationService`.
     *
     * No default here, deliberately. The server defaults it to `false`, which means a client that
     * omits it disables the check for every real user with nothing failing anywhere. Making it
     * required turns "someone forgot to pass it" into a compile error.
     */
    val locationIsMock: Boolean
)

data class WeatherEventResponse(
    val id: String,
    val title: String,
    val description: String,
    val photoUrl: String,
    val capturedAt: String,
    val latitude: Double,
    val longitude: Double,
    val userId: String,
    val authorName: String,
    val phenomenon: String,
    val phenomenonName: String,
    val rarity: String,
    val validationStatus: String,
    val xpAwarded: Int
)

data class NearbyPhenomenonResponse(
    /** Enum name, e.g. "THUNDERSTORM" — stable identifier for the species. */
    val phenomenon: String,
    /** Display copy, e.g. "Tempestade com Trovões". */
    val phenomenonName: String,
    val rarity: String,
    val time: String,
    val temperatureCelsius: Double?,
    val alertLevel: String
)
