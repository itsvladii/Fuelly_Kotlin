package com.example.fuelly

import android.content.Intent
import android.os.*
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fuelly.classes.Benzinaio
import com.example.fuelly.classes.ColonninaEV
import com.example.fuelly.supabase.SupabaseInstance
import com.google.android.gms.location.LocationServices
import io.github.jan.supabase.postgrest.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Splash : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        supportActionBar?.hide()

        try {
            //richiedo i permessi alla mappa
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 100)

            //carico i dati dei benzinai e delle colonnine
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
                        Log.d("Fuelly", "Sto cercando benzinai vicino a: ${location.latitude}, ${location.longitude}")
                        val response= SupabaseInstance.client.postgrest.rpc(
                            function = "get_benzinai_vicini",
                            parameters = mapOf(
                                "user_lat" to location.latitude,
                                "user_lon" to location.longitude,
                                "raggio_km" to 10.0
                            )
                        )
                        //salvo i benzinai vicini in una lista
                        val listaTutti = Benzinaio.parseLista(response.data)
                        Benzinaio.listaVicini = listaTutti
                        Log.d("Fuelly", "Lista di benzinai vicini: ${Benzinaio.listaVicini.count()}")

                        //raccolgo le colonnine di ricarica vicine tramite la chiamata API e le salvo in una lista
                        val jsonEV = fetchColonnineEV(location.latitude, location.longitude)
                        ColonninaEV.listaVicini = ColonninaEV.parseLista(jsonEV)
                        Log.d("Fuelly", "Caricate ${ColonninaEV.listaVicini.size} colonnine elettriche!")
                    }
                } catch (e: Exception) {
                    Log.e("Fuelly", "Errore Supabase: ${e.message}")
                } finally {
                    //finita la query (o se non riesce a eseguirla), passo all'activity della mappa comunque
                    goToMappa()
                }
            }
        }

        //se non ho la posizione, passo all'activity della mappa comunque
        Handler(Looper.getMainLooper()).postDelayed({
            goToMappa()
        }, 10000)
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

    // Funzione di fetching dei dati da OpenChargeMap tramite chiamata API
    private suspend fun fetchColonnineEV(lat: Double, lon: Double): String {
        //inizializzo il client con la chiave e l'url di richiesta
        val client = okhttp3.OkHttpClient()
        val apiKey = "fd460f11-3769-451b-b8c9-28a711261ae0" //non proprio una buona pratica ma funziona
        val url = "https://api.openchargemap.io/v3/poi/?output=json&latitude=$lat&longitude=$lon&distance=20&distanceunit=KM&key=$apiKey" //url della richiesta

        //effettuo la richiesta
        val request = okhttp3.Request.Builder().url(url).build()

        //eseguo la richiesta in un thread separato
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                response.body?.string() ?: ""
            }
        }
    }

}