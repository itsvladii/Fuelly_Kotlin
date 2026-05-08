package com.example.fuelly.classes

import kotlinx.serialization.Serializable

@Serializable
data class Utente(
    val Id: String, // Assicurati che nel DB sia TEXT o UUID
    val Email: String,
)
