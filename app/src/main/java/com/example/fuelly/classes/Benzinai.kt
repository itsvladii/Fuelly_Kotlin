package com.example.fuelly.classes

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import org.json.JSONArray


data class Benzinai(
    val id: Int,
    val gestore: String,
    val bandiera: String,
    val tipoImpianto: String,
    val nomeImpianto: String,
    val indirizzo: String,
    val comune: String,
    val provincia: String,
    val lat: Double,
    val lon: Double
){
    //contenitore di benzinai vicini
    companion object {
        var listaVicini: List<Benzinai> = emptyList()



        fun parseLista(data: Any?): List<Benzinai> {
            val lista = mutableListOf<Benzinai>()

            // 1. Prendiamo la stringa che abbiamo visto nel log
            val jsonString = data?.toString() ?: return emptyList()

            try {
                // 2. Trasformiamo la stringa in un vero Array JSON
                val jsonArray = JSONArray(jsonString)

                Log.d("Fuelly", "Inizio parsing di ${jsonArray.length()} elementi dalla stringa...")

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)

                    // 3. Estraiamo i dati usando i nomi esatti (Case Sensitive!)
                    // Nota: Con JSONObject usiamo getString, getDouble, getInt
                    lista.add(
                        Benzinai(
                            id = obj.optInt("idImpianto", 0),
                            gestore = obj.optString("Gestore", "N/A"),
                            bandiera = obj.optString("Bandiera", "-"),
                            tipoImpianto = obj.optString("Tipo Impianto", "-"),
                            nomeImpianto = obj.optString("Nome Impianto", "-"),
                            indirizzo = obj.optString("Indirizzo", "N/A"),
                            comune = obj.optString("Comune", "-"),
                            provincia = obj.optString("Provincia", "-"),
                            lat = obj.optDouble("Latitudine", 0.0),
                            lon = obj.optDouble("Longitudine", 0.0)
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("Fuelly", "Errore durante il parsing del JSON: ${e.message}")
            }

            return lista
        }
    }
}