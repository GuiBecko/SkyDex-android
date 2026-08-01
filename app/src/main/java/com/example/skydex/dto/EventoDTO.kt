package com.example.skydex.dto

import java.time.LocalDateTime
import java.util.UUID
import kotlin.uuid.Uuid

// O QUE É ENVIADO
data class EventoRequest (
    var titulo: String,
    var descricao: String,
    var urlFoto: String,
    var userId: String
)

// RESPOSTA DA API
data class EventoResponse (
    var id: UUID,
    var titulo: String,
    var descricao: String,
    var urlFoto: String,
    var dataHoraRegistro: String,
    var user_id: UUID
)
