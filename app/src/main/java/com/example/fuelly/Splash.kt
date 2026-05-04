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
                avviaPrecaricamento()
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

    //funzione di caricamento dei benzinai vicini dal DB (necessario il permesso di geolocalizzazione)
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun avviaPrecaricamento(){

        //imposto il client per la geolocalizzazione
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        //prendo la posizione dell'utente tramite il fusedLocationClient
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            lifecycleScope.launch {
                try {
                    //se ho la posizione, prendo i benzinai vicini alla posizione dell'utente
                    if (location != null) {
                        //effettuo la funzione del DB (get_benzinai_vicini) di fetching dei benzinai vicini
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


                        // --- NUOVO: CARICAMENTO COLONNINE DAL TUO DB ---
                        Log.d("Fuelly", "Sto cercando colonnine nel DB vicino a: ${location.latitude}")

                        val responseEV = SupabaseInstance.client.postgrest.rpc(
                            function = "get_colonnine_vicine", // La nuova funzione creata su Supabase
                            parameters = mapOf(
                                "user_lat" to location.latitude,
                                "user_lon" to location.longitude,
                                "raggio_km" to 10.0
                            )
                        )

                        ColonninaEV.listaVicini = ColonninaEV.parseLista(responseEV.data)
                        Log.d("Fuelly", "Caricate ${ColonninaEV.listaVicini.size} colonnine dal DB proprietario!")

                        //controllo se l'utente ha gia effetuato l'accesso
                        try {
                            val session = SupabaseInstance.client.auth.currentSessionOrNull()
                            //se l'utente ha gia effettuato l'accesso, vado direttamente alla MainActivity (che contiene la navbar), altrimenti alla login
                            if (session != null) {
                                //carico i preferiti dell'utente (se loggato)
                                Utils.caricaSalvati(session)

                                val intent = Intent(this@Splash, MainActivity::class.java)
                                startActivity(intent)
                                finish()
                            } else {
                                val intent = Intent(this@Splash, LoginActivity::class.java)
                                startActivity(intent)
                                finish()
                            }
                        }catch (e: Exception){
                            Log.e("Fuelly", "Errore passaggio: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Fuelly", "Errore Supabase: ${e.message}")
                }
            }
        }
    }

}
