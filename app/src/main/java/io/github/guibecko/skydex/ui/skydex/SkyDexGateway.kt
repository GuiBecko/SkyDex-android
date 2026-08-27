package io.github.guibecko.skydex.ui.skydex

import io.github.guibecko.skydex.data.remote.dto.SkyDexResponse

interface SkyDexGateway {
    suspend fun collection(): Result<SkyDexResponse>
}
