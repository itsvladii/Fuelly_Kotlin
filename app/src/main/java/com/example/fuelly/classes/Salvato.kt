package com.example.fuelly.classes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Salvato(
    @SerialName("idUtente") val idUtente: String,
    @SerialName("idImpianto") val idBenzinaio: Long,
)