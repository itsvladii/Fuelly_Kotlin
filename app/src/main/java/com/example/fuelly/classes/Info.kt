package com.example.fuelly.classes

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

@Serializable
data class Info(
    val id: String, // Assicurati che nel DB sia TEXT o UUID
    val idImpianto: Long,
    val idUtente:String,
    val orarioApertura: LocalTime,
    val orarioChiusura: LocalTime,
    val isBar: Boolean,
    val isBagno:Boolean,
    val descEstesa:String

)
