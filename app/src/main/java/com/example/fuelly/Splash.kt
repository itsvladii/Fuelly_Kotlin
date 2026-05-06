package com.example.fuelly

import android.Manifest
import android.content.Intent
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.fuelly.classes.*
import com.example.fuelly.utils.Utils
import com.example.fuelly.supabase.SupabaseInstance
import com.google.android.gms.location.LocationServices
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.postgrest.*
import kotlinx.coroutines.*

class Splash : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)
        supportActionBar?.hide()
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false  // icone status bar bianche
        windowInsetsController.isAppearanceLightNavigationBars = false  // icone nav bar bianche

        try {
            //richiedo i permessi alla mappa
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
        }
        catch (e: SecurityException) {
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
                    // Il GPS è spento, mostriamo il popup di sistema per attivarlo
                    exception.startResolutionForResult(this, 200)
                } catch (sendEx: android.content.IntentSender.SendIntentException) {
                    // Errore nel mostrare il popup, procediamo al login
                    vaiALogin()
                }
            } else {
                // Dispositivo non supportato o errore grave
                vaiALogin()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 200) {
            if (resultCode == RESULT_OK) {
                // L'utente ha attivato il GPS!
                avviaPrecaricamento()
            } else {
                // L'utente ha rifiutato di attivare il GPS
                Toast.makeText(this, "Il GPS è necessario per i prezzi vicini", Toast.LENGTH_SHORT).show()
                vaiALogin()
            }
        }
    }

    private fun vaiALogin() {
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }, 1500)
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun avviaPrecaricamento() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Definiamo cosa fare quando otteniamo la posizione (sia da cache che da nuova richiesta)
        val onLocationReceived: (android.location.Location?) -> Unit = { location ->
            if (location != null) {
                eseguiQueryDatabase(location)
            } else {
                Log.e("Fuelly", "Impossibile ottenere la posizione anche dopo il refresh")
                vaiALogin()
            }
        }

        // 1. Proviamo prima con la posizione rapida in cache
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                onLocationReceived(location)
            } else {
                // 2. Se è NULL (comune dopo aver appena attivato il GPS), forziamo un aggiornamento
                Log.d("Fuelly", "LastLocation null, richiedo posizione attuale...")

                val priority = com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
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

    private fun eseguiQueryDatabase(location: android.location.Location) {
        lifecycleScope.launch {
            try {
                Log.d("Fuelly", "Eseguo query per: ${location.latitude}, ${location.longitude}")

                // --- Chiamata Benzinai ---
                val response = SupabaseInstance.client.postgrest.rpc(
                    function = "get_benzinai_vicini",
                    parameters = mapOf(
                        "user_lat" to location.latitude,
                        "user_lon" to location.longitude,
                        "raggio_km" to 10.0
                    )
                )
                Benzinaio.listaVicini = Benzinaio.parseLista(response.data)

                // --- Chiamata Colonnine ---
                val responseEV = SupabaseInstance.client.postgrest.rpc(
                    function = "get_colonnine_vicine",
                    parameters = mapOf(
                        "user_lat" to location.latitude,
                        "user_lon" to location.longitude,
                        "raggio_km" to 10.0
                    )
                )
                ColonninaEV.listaVicini = ColonninaEV.parseLista(responseEV.data)

                // --- Gestione Sessione e Navigazione ---
                gestisciNavigazionePostCaricamento()

            } catch (e: Exception) {
                Log.e("Fuelly", "Errore Supabase: ${e.message}")
                vaiALogin()
            }
        }
    }

    private suspend fun gestisciNavigazionePostCaricamento() {
        val session = SupabaseInstance.client.auth.currentSessionOrNull()
        if (session != null) {
            Utils.caricaSalvati(session)
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }

}
