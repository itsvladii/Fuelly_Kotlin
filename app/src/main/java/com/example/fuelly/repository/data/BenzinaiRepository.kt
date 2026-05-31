package com.example.fuelly.repository.data

import android.util.Log
import com.example.fuelly.repository.model.Benzinaio
import com.example.fuelly.repository.model.Info
import com.example.fuelly.repository.model.Salvato
import com.example.fuelly.repository.model.Recensione
import com.example.fuelly.repository.supabase.SupabaseInstance
import io.github.jan.supabase.postgrest.*
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlin.collections.mapOf

/*
* FUNZIONI DI:
* - FETCHING DEI BENZINAI VICINI
* - FETCHING DEI BENZINAI PIU SALVATI
* - FETCHING DEI BENZINAI SALVATI DALL'UTENTE
* - AGGIUNTA/RIMOZIONE DEI BENZINAI DAI SALVATI DELL'UTENTE
* - VERIFICA SE IL BENZINAIO E' TRA I SALVATI
* - FETCHING/RIMOZIONE/AGGIUNTA DELLE RECENSIONI NEI BENZINAI
* - FETCHING DELLE RECENSIONI DEI BENZINAI SCRITTE DALL'UTENTE
* - FETCHING/SALVATAGGIO DELLE INFO AGGIUNTIVE DEI BENZINAI
* - FETCHING DEL NOME DEL GESTORE DEI BENZINAI
*/
class BenzinaiRepository {
    //creo il client Supabase
    private val client= SupabaseInstance.client

    //funzione di fetching dei benzinai vicini all'utente (raggio di 10km) dal DB
    suspend fun getBenzinaiVicini(lat: Double, lon: Double): List<Benzinaio>
    {
        //richiamo la funzione RPC get_benzinai_vicini dal DB
        val response = client.postgrest.rpc(
            function = "get_benzinai_vicini",
            parameters = mapOf("user_lat" to lat, "user_lon" to lon, "raggio_km" to 10.0)
        )
        //passo l'output della query alla funzione di parsing all'interno di Benzinaio,
        // cosi da ottenere la List dei oggetti Benzinaio
        return Benzinaio.parseLista(response.data)

    }

    //funzione che recupera gli ID dei benzinai più salvati da tutti gli utenti
    suspend fun getTopSalvatiIds(): List<Int> {
        val response = client.postgrest.rpc(function = "get_top_salvati")
        //passo l'output della query alla funzione di parsing all'interno di Benzinaio,
        //cosi da ottenere la List dei oggetti Benzinaio
        return Benzinaio.parseTopSalvatiIds(response.data)
    }

    //funzione che recupera i benzinai salvati dall'utente
    suspend fun getBenzinaiSalvati(userId: String): List<Benzinaio> {
        // 1. Caricamento Benzinai
        val response = SupabaseInstance.client.postgrest.rpc(
            function = "get_benzinai_salvati",
            parameters = mapOf("user_id" to userId)
        )

        //Log di eventuale successo
        Log.d("Fuelly", "Caricati ${Benzinaio.listaSalvati.size} benzinai")

        //Riempo la lista salvati dell'oggetto Benzinaio con il metodo parseLista
        return Benzinaio.parseLista(response.data)
    }

    //funzione che aggiunge/rimuove un benzinaio dai salvati dell'utente
    suspend fun toggleSalvato(userId: String, idImpianto: Long): Boolean {
        val tabella = "salvati_benzinai"
        val esistente = client.from(tabella)
            .select { filter { eq("idUtente", userId); eq("idImpianto", idImpianto) } }
            .decodeList<Salvato>()

        if (esistente.isNotEmpty()) {
            client.from(tabella).delete { filter { eq("idUtente", userId); eq("idImpianto", idImpianto) } }
            return false // ora non è più salvato
        } else {
            client.from(tabella).insert(Salvato(userId,idImpianto))
            return true  // ora è salvato
        }
    }

    //funzione che verifica se un benzinaio è già salvato dall'utente
    suspend fun isBenzinaioSalvato(userId: String, idImpianto: Long): Boolean {
        val tabella = "salvati_benzinai"
        val esistente = client.from(tabella)
            .select { filter { eq("idUtente", userId); eq("idImpianto", idImpianto) } }
            .decodeList<Salvato>()

        return if (esistente.isNotEmpty()) {
            true //è salvato
        } else {
            false //non è salvato
        }
    }

    //funzione di fetching delle recensioni del benzinaio
    suspend fun getRecensioni(idImpianto: Long): List<Recensione> {
        return client.from("recensioni_benzinai")
            .select {
                filter { eq("idImpianto", idImpianto) }
                order("created_at", order = Order.DESCENDING)
            }
            .decodeList<Recensione>()
    }

    //funzione di inserimento di una recensione nel benzinaio
    suspend fun inserisciRecensione(recensione: Recensione) {
        client.from("recensioni_benzinai").insert(recensione)
    }

    //funzione di eliminazione di una recensione del benzinaio
    suspend fun eliminaRecensione(idRecensione: String, idUtente: String) {
        client.from("recensioni_benzinai").delete {
            filter {
                eq("idRecensione", idRecensione)
                eq("idUtente", idUtente)
            }
        }
    }

    //funzione di fetching di tutte le recensioni dei benzinai scritte dall'utente
    suspend fun getRecensioniUtente(userId: String): List<Recensione> {
        return client.from("recensioni_benzinai")
            .select { filter { eq("idUtente", userId) } }
            .decodeList<Recensione>()
            .map { it.copy(tipo = "BENZINA") } //
    }

    //funzione di fetching del gestore del benzinaio
    suspend fun getGestore(idImpianto: Long): String {
        val result = client.from("benzinai")
            .select(columns = Columns.list("Gestore")) {
                filter { eq("idImpianto", idImpianto) }
            }
            .decodeSingle<Map<String, String>>()
        return result["Gestore"] ?: "Sconosciuto"
    }

    //funzione di fetching delle info del benzinaio
    suspend fun getInfo(idImpianto: Long): Info? {
        val result = client.from("info_benzinai")
            .select { filter { eq("idImpianto", idImpianto) } }
            .decodeList<Info>()
        return result.lastOrNull() // se ci sono più righe, prendiamo l'ultima (la più recente)
    }

    //funzione di salvataggio delle info del benzinaio
    suspend fun salvaInfo(info: Info): Info {
        return client.from("info_benzinai")
            .upsert(info) { select() }
            .decodeSingle<Info>()
    }

}