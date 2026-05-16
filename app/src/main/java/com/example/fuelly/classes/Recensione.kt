package com.example.fuelly.classes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class Recensione(
    @SerialName("idUtente") val idUtente: String,
    @SerialName("idImpianto") val idBenzinaio: Long,
    @SerialName("idRecensione")val idRecensione: String= java.util.UUID.randomUUID().toString(),
    @SerialName("rating") val rating: Int,
    @SerialName("descRecensione") val descRecensione: String,
    @SerialName("created_at") val data_inserimento: String?=null,
    @SerialName("nome_display") val nome: String?= "Utente Anonimo",
    @SerialName("img_profilo") val avatar_url: String? = null,
    @SerialName("tipologia_elemento") var tipo: String? = null

){}