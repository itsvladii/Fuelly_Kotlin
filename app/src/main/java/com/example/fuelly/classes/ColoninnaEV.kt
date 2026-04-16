package com.example.fuelly.classes

import org.json.JSONArray
import com.example.fuelly.R

data class ColonninaEV(
    val id: Int,
    val titolo: String,
    val indirizzo: String,
    val lat: Double,
    val lon: Double,
    val potenzaKW: Double,
    val numPunti: Int,
    val operatore: String // Aggiungi questo campo
) {
    companion object {
        //contenitore di tutte le colonnine vicine
        var listaVicini: List<ColonninaEV> = emptyList()

        //funzione di parsing della risposta JSON
        fun parseLista(jsonString: String): List<ColonninaEV> {
            val lista = mutableListOf<ColonninaEV>()
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val poi = array.getJSONObject(i)
                val address = poi.getJSONObject("AddressInfo")

                //estrazione del numero di punti di ricarica
                val nPoints = poi.optInt("NumberOfPoints", 1)

                //estrazione della potenza in kw
                val connections = poi.optJSONArray("Connections")
                val kw = connections?.optJSONObject(0)?.optDouble("PowerKW", 0.0) ?: 0.0
                // 1. Estrai l'oggetto OperatorInfo (può essere null, quindi usa optJSONObject)
                val operatorInfo = poi.optJSONObject("OperatorInfo")

                val nomeOperatore = operatorInfo?.optString("Title", "Generico") ?: "Generico"

                lista.add(ColonninaEV(
                    id = poi.getInt("ID"),
                    titolo = address.optString("Title", "Colonnina"),
                    indirizzo = address.optString("AddressLine1", "Indirizzo N.D."),
                    lat = address.getDouble("Latitude"),
                    lon = address.getDouble("Longitude"),
                    potenzaKW = kw,
                    numPunti = nPoints,
                    operatore = nomeOperatore
                ))
            }
            return lista
        }
    }

    fun getLogoResource(): Int {
        val op = this.operatore.lowercase()

        return when {
            op.contains("enel") -> R.drawable.logo_enelx
            op.contains("tesla") -> R.drawable.logo_tesla
            op.contains("be charge") || op.contains("plenitude") -> R.drawable.logo_plenitude
            else -> R.drawable.ev_logo
        }
    }
}