package com.example.fuelly.classes

import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable


//classe Info per memorizzare le informazioni aggiuntive sui benzinai,
//come orari di apertura/chiusura, presenza di bar o bagno, e una descrizione estesa.
@Serializable
data class Info(
    val id: String? = null,
    val idImpianto: Long? = null,
    val idUtente: String? = null,
    val orarioApertura: String? = null,
    val orarioChiusura: String? = null,
    val isBar: Boolean? = null,
    val isBagno: Boolean? = null,
    val descEstesa: String? = null
)
