package com.example.fuelly

import android.content.Intent
import android.os.*
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fuelly.supabase.SupabaseInstance
import com.google.android.gms.location.LocationServices
import io.github.jan.supabase.postgrest.*
import kotlinx.coroutines.launch
class Splash : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        supportActionBar?.hide()

        try {
            //richiedo i permessi alla mappa
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 100)

            //carico i dati dal db
            avviaPrecaricamento()
        }
        catch (e: SecurityException) {
            Log.e("Fuelly", "Errore di sicurezza: ${e.message}")
        }

    }

    //Funzione di caricamento dei benzinai vicini dal DB
    private fun avviaPrecaricamento(){

        //imposto il client per la geolocalizzazione
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        //prendo la posizione dell'utente tramite il fusedLocationClient
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            lifecycleScope.launch {
                try {
                    //se ho la posizione, prendo i benzinai vicini alla posizione dell'utente
                    if (location != null) {
                        //effettuo la funzione rpc su supabase
                        Log.d("Fuelly", "Posizione trovata: ${location.latitude}, ${location.longitude}")
                        SupabaseInstance.client.postgrest.rpc(
                            function = "get_benzinai_vicini",
                            parameters = mapOf(
                                "user_lat" to location.latitude,
                                "user_lon" to location.longitude,
                                "raggio_km" to 25.0
                            )
                        )
                        Log.d("Fuelly", "Dati vicini scaricati!")
                    }
                } catch (e: Exception) {
                    Log.e("Fuelly", "Errore Supabase: ${e.message}")
                } finally {
                    //finita la query (o se non riesce ad eseguirla), passo all'activity della mappa
                    goToMappa()
                }
            }
        }

        //se non ho la posizione, passo all'activity della mappa comunque
        Handler(Looper.getMainLooper()).postDelayed({
            goToMappa()
        }, 3000)
    }



    //Funzione di passaggio all'activity della mappa
    private fun goToMappa() {
        // Evitiamo di aprire l'activity due volte
        if (!isFinishing) {
            val intent = Intent(this, MapsActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

}