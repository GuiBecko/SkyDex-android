package com.example.skydex.ui.skydex

import com.example.skydex.data.remote.dto.SkyDexResponse

interface SkyDexGateway {
    suspend fun collection(): Result<SkyDexResponse>
}
