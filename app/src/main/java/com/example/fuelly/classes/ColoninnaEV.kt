package com.example.fuelly.classes

import org.json.JSONArray

data class ColonninaEV(
    val id: Int,
    val titolo: String,
    val indirizzo: String,
    val lat: Double,
    val lon: Double,
    val potenzaKW: Double,
    val numPunti: Int
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

                lista.add(ColonninaEV(
                    id = poi.getInt("ID"),
                    titolo = address.optString("Title", "Colonnina"),
                    indirizzo = address.optString("AddressLine1", "Indirizzo N.D."),
                    lat = address.getDouble("Latitude"),
                    lon = address.getDouble("Longitude"),
                    potenzaKW = kw,
                    numPunti = nPoints
                ))
            }
            return lista
        }
    }
}