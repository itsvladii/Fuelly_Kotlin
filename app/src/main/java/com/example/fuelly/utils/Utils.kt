package com.example.fuelly.utils

import android.util.Log
import com.example.fuelly.classes.Benzinaio
import com.example.fuelly.supabase.SupabaseInstance
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc

object Utils {
    /**
     * Carica i benzinai salvati dall'utente nel contenitore statico [Benzinaio.listaSalvati].
     * @param session La sessione dell'utente corrente.
     */
    suspend fun caricaSalvati(session: UserSession) {
        try {
            // Richiamo la funzione RPC del DB per ricavare i benzinai salvati dall'utente
            // (passando l'id dell'utente dalla sessione come parametro)
            val response = SupabaseInstance.client.postgrest.rpc(
                function = "get_benzinai_salvati",
                parameters = mapOf("user_id" to session.user?.id)
            )

            // Parsing della risposta JSON in una lista di oggetti Benzinaio
            val salvati = Benzinaio.parseLista(response.data)
            Benzinaio.listaSalvati = salvati

            Log.d("Fuelly", "Caricati ${Benzinaio.listaSalvati.size} benzinai salvati")
        } catch (e: Exception) {
            Log.e("Fuelly", "Errore caricamento salvati: ${e.message}")
        }
    }
}
