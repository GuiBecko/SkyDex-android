package io.github.guibecko.skydex.ui.profile

import io.github.guibecko.skydex.data.remote.dto.ProfileResponse

interface ProfileGateway {
    suspend fun profile(): Result<ProfileResponse>
}
