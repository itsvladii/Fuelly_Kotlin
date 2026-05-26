package com.example.fuelly

import android.Manifest
import android.content.Intent
import android.os.*
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.fuelly.repository.model.Benzinaio
import com.example.fuelly.repository.model.ColonninaEV
import com.example.fuelly.utils.Utils
import com.example.fuelly.repository.supabase.SupabaseInstance
import com.google.android.gms.location.LocationServices
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.*
import kotlinx.coroutines.*

class Splash : AppCompatActivity() {
    private lateinit var progressBar: LinearProgressIndicator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)
        supportActionBar?.hide()
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false  // icone status bar bianche
        windowInsetsController.isAppearanceLightNavigationBars = false  // icone nav bar bianche
        progressBar = findViewById(R.id.progressIndicator)
        try {
            //richiedo i permessi alla mappa
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
        } catch (e: SecurityException) {
            Log.e("Fuelly", "Errore di sicurezza: ${e.message}")
        }

    }

    //funzione che viene invocata quando l'utente risponde al popup dei permessi (override del metodo gia presente)
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                //se l'utente ha autorizzato l'accesso alla geolocalizzazione, passa al precaricamento dei marker
                Log.d("Fuelly", "Permesso accordato, avvio caricamento...")
                controllaGpsEAvvia()
            } else {
                //se l'utente ha negato, passa comunque ma non avrà i marker
                Log.d("Fuelly", "Permesso negato")
                Toast.makeText(
                    this,
                    "Permesso necessario per trovare i distributori vicini",
                    Toast.LENGTH_LONG
                ).show()
                Handler(Looper.getMainLooper()).postDelayed({
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }, 2000)
            }
        }
    }

    //funzione che controlla se il GPS è attivo e, in caso contrario, mostra il popup di sistema per attivarlo
    private fun controllaGpsEAvvia() {
        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 10000
        ).build()

        val builder = com.google.android.gms.location.LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        val settingsClient = LocationServices.getSettingsClient(this)
        val task = settingsClient.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            // Il GPS è attivo, procediamo con il precaricamento
            avviaPrecaricamento()
        }

        task.addOnFailureListener { exception ->
            if (exception is com.google.android.gms.common.api.ResolvableApiException) {
                try {
                    //se il GPS è spento, mostriamo il popup di sistema per attivarlo
                    exception.startResolutionForResult(this, 200)
                } catch (sendEx: android.content.IntentSender.SendIntentException) {
                    //errore nel mostrare il popup, procediamo al login
                    vaiALogin()
                }
            } else {
                //come fallback, se c'è un errore diverso, procediamo comunque al login
                // (anche se senza GPS non avremo i marker vicini)
                vaiALogin()
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 200) {
            if (resultCode == RESULT_OK) {
                //se l'utente ha accettato di attivare il GPS, procediamo con il precaricamento
                avviaPrecaricamento()
            } else {
                //se l'utente ha rifiutato di attivare il GPS, mostriamo un messaggio e procediamo al login
                // (senza GPS non avremo i marker vicini)
                Toast.makeText(this, getString(R.string.gps_required), Toast.LENGTH_SHORT).show()
                vaiALogin()
            }
        }
    }

    //funzione di passaggio alla schermata di login,
    //con un piccolo delay per permettere all'utente di leggere eventuali messaggi
    private fun vaiALogin() {
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }, 1500)
    }

    //funzione che si occupa di ottenere la posizione dell'utente
    // e di eseguire le query al database per precaricare i marker vicini
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun avviaPrecaricamento() {
        runOnUiThread { progressBar.visibility = View.VISIBLE }
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        //se otteniamo la posizione, eseguiamo le query al database,
        // altrimenti mostriamo un messaggio di errore e procediamo al login
        val onLocationReceived: (android.location.Location?) -> Unit = { location ->
            if (location != null) {
                eseguiQueryDatabase(location)
            } else {
                Log.e("Fuelly", "Impossibile ottenere la posizione anche dopo il refresh")
                vaiALogin()
            }
        }

        //prima otteniamo la posizione dalla cache
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                onLocationReceived(location)
            } else {
                // se non abbiamo un ultima posizione nella cache, la calcoliamo
                Log.d("Fuelly", "LastLocation null, richiedo posizione attuale...")

                //priority HIGH_ACCURACY per ottenere la posizione più precisa possibile
                val priority = com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
                //otteniamo la posizione attuale
                fusedLocationClient.getCurrentLocation(priority, null)
                    .addOnSuccessListener { newLocation ->
                        onLocationReceived(newLocation)
                    }
                    .addOnFailureListener {
                        Log.e("Fuelly", "Errore richiesta posizione: ${it.message}")
                        vaiALogin()
                    }
            }
        }
    }

    //funzione che esegue le query al database per ottenere i marker vicini
    private fun eseguiQueryDatabase(location: android.location.Location) {
        lifecycleScope.launch {
            try {
                Log.d("Fuelly", "Eseguo query per: ${location.latitude}, ${location.longitude}")

                //ricavo i benzinai vicini tramite la funzione rpc su Supabase
                val response = SupabaseInstance.client.postgrest.rpc(
                    function = "get_benzinai_vicini",
                    parameters = mapOf(
                        "user_lat" to location.latitude,
                        "user_lon" to location.longitude,
                        "raggio_km" to 10.0
                    )
                )
                Benzinaio.listaVicini = Benzinaio.parseLista(response.data)

                //ricavo i benzinai più salvati da tutti gli utenti tramite la funzione rpc su Supabase
                // (senza parameters, quindi è una SELECT *)
                val responseTop = SupabaseInstance.client.postgrest.rpc(
                    function = "get_benzinai_piu_salvati"
                )

                //salviamo i dati nella variabile globale
                Benzinaio.listaTopSalvatiIds = Benzinaio.parseTopSalvatiIds(responseTop.data)

                //ricavo le colonnine vicine tramite la funzione rpc su Supabase
                val responseEV = SupabaseInstance.client.postgrest.rpc(
                    function = "get_colonnine_vicine",
                    parameters = mapOf(
                        "user_lat" to location.latitude,
                        "user_lon" to location.longitude,
                        "raggio_km" to 10.0
                    )
                )
                //salviamo i dati nella variabile globale
                ColonninaEV.listaVicini = ColonninaEV.parseLista(responseEV.data)

                //richiamo la funzione che gestisce la navigazione dopo il caricamento,
                //  che decide se andare al login o alla home
                gestisciNavigazionePostCaricamento()

            } catch (e: Exception) {
                Log.e("Fuelly", "Errore Supabase: ${e.message}")
                runOnUiThread { progressBar.visibility = View.INVISIBLE }
                vaiALogin()
            }
        }
    }

    //funzione che gestisce la navigazione dopo il caricamento,
    private suspend fun gestisciNavigazionePostCaricamento() {
        //se l'utente è già loggato, salviamo i dati dei benzinai e delle colonnine salvati
        val session = SupabaseInstance.client.auth.currentSessionOrNull()
        if (session != null) {
            Utils.colonnineSalvate(session)
            Utils.benzinaiSalvati(session)
            //dopo aver precaricato i dati, passiamo alla home
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            //se l'utente non è loggato, passiamo al login
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }

}
