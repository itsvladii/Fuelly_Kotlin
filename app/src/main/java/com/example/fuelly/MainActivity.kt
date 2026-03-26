package com.example.fuelly

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import androidx.lifecycle.lifecycleScope
import com.example.fuelly.classes.Benzinai
import com.example.fuelly.supabase.SupabaseInstance
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fetchDataManually()
    }

    private fun fetchDataManually() {
        lifecycleScope.launch {
            try {
                // 1. Chiamata a Supabase
                val result = SupabaseInstance.client.from("benzinai").select()

                // 2. Otteniamo il JSON grezzo come stringa
                val jsonGrezzo = result.data

                // 3. PARSER MANUALE
                val listaStazioni = mutableListOf<Benzinai>()
                val jsonArray = JSONArray(jsonGrezzo)

                for (i in 0 until jsonArray.length()) {
                    val riga = jsonArray.getJSONObject(i)

                    // Estrazione campo per campo (occhio alle maiuscole/minuscole del DB!)
                    val stazione = Benzinai(
                        id = riga.getInt("idImpianto"),
                        gestore = riga.getString("Gestore"),
                        bandiera = riga.getString("Bandiera"),
                        tipoImpianto = riga.getString("Tipo Impianto"),
                        nomeImpianto = riga.getString("Nome Impianto"),
                        indirizzo = riga.getString("Indirizzo"),
                        comune = riga.getString("Comune"),
                        provincia = riga.getString("Provincia"),
                        lat = riga.getDouble("Latitudine"),
                        lon = riga.getDouble("Longitudine")
                    )
                    listaStazioni.add(stazione)
                }

                Log.d("Fuelly", "Successo! Caricate ${listaStazioni.size} stazioni")

            } catch (e: Exception) {
                Log.e("Fuelly", "Errore: ${e.message}")
            }
        }
    }
}