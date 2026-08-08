package com.example.skydex.data.remote.dto

data class CreateWeatherEventRequest(
    val title: String,
    val description: String,
    val photoUrl: String,
    val latitude: Double,
    val longitude: Double
    // No `capturedAt`. The server stamps the capture time; a client that could name the hour
    // could backdate to a past storm and farm the rare badges. `WeatherEventResponse` below
    // still reads it back as a String, since Gson has no Instant adapter.
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
    val authorName: String
)

data class NearbyPhenomenonResponse(
    val phenomenon: String,
    val time: String,
    val temperatureCelsius: Double?,
    val alertLevel: String
)
