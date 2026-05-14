package com.example.fuelly.classes

import org.json.JSONArray
import com.example.fuelly.R
import android.util.Log

data class ColonninaEV(
    val id: Int,
    val titolo: String,
    val comune: String,
    val provincia: String,
    val indirizzo: String,
    val lat: Double,
    val lon: Double,
    val potenzaKW: Double,
    val numPunti: Int,
    val operatore: String,
    val stato: String,
    val connettoriJson: String? // Memorizziamo il JSON dei connettori per usi futuri
) {
    companion object {
        var listaVicini: List<ColonninaEV> = emptyList()

        var listaSalvati: List<ColonninaEV> = emptyList()

        // Lista che unisce salvati e vicini senza duplicati
        val listaCompleta: List<ColonninaEV>
            get() = (listaSalvati + listaVicini).distinctBy { it.id }

        fun parseLista(jsonString: String): List<ColonninaEV> {
            val lista = mutableListOf<ColonninaEV>()
            try {
                val array = JSONArray(jsonString)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)

                    // Gestione della potenza: la leggiamo dal primo connettore nel JSON aggregato se disponibile
                    val connettoriArray = obj.optJSONArray("connettori")
                    val primaPotenza = connettoriArray?.optJSONObject(0)?.optDouble("potenza", 0.0) ?: 0.0

                    lista.add(ColonninaEV(
                        id = obj.getInt("ocm_id"), // Nome colonna DB
                        titolo = obj.optString("nome", "Colonnina"),
                        indirizzo = obj.optString("indirizzo", "Indirizzo N.D."),
                        comune = obj.optString("comune", "N.D."),
                        provincia = obj.optString("provincia", "N.D."),
                        lat = obj.getDouble("latitudine"),
                        lon = obj.getDouble("longitudine"),
                        potenzaKW = primaPotenza,
                        numPunti = obj.optInt("num_punti", 1),
                        operatore = obj.optString("operatore_nome", "Generico"),
                        stato = obj.optString("stato", "Sconosciuto"),
                        connettoriJson = obj.optString("connettori", null)
                    ))
                }
            } catch (e: Exception) {
                Log.e("Fuelly", "Errore parsing Colonnine dal DB: ${e.message}")
            }
            return lista
        }

        private fun parseTopSalvatiIds(data: Any?): List<Int> {
            val ids = mutableListOf<Int>()
            try {
                val jsonArray = org.json.JSONArray(data.toString())
                for (i in 0 until jsonArray.length()) {
                    ids.add(jsonArray.getJSONObject(i).getInt("idImpianto"))
                }
            } catch (e: Exception) { /* gestione errore */ }
            return ids
        }

        fun getIconaConnettore(typeName: String?): Int {
            if (typeName == null) return R.drawable.ic_ev_logo
            val name = typeName.lowercase()
            return when {
                name.contains("type 2") || name.contains("mennekes") -> R.drawable.ic_type2
                name.contains("ccs") || name.contains("combo") -> R.drawable.ic_ccs_type2
                name.contains("chademo") -> R.drawable.ic_chademo
                else -> R.drawable.ic_ev_logo
            }
        }
    }

    fun getLogoResource(): Int {
        val op = this.operatore.lowercase()
        return when {
            op.contains("enel") -> R.drawable.logo_enelx
            op.contains("tesla") -> R.drawable.logo_tesla
            op.contains("be charge") || op.contains("plenitude") || op.contains("eni") -> R.drawable.logo_plenitude
            else -> R.drawable.ic_ev_logo
        }
    }

    fun getShareText(): String {
        return """
        ⚡ *${titolo}*
        📍 ${indirizzo}, ${comune}
        
        🔋 Potenza: ${potenzaKW} kW
        
        🗺️ Apri su Maps: https://www.google.com/maps/search/?api=1&query=$lat,$lon
    """.trimIndent()
    }


}