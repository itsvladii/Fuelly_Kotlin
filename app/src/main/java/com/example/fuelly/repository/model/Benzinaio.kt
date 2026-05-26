package com.example.fuelly.repository.model

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
    val prezzoDiesel: Double,
    val prezzoGPL: Double,
    val prezzoMetano: Double,
) {

    companion object {
        //lista dei benzinai vicini alla poszione dell'utente, da aggiornare ogni volta che si aggiorna la posizione
        var listaVicini: List<Benzinaio> = emptyList()

        //lista dei benzinai salvati dall'utente
        var listaSalvati: List<Benzinaio> = emptyList()

        //lista dei benzinai piu salvati dagli utenti
        var listaTopSalvatiIds: List<Int> = emptyList()

        //lista completa che unisce i benzinai vicini e quelli salvati (evitando duplicati)
        val listaCompleta: List<Benzinaio>
            get() = (listaSalvati + listaVicini).distinctBy { it.id }

        //funzione di parsing che prende in input l'array di benzinai ottenuti da Supabase e
        // lo trasforma in una lista di oggetti Benzinaio
        fun parseLista(data: Any?): List<Benzinaio> {
            val lista = mutableListOf<Benzinaio>()

            //converto l'oggetto in una stringa JSON (se non è null, altrimenti ritorno una lista vuota)
            val jsonString = data?.toString() ?: return emptyList()

            try {
                //tramite JSONArray posso "scorporare" la stringa in singoli oggetti JSON,
                //  poi inseriti in un array di oggetti JSON che posso iterare
                val jsonArray = JSONArray(jsonString)

                //Log.d("Fuelly", "Inizio parsing di ${jsonArray.length()} elementi dalla stringa...")

                //ciclo per ogni oggetto JSON nell'array
                for (i in 0 until jsonArray.length()) {
                    //oggetto corrente
                    val obj = jsonArray.getJSONObject(i)

                    //estraggo i campi necessari per creare un oggetto Benzinaio,
                    //e li aggiungo alla lista finale. (se un campo è mancante, uso un valore di default)
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
                            prezzoDiesel = obj.optDouble("prezzo_diesel", 0.0),
                            prezzoGPL = obj.optDouble("prezzo_gpl", 0.0),
                            prezzoMetano = obj.optDouble("prezzo_metano", 0.0)
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("Fuelly", "Errore durante il parsing del JSON: ${e.message}")
            }

            return lista
        }

        //funzione per il parsing degli ID dei benzinai più salvati,
        // che prende in input l'array di oggetti JSON ottenuti da Supabase e ritorna una lista di interi con gli
        // ID dei benzinai più salvati
        fun parseTopSalvatiIds(data: Any?): List<Int> {
            //lista di interi che conterrà gli ID dei benzinai più salvati
            val ids = mutableListOf<Int>()
            try {
                //converto la stringa JSON in un array di oggetti JSON
                val jsonArray = JSONArray(data.toString())
                //ciclo per ogni oggetto JSON nell'array e estraggo l'ID del benzinaio, che aggiungo alla lista finale
                for (i in 0 until jsonArray.length()) {
                    ids.add(jsonArray.getJSONObject(i).getInt("idImpianto"))
                }
            } catch (e: Exception) {
                Log.e("Fuelly", "Errore durante il parsing del JSON per i salvati: ${e.message}")
            }
            return ids
        }
    }

    //funzione che associa ad ogni benzinaio un logo in base alla bandiera,
    // per poi mostrarlo nella UI.
    fun getLogoResource(): Int {
        //ottengo la bandiera in minuscolo
        val brand = this.bandiera.lowercase()

        //associo ad ogni brand un logo specifico,
        // se non ho il logo per quel brand uso un logo generico (la pompa di Fuelly)
        return when {
            brand.contains("eni") || brand.contains("agip") -> R.drawable.logo_agipeni
            brand.contains("ip") -> R.drawable.logo_ip
            brand.contains("q8") -> R.drawable.logo_q8
            brand.contains("esso") -> R.drawable.logo_esso
            brand.contains("tamoil") -> R.drawable.logo_tamoil
            brand.contains("shell") -> R.drawable.logo_shell
            brand.contains("beyfin") -> R.drawable.logo_beyfin
            brand.contains("lukoil") -> R.drawable.logo_lukoil
            brand.contains("retitalia") -> R.drawable.logo_retitalia
            else -> R.drawable.fuelly_logo_foreground
        }
    }

    //funzione che genera un testo formattato con le informazioni del benzinaio,
    // da condividere tramite intent di condivisione,
    fun getShareText(): String {
        //inizializzo un StringBuilder per costruire il testo in modo efficiente
        val sb = StringBuilder()
        sb.append("⛽ *${bandiera}*\n")
        sb.append("📍 ${indirizzo}, ${comune}\n\n")

        //se il prezzo è maggiore di 0, lo aggiungo al testo formattato (con 3 decimali e il simbolo dell'euro),
        if (prezzoBenzina > 0) sb.append("🟢 Benzina: ${String.format("%.3f", prezzoBenzina)}€\n")
        if (prezzoDiesel > 0) sb.append("🟡 Diesel: ${String.format("%.3f", prezzoDiesel)}€\n")

        //creo il link a Google Maps del benzinaio, usando le coordinate di latitudine e longitudine
        sb.append("\nGuarda su Google Maps:\n")
        sb.append("https://www.google.com/maps/search/?api=1&query=${lat},${lon}")

        return sb.toString()
    }
}