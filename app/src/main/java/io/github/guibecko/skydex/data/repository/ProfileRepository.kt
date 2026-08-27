package io.github.guibecko.skydex.data.repository

import io.github.guibecko.skydex.data.remote.SkyDexApi
import io.github.guibecko.skydex.data.remote.dto.ProfileResponse
import io.github.guibecko.skydex.ui.profile.ProfileGateway

class ProfileRepository(private val api: SkyDexApi) : ProfileGateway {
    override suspend fun profile(): Result<ProfileResponse> = resultOf { api.profile() }
}
