package com.example.fuelly.classes

import kotlinx.serialization.Serializable

@Serializable
data class Salvato(
    val idUtente: String,
    val idImpianto: Long
)