package io.github.guibecko.skydex.data.remote.dto

data class CreateWeatherEventRequest(
    val title: String,
    val description: String,
    val photoUrl: String,
    val latitude: Double,
    val longitude: Double,
    // No `capturedAt`. The server stamps the capture time; a client that could name the hour
    // could backdate to a past storm and farm the rare badges.
    //
    // No `phenomenon` either, as of the AI validation work, and for a related reason: the server
    // reads the weather from Open-Meteo itself. A species the client names is a species a
    // modified client can name falsely. The backend still accepts the field from older installs
    // and ignores it; this build simply does not send it.
    /**
     * Whether the platform reported this fix as coming from a mock provider. Client-asserted, so
     * it stops a casual mock-GPS app and not a modified client.
     *
     * No default here, deliberately. The server defaults it to `false`, which means a client that
     * omits it disables the check for every real user with nothing failing anywhere.
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
    /**
     * Enum name — `PHOTO_CONTRADICTS_WEATHER`, `IMPLAUSIBLE_TRAVEL`, `MOCK_LOCATION` — or null.
     *
     * Nullable and absent from the body on a confirmed capture, so this must stay a `String?`.
     * The Portuguese sentence for each value lives in `CaptureRewardOverlay`, not here: this
     * carries the signal, the UI owns the words.
     */
    val unconfirmedReason: String? = null,
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
