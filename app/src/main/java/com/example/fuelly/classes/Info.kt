package com.example.fuelly.classes

import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable


@Serializable
data class Info(
    val id: String? = null,           // Deve essere String? per gestire l'UUID
    val idImpianto: Long? = null,     // Verifica che il nome sia identico al DB
    val idUtente: String? = null,     // Deve essere String? per l'UUID
    val orarioApertura: String? = null,
    val orarioChiusura: String? = null,
    val isBar: Boolean? = null,
    val isBagno: Boolean? = null,
    val descEstesa: String? = null
)
