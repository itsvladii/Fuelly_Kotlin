package com.example.fuelly

import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.fuelly.classes.Benzinai
import com.example.fuelly.databinding.ActivityMapsBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import androidx.core.view.isVisible

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        try {
            // 1. Configurazione Estetica (Stile Dark e impostazioni UI)
            val success = mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style))
            if (!success) Log.e("Fuelly", "Errore nel caricamento dello stile JSON.")

            mMap.isBuildingsEnabled = false
            mMap.uiSettings.isTiltGesturesEnabled = false
            mMap.uiSettings.isRotateGesturesEnabled = false // Opzionale: blocca rotazione per UX più pulita

            // 2. Caricamento Marker dal Companion Object
            val benzinaiDaMostrare = Benzinai.listaVicini
            val iconaCustom = BitmapDescriptorFactory.fromResource(R.drawable.pin_fuel)

            if (benzinaiDaMostrare.isNotEmpty()) {
                for (stazione in benzinaiDaMostrare) {
                    val marker = mMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(stazione.lat, stazione.lon))
                            .icon(iconaCustom)
                            .anchor(0.5f, 0.5f)
                    )
                    // Associao l'oggetto stazione al marker
                    marker?.tag = stazione
                }


                // SPOSTA LA CAMERA SUL PRIMO BENZINAIO TROVATO
                val focus = LatLng(benzinaiDaMostrare[0].lat, benzinaiDaMostrare[0].lon)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(focus, 12f))
                mMap.uiSettings.isMapToolbarEnabled = false
            }

            val card = findViewById<androidx.cardview.widget.CardView>(R.id.stationCard)
            // 3. Gestione Click sul Marker (Mostra Card)
            mMap.setOnMarkerClickListener { marker ->
                val stazione = marker.tag as? Benzinai
                if (stazione != null) {
                    // Popoliamo i testi della card con i dati dell'oggetto cliccato
                    findViewById<TextView>(R.id.txtStationName).text = stazione.nomeImpianto
                    findViewById<TextView>(R.id.txtStationAddress).text = stazione.indirizzo
                    findViewById<TextView>(R.id.txtPrice).text = "1.789 €/L" // Per ora fisso, o stazione.prezzo se presente


                    // Mostriamo la card con una piccola animazione
                    card.visibility = View.VISIBLE
                    card.alpha = 0f
                    card.animate().alpha(1f).setDuration(300).start()
                }
                false // Ritorna false per far sì che la mappa faccia comunque l'animazione di default sul marker
            }

            // 4. Gestione Click sulla Mappa (Nascondi Card)
            mMap.setOnMapClickListener {
                if (card.isVisible) {
                    card.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction { card.visibility = View.GONE }
                        .start()
                }
            }

        } catch (e: Resources.NotFoundException) {
            Log.e("Fuelly", "File map_style non trovato: ", e)
        }
    }
}