package com.example.fuelly.repository.model

import kotlinx.serialization.Serializable

//classe Utente per memorizzare le informazioni degli utenti, come ID e email,
//utilizzata principalmente per l'autenticazione e la gestione degli account
@Serializable
data class Utente(
    val Id: String,
    val Email: String,
)
