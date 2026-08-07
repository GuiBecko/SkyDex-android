package com.example.skydex.data.remote.dto

data class CreateWeatherEventRequest(
    val title: String,
    val description: String,
    val photoUrl: String
)

data class WeatherEventResponse(
    val id: String,
    val title: String,
    val description: String,
    val photoUrl: String,
    val capturedAt: String,
    val userId: String,
    val authorName: String
)

data class NearbyPhenomenonResponse(
    val phenomenon: String,
    val time: String,
    val temperatureCelsius: Double?,
    val alertLevel: String
)
