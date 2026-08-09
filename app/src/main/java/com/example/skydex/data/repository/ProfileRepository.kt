package com.example.skydex.data.repository

import com.example.skydex.data.remote.SkyDexApi
import com.example.skydex.data.remote.dto.ProfileResponse
import com.example.skydex.ui.profile.ProfileGateway

class ProfileRepository(private val api: SkyDexApi) : ProfileGateway {
    override suspend fun profile(): Result<ProfileResponse> = resultOf { api.profile() }
}
