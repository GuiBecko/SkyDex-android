package com.example.skydex.data.repository

import com.example.skydex.data.remote.SkyDexApi
import com.example.skydex.data.remote.dto.SkyDexResponse
import com.example.skydex.ui.skydex.SkyDexGateway

class SkyDexRepository(private val api: SkyDexApi) : SkyDexGateway {
    override suspend fun collection(): Result<SkyDexResponse> = resultOf { api.skyDex() }
}
