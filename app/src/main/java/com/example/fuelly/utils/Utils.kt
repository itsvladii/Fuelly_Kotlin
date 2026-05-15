package com.example.fuelly.utils

import android.util.Log
import com.example.fuelly.classes.Benzinaio
import com.example.fuelly.classes.ColonninaEV
import com.example.fuelly.supabase.SupabaseInstance
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc

object Utils {
    /**
     * Carica i benzinai salvati dall'utente nel contenitore statico [Benzinaio.listaSalvati].
     * @param session La sessione dell'utente corrente.
     */

    suspend fun BenzinaiSalvati(session:UserSession)
    {
        val userId = session.user?.id ?: return // Esci se non c'è un utente loggato

        try
        {
            // 1. Caricamento Benzinai
            val respBenzinai = SupabaseInstance.client.postgrest.rpc(
                function = "get_benzinai_salvati",
                parameters = mapOf("user_id" to userId)
            )

            //Riempo la listasalvati dell'oggeto Benzinaio con il metodo parseLista
            Benzinaio.listaSalvati = Benzinaio.parseLista(respBenzinai.data)

            //Log di eventuale successo
            Log.d("Fuelly", "Caricati ${Benzinaio.listaSalvati.size} benzinai")

        }catch(e:Exception)
        {
            Log.e("Fuelly", "Errore caricamento benzinai: ${e.message}")
            Benzinaio.listaSalvati = emptyList() // Evita null pointer
        }
    }

    suspend fun ColonnineSalvate(session:UserSession)
    {
        val userId = session.user?.id ?: return // Esci se non c'è un utente loggato

        try
        {
            // 2. Caricamento Colonnine EV
            val respColonnine = SupabaseInstance.client.postgrest.rpc(
                function = "get_colonnine_salvati",
                parameters = mapOf("user_id" to userId)
            )

            //Riempo la listasalvati dell'oggeto ColonninaEV con il metodo parseLista
            ColonninaEV.listaSalvati = ColonninaEV.parseLista(respColonnine.data)

            //Log di eventuale successo
            Log.d("Fuelly", "Caricati ${ColonninaEV.listaSalvati.size} colonnine")

        }catch(e:Exception)
        {
            Log.e("Fuelly", "Errore caricamento colonnine: ${e.message}")
            ColonninaEV.listaSalvati = emptyList()
        }
    }

    fun calcolaDistanza(latUser: Double, lonUser: Double, latItem: Double, lonItem: Double): Double {
        val start = android.location.Location("A").apply { latitude = latUser; longitude = lonUser }
        val end = android.location.Location("B").apply { latitude = latItem; longitude = lonItem }
        return (start.distanceTo(end)).toDouble()
    }
}
