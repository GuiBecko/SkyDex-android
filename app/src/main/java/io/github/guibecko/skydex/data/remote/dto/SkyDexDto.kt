package io.github.guibecko.skydex.data.remote.dto

data class SkyDexEntryResponse(
    val phenomenon: String,
    val displayName: String,
    val rarity: String,
    val xpPerCapture: Int,
    val captured: Boolean,
    val captureCount: Int,
    val firstCapturedAt: String?
)

data class SkyDexResponse(
    val level: Int,
    val totalXp: Int,
    val xpToNextLevel: Int,
    val capturedSpecies: Int,
    val totalSpecies: Int,
    val entries: List<SkyDexEntryResponse>
)
