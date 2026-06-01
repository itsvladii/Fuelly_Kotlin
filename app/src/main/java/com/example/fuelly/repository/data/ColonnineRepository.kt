package com.example.fuelly.repository.data

import android.util.Log
import com.example.fuelly.repository.model.ColonninaEV
import com.example.fuelly.repository.model.Salvato
import com.example.fuelly.repository.model.Recensione
import com.example.fuelly.repository.supabase.SupabaseInstance
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlin.collections.mapOf
import io.github.jan.supabase.postgrest.query.Order


/*
* FUNZIONI DI:
* - FETCHING DELLE COLONNINE VICINE
* - FETCHING DELLE COLONNINE SALVATE DALL'UTENTE
* - AGGIUNTA/RIMOZIONE DELLE COLONNINE DAI SALVATI DELL'UTENTE
* - VERIFICA SE LA COLONNINA E' TRA I SALVATI
* - FETCHING/RIMOZIONE/AGGIUNTA DELLE RECENSIONI NELLE COLONNINE
* - FETCHING DELLE RECENSIONI DELLE COLONNINE SCRITTE DALL'UTENTE
*/
class ColonnineRepository {

    companion object {
        //lista delle colonnine vicine alla posizione dell'utente
        var listaVicini: List<ColonninaEV> = emptyList()

        //lista delle colonnine salvate dall'utente
        var listaSalvati: List<ColonninaEV> = emptyList()

        //lista che unisce salvati e vicini senza duplicati
        val listaCompleta: List<ColonninaEV>
            get() = (listaSalvati + listaVicini).distinctBy { it.id }
    }

    //creo il client Supabase
    private val client = SupabaseInstance.client

    //funzione di fetching delle colonnine EV vicini all'utente (raggio di 10km) dal DB
    suspend fun getColonnineVicine(lat: Double, lon: Double): List<ColonninaEV> {
        val response = client.postgrest.rpc(
            function = "get_colonnine_vicine",
            parameters = mapOf("user_lat" to lat, "user_lon" to lon, "raggio_km" to 10.0)
        )
        //passo l'output della query alla funzione di parsing all'interno di ColonninaEV,
        // cosi da ottenere la List dei oggetti ColonninaEV
        return ColonninaEV.parseLista(response.data)
    }

    //funzione che recupera le colonnine salvate dall'utente corrente
    suspend fun getColonnineSalvate(userId: String): List<ColonninaEV> {
        // 2. Caricamento Colonnine EV
        val response = SupabaseInstance.client.postgrest.rpc(
            function = "get_colonnine_salvati",
            parameters = mapOf("user_id" to userId)
        )

        //Log di eventuale successo
        Log.d("Fuelly", "Caricati ${listaSalvati.size} colonnine")

        //Riempo la lista salvati dell'oggetto ColonninaEV con il metodo parseLista
        return ColonninaEV.parseLista(response.data)

    }

    //funzione che aggiunge/rimuove una colonnina dai salvati dell'utente
    suspend fun toggleSalvata(userId: String, idImpianto: Long): Boolean {
        val tabella = "salvati_ev"
        val esistente = client.from(tabella)
            .select { filter { eq("idUtente", userId); eq("idImpianto", idImpianto) } }
            .decodeList<Salvato>()

         if (esistente.isNotEmpty()) {
            client.from(tabella).delete { filter { eq("idUtente", userId); eq("idImpianto", idImpianto) } }
             return false
        } else {
            client.from(tabella).insert(Salvato(userId, idImpianto))
             return true
        }
    }

    //funzione che controlla se una colonnina è già salvata dall'utente
    suspend fun isColonninaSalvata(userId: String, idImpianto: Long): Boolean {
        val tabella = "salvati_ev"
        val esistente = client.from(tabella)
            .select { filter { eq("idUtente", userId); eq("idImpianto", idImpianto) } }
            .decodeList<Salvato>()
        return esistente.isNotEmpty()
    }

    //funzione di fetching delle recensioni della colonnina
    suspend fun getRecensioni(idImpianto: Long): List<Recensione> {
        return client.from("recensioni_ev")
            .select {
                filter { eq("idImpianto", idImpianto) }
                order("created_at", order = Order.DESCENDING)
            }
            .decodeList<Recensione>()
    }

    //funzione di inserimento della recensione nella colonnina
    suspend fun inserisciRecensione(recensione: Recensione) {
        client.from("recensioni_ev").insert(recensione)
    }

    //funzione di eliminazione della recensione della colonnina
    suspend fun eliminaRecensione(idRecensione: String, idUtente: String) {
        client.from("recensioni_ev").delete {
            filter {
                eq("idRecensione", idRecensione)
                eq("idUtente", idUtente)
            }
        }
    }

    //funzione di fetching di tutte le recensioni dell'utente sulle colonnine
    suspend fun getRecensioniUtente(userId: String): List<Recensione> {
        return client.from("recensioni_ev")
            .select { filter { eq("idUtente", userId) } }
            .decodeList<Recensione>()
            .map { it.copy(tipo = "EV") }
    }
}