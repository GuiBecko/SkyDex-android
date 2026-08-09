package com.example.skydex.ui.profile

import com.example.skydex.data.remote.dto.ProfileResponse

interface ProfileGateway {
    suspend fun profile(): Result<ProfileResponse>
}
