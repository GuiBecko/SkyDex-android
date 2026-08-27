package io.github.guibecko.skydex.data.repository

import io.github.guibecko.skydex.data.remote.SkyDexApi
import io.github.guibecko.skydex.data.remote.dto.SkyDexResponse
import io.github.guibecko.skydex.ui.skydex.SkyDexGateway

class SkyDexRepository(private val api: SkyDexApi) : SkyDexGateway {
    override suspend fun collection(): Result<SkyDexResponse> = resultOf { api.skyDex() }
}
