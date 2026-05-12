package com.example.fuelly.classes

import android.util.Log
import com.example.fuelly.R
import org.json.JSONArray


data class Benzinaio(
    val id: Int,
    val gestore: String,
    val bandiera: String,
    val tipoImpianto: String,
    val nomeImpianto: String,
    val indirizzo: String,
    val comune: String,
    val provincia: String,
    val lat: Double,
    val lon: Double,
    val prezzoBenzina: Double,
    val prezzoDiesel: Double
){
    //contenitore di benzinai vicini
    companion object {
        var listaVicini: List<Benzinaio> = emptyList()

        var listaSalvati: List<Benzinaio> = emptyList()

        // Lista che unisce salvati e vicini senza duplicati
        val listaCompleta: List<Benzinaio>
            get() = (listaSalvati + listaVicini).distinctBy { it.id }

        fun parseLista(data: Any?): List<Benzinaio> {
            val lista = mutableListOf<Benzinaio>()

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
                        Benzinaio(
                            id = obj.optInt("idImpianto", 0),
                            gestore = obj.optString("Gestore", "N/A"),
                            bandiera = obj.optString("Bandiera", "-"),
                            tipoImpianto = obj.optString("Tipo Impianto", "-"),
                            nomeImpianto = obj.optString("Nome Impianto", "-"),
                            indirizzo = obj.optString("Indirizzo", "N/A"),
                            comune = obj.optString("Comune", "-"),
                            provincia = obj.optString("Provincia", "-"),
                            lat = obj.optDouble("Latitudine", 0.0),
                            lon = obj.optDouble("Longitudine", 0.0),
                            prezzoBenzina = obj.optDouble("prezzo_benzina", 0.0),
                            prezzoDiesel = obj.optDouble("prezzo_diesel", 0.0)
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("Fuelly", "Errore durante il parsing del JSON: ${e.message}")
            }

            return lista
        }
    }

    fun getLogoResource(): Int {
        // Puliamo la stringa come prima
        val brand = this.bandiera.lowercase()

        return when {
            brand.contains("eni") || brand.contains("agip") -> R.drawable.logo_agipeni
            brand.contains("ip") -> R.drawable.logo_ip
            brand.contains("q8") -> R.drawable.logo_q8
            brand.contains("esso") -> R.drawable.logo_esso
            brand.contains("tamoil") -> R.drawable.logo_tamoil
            brand.contains("shell") -> R.drawable.logo_shell
            brand.contains("beyfin") -> R.drawable.logo_beyfin
            brand.contains("lukoil") -> R.drawable.logo_lukoil
            brand.contains("retitalia")-> R.drawable.logo_retitalia
            //se non ho il logo: usa l'SVG generico della pompa
            else -> R.drawable.fuelly_logo_foreground
        }
    }

    fun getShareText(): String {
        val sb = StringBuilder()
        sb.append("⛽ *${bandiera}*\n")
        sb.append("📍 ${indirizzo}, ${comune}\n\n")

        if (prezzoBenzina > 0) sb.append("🟢 Benzina: ${String.format("%.3f", prezzoBenzina)}€\n")
        if (prezzoDiesel > 0) sb.append("🟡 Diesel: ${String.format("%.3f", prezzoDiesel)}€\n")

        sb.append("\nGuarda su Google Maps:\n")
        sb.append("https://www.google.com/maps/search/?api=1&query=${lat},${lon}")

        return sb.toString()
    }
}
