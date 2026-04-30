package com.example.fuelly.classes

import kotlinx.serialization.Serializable

@Serializable
data class Utente(
    val id: String, // Assicurati che nel DB sia TEXT o UUID
    val email: String,
    val password: String,
    val nome: String,
    val cognome: String
)
