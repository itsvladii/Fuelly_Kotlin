package com.example.fuelly.classes

import org.json.JSONArray

data class ColonninaEV(
    val id: Int,
    val titolo: String,
    val indirizzo: String,
    val lat: Double,
    val lon: Double,
    val potenzaKW: Double
) {
    companion object {
        //contenitore di tutte le colonnine vicine
        var listaVicini: List<ColonninaEV> = emptyList()

        //funzione di parsing della risposta JSON
        fun parseLista(jsonString: String): List<ColonninaEV> {
            //lista vuota di colonnine
            val lista = mutableListOf<ColonninaEV>()
            //array degli elementi JSON dal output della chiamata
            val array = JSONArray(jsonString)
            //ciclo tutti gli elementi dell'array
            for (i in 0 until array.length()) {
                //prendo l'elemento JSON in posizione i
                val poi = array.getJSONObject(i)
                val address = poi.getJSONObject("AddressInfo")
                // Estraiamo la potenza dal primo connettore disponibile
                val connections = poi.optJSONArray("Connections")
                val kw = connections?.optJSONObject(0)?.optDouble("PowerKW", 0.0) ?: 0.0

                //aggiungo la colonnina alla lista
                lista.add(ColonninaEV(
                    id = poi.getInt("ID"),
                    titolo = address.optString("Title", "Colonnina"),
                    indirizzo = address.optString("AddressLine1", "Indirizzo N.D."),
                    lat = address.getDouble("Latitude"),
                    lon = address.getDouble("Longitude"),
                    potenzaKW = kw
                ))
            }
            return lista
        }
    }
}