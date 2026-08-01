package com.example.skydex

import com.example.skydex.dto.EventoRequest
import com.example.skydex.dto.EventoResponse
import com.example.skydex.ui.theme.pages.EventoProximoDTO
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SkyDexApi {
    @GET("/api/users/{id}/eventos")
    suspend fun listarUserEvents(
        @Path("id") userId: String,
        @Header("Authorization") token: String
    ): List<EventoResponse>

    @POST("/api/eventos")
    suspend fun criarRegistro(
        @Body request: EventoRequest,
        @Header("Authorization") token: String
    ): EventoResponse

    @DELETE("api/eventos/{id}")
    suspend fun deletarEvento(
        @Path("id") id: String,
        @Header("Authorization") token: String
    )

    @GET("api/users/{id}/eventosProximos")
    suspend fun listarEventosProximos(
        @Path("id") userId: String,
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Header("Authorization") token: String
    ) : List<EventoProximoDTO>
}