package io.github.guibecko.skydex.data.remote.dto

data class UserSummary(
    val id: String,
    val name: String,
    val email: String,
    val joinedAt: String
)

data class BadgeResponse(
    /** Enum name, e.g. "THREE_CAPTURES" — stable identifier for the achievement. */
    val achievement: String,
    val displayName: String,
    val description: String,
    val unlocked: Boolean,
    /** ISO-8601 instant, or null while the badge is still locked. */
    val unlockedAt: String?
)

data class ProfileResponse(
    val user: UserSummary,
    val level: Int,
    val totalXp: Int,
    val xpToNextLevel: Int,
    val confirmedCaptures: Int,
    val totalCaptures: Int,
    val capturedSpecies: Int,
    val totalSpecies: Int,
    val friends: Int,
    val unlockedBadges: Int,
    val totalBadges: Int,
    val badges: List<BadgeResponse>
)
