package com.example.fuelly.repository.model

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
    val connettoriJson: String?
) {
    companion object {
        //lista delle colonnine vicine alla posizione dell'utente
        var listaVicini: List<ColonninaEV> = emptyList()

        //lista delle colonnine salvate dall'utente
        var listaSalvati: List<ColonninaEV> = emptyList()

        //lista che unisce salvati e vicini senza duplicati
        val listaCompleta: List<ColonninaEV>
            get() = (listaSalvati + listaVicini).distinctBy { it.id }

        //funzione di parsing che prende in input l'array di colonnine ottenuti da Supabase e
        // lo trasforma in una lista di oggetti ColonninaEV
        fun parseLista(jsonString: String): List<ColonninaEV> {

            val lista = mutableListOf<ColonninaEV>()
            try {
                //converto la stringa JSON in un array di oggetti JSON
                val array = JSONArray(jsonString)
                //ciclo su ogni oggetto JSON nell'array
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)

                    //per i connettori, prendo la stringa JSON,
                    // la converto in un array di oggetti JSON e prendo la potenza del primo connettore (se presente)
                    val connettoriArray = obj.optJSONArray("connettori")
                    val primaPotenza = connettoriArray?.optJSONObject(0)?.optDouble("potenza", 0.0) ?: 0.0

                    //alla lista, aggiungo una nuova colonnina creata con i campi estratti dall'oggetto JSON
                    // (se un campo è mancante, uso un valore di default)
                    lista.add(
                        ColonninaEV(
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
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("Fuelly", "Errore parsing Colonnine dal DB: ${e.message}")
            }
            return lista
        }

        //funzione di parsing che prende in input l'array di colonnine più salvate ottenuti da Supabase e
        // lo trasforma in una lista di ID di colonnine
        private fun parseTopSalvatiIds(data: Any?): List<Int> {
            val ids = mutableListOf<Int>()
            try {
                val jsonArray = JSONArray(data.toString())
                for (i in 0 until jsonArray.length()) {
                    ids.add(jsonArray.getJSONObject(i).getInt("idImpianto"))
                }
            } catch (e: Exception) {
                Log.e("Fuelly", "Errore parsing ID colonnine salvate: ${e.message}")
            }
            return ids
        }

        //funzione che, dato il nome del tipo di connettore, ritorna l'SVG corrispondente da mostrare nell'UI.
        //se il nome è sconosciuto, ritorna un'icona generica.
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

    //funzione che, dato il nome dell'operatore, ritorna il logo corrispondente da mostrare nell'UI.
    // se il nome è sconosciuto, ritorna un'icona generica.
    fun getLogoResource(): Int {
        val op = this.operatore.lowercase()
        return when {
            op.contains("enel") -> R.drawable.logo_enelx
            op.contains("tesla") -> R.drawable.logo_tesla
            op.contains("be charge") || op.contains("plenitude") || op.contains("eni") -> R.drawable.logo_plenitude
            else -> R.drawable.ic_ev_logo
        }
    }

    //funzione che genera il testo da condividere quando l'utente vuole condividere la colonnina,
    // con le informazioni principali e un link a Google Maps
    fun getShareText(): String {
        return """
        ⚡ *${titolo}*
        📍 ${indirizzo}, ${comune}

        🔋 Potenza: ${potenzaKW} kW

        🗺️ Apri su Maps: https://www.google.com/maps/search/?api=1&query=$lat,$lon
    """.trimIndent()
    }


}
