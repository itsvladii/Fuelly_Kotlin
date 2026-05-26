package com.example.fuelly.repository.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

//classe Salvato per memorizzare i benzinai salvati dagli utenti, con riferimento all'ID dell'utente e all'ID del benzinaio
@Serializable
data class Salvato(
    @SerialName("idUtente") val idUtente: String,
    @SerialName("idImpianto") val idBenzinaio: Long,
)
