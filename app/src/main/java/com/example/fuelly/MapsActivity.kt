package com.example.fuelly

import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.fuelly.classes.*
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
import androidx.core.graphics.toColorInt


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    //lista di tutti i marker della mappa, necessario salvarli tutti in modo tale da renderli accessibili dal filtro
    private val markersBenzina = mutableListOf<com.google.android.gms.maps.model.Marker>()
    private val markersEV = mutableListOf<com.google.android.gms.maps.model.Marker>()

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        //variabili booleane per il filtro
        var benzinaAttiva = true
        var evAttivo = true

        //bottoni per il filtro
        val btnFiltroBenzina = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.btnFiltroBenzina)
        val btnFiltroEV = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.btnFiltroEV)


        //event handler per il click dei bottoni di filtro
        btnFiltroBenzina.setOnClickListener {
            benzinaAttiva = !benzinaAttiva // Inverte lo stato (ON/OFF)

            //se spento diventa semitrasparente
            binding.btnFiltroBenzina.alpha = if (benzinaAttiva) 1.0f else 0.5f
            //richiamo la funzione di filtro
            filtraMarker(benzinaAttiva, evAttivo)
        }

        btnFiltroEV.setOnClickListener {
            evAttivo = !evAttivo
            //se spento diventa semitrasparente
            binding.btnFiltroEV.alpha = if (evAttivo) 1.0f else 0.5f
            //richiamo la funzione di filtro
            filtraMarker(benzinaAttiva, evAttivo)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        try {
            //IMPOSTAZIONE DELLO STILE CUSTOM DELLA MAPPA
            val success = mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style))
            if (!success) Log.e("Fuelly", "Errore nel caricamento dello stile JSON.")

            //IMPOSTAZIONI DELLA MAPPA
            mMap.isBuildingsEnabled = false //rimuovo gli edifici 3D
            mMap.uiSettings.isTiltGesturesEnabled = false //rimuovo la gesture che tilta la mappa di 30° (non serve)
            mMap.uiSettings.isRotateGesturesEnabled = false //blocco la mappa in portrait

            //CARICAMENTO DEI MARKER PER BENZINAI SULLA MAPPA
            val benzinaioDaMostrare = Benzinaio.listaVicini //prelevo i benzinai vicini dal companion object
            val iconaCustom = BitmapDescriptorFactory.fromResource(R.drawable.pin_fuel) //icona custom del marker

            //ciclo tutti i benzinai vicini e creo un marker per ognuno di essi
            if (benzinaioDaMostrare.isNotEmpty()) {
                for (stazione in benzinaioDaMostrare) {
                    val marker = mMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(stazione.lat, stazione.lon))
                            .icon(iconaCustom)
                            .anchor(0.5f, 0.5f)
                    )
                    //a ogni marker aggiungo un tag con l'oggetto di tipo Benzina
                    marker?.tag = stazione
                    marker?.let { markersBenzina.add(it) }
                }


                // SPOSTA LA CAMERA SUL PRIMO BENZINAIO TROVATO
                val focus = LatLng(benzinaioDaMostrare[0].lat, benzinaioDaMostrare[0].lon)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(focus, 12f))
                mMap.uiSettings.isMapToolbarEnabled = false
            }

            //CARICAMENTO DEI MARKER PER COLONNINE EV SULLA MAPPA
            //icona custom del marker per le colonnine
            val iconaEV = BitmapDescriptorFactory.fromResource(R.drawable.ev_pin)

            //ciclo tutte le colonnine vicine e creo un marker per ognuno di esse
            for (ev in ColonninaEV.listaVicini) {
                val marker=mMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(ev.lat, ev.lon))
                        .icon(iconaEV)
                        .anchor(0.5f, 0.5f)
                ) // Qui il tag sarà un oggetto ColonninaEV
                marker?.tag = ev
                marker?.let { markersEV.add(it) }
            }

            //CARICAMENTO DELLA CARD UNA VOLTA CLICCATO UN MARKER
            val card = findViewById<androidx.cardview.widget.CardView>(R.id.stationCard)
            //event handler per il click sul marker
            mMap.setOnMarkerClickListener { marker ->
                val data = marker.tag

                //se il marker appartiene a un benzinaio, genero la card per i benzinai, altrimenti per le colonnine
                if (data is Benzinaio) {
                    setupCardBenzinaio(data)
                } else if (data is ColonninaEV) {
                    setupCardElettrica(data)
                }

                // animazione di comparsa della card
                card.visibility = View.VISIBLE
                card.alpha = 0f
                card.animate().alpha(1f).setDuration(300).start()

                false

            }

            //event handler se la mappa viene cliccata
            mMap.setOnMapClickListener {
                //se la card è visibile, nascondila
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

    //funzione di setup della card per i benzinai e per le colonnine
    private fun setupCardBenzinaio(b: Benzinaio) {
        val card = findViewById<androidx.cardview.widget.CardView>(R.id.stationCard)
        // Colore Giallo Fuelly
        card.setCardBackgroundColor("#DFFF00".toColorInt())

        // Testi
        findViewById<TextView>(R.id.txtStationName).text = b.nomeImpianto
        findViewById<TextView>(R.id.txtStationAddress).text = b.indirizzo
        findViewById<TextView>(R.id.txtPrice).text = "6.767 €/L"

        // Icona Pompa
        findViewById<ImageView>(R.id.imgPompa).setImageResource(R.drawable.fuel_logo)
    }
    private fun setupCardElettrica(ev: ColonninaEV) {
        val card = findViewById<androidx.cardview.widget.CardView>(R.id.stationCard)
        // Colore Azzurro/Verde Acqua (come nel tuo mockup)
        card.setCardBackgroundColor(android.graphics.Color.parseColor("#00FFC2"))

        // Testi
        findViewById<TextView>(R.id.txtStationName).text = ev.titolo
        findViewById<TextView>(R.id.txtStationAddress).text = ev.indirizzo
        findViewById<TextView>(R.id.txtPrice).text = "${ev.potenzaKW} kW"

        // Icona Fulmine
        findViewById<ImageView>(R.id.imgPompa).setImageResource(R.drawable.ev_logo)
    }

    //funzione di filtro dei marker in base al booleano passato
    private fun filtraMarker(mostraBenzina: Boolean, mostraEV: Boolean) {
        markersBenzina.forEach { it.isVisible = mostraBenzina }
        markersEV.forEach { it.isVisible = mostraEV }

        val card = findViewById<androidx.cardview.widget.CardView>(R.id.stationCard)
        card.visibility = View.GONE
    }
}