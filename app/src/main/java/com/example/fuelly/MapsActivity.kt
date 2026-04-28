package com.example.fuelly

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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
import androidx.core.view.WindowCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    //lista di tutti i marker della mappa, necessario salvarli tutti in modo tale da renderli accessibili dal filtro
    private val markersBenzina = mutableListOf<com.google.android.gms.maps.model.Marker>()
    private val markersEV = mutableListOf<com.google.android.gms.maps.model.Marker>()

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false  // icone status bar bianche
        windowInsetsController.isAppearanceLightNavigationBars = false  // icone nav bar bianche


        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        //variabili booleane per il filtro
        var benzinaAttiva = true
        var evAttivo = true

        //bottoni per il filtro
        val btnFiltroBenzina = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.btnFiltroBenzina)
        val btnFiltroEV = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.btnFiltroEV)
        val btnMyLocation = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.btnMyLocation)


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

        btnMyLocation.setOnClickListener {
            moveToCurrentLocation()
        }

        // Listener per la navigazione
        findViewById<LinearLayout>(R.id.btnNavSalvati).setOnClickListener {
            startActivity(Intent(this, SalvatiActivity::class.java))
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
            mMap.uiSettings.isMapToolbarEnabled = false

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mMap.isMyLocationEnabled = true
                mMap.uiSettings.isMyLocationButtonEnabled = false // Nascondiamo quello di default perché ne usiamo uno custom
                moveToCurrentLocation()
            }

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

                // Se non siamo riusciti a spostarci sulla posizione attuale (es. permessi o GPS spento), focus sul primo benzinaio
                if (!mMap.isMyLocationEnabled) {
                    val focus = LatLng(benzinaioDaMostrare[0].lat, benzinaioDaMostrare[0].lon)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(focus, 14f))
                }
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
            val bottomNav = findViewById<LinearLayout>(R.id.customBottomNav)
            val btnMyLocation = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.btnMyLocation)


            //event handler per il click sul marker
            mMap.setOnMarkerClickListener { marker ->
                val data = marker.tag

                //se il marker appartiene a un benzinaio, genero la card per i benzinai, altrimenti per le colonnine
                if (data is Benzinaio) {
                    setupCardBenzinaio(data)
                } else if (data is ColonninaEV) {
                    setupCardElettrica(data)
                }

                //animazione di scomparsa della bottomNav
                bottomNav.animate()
                    .translationY(600f) // La sposta fuori dallo schermo in basso
                    .setDuration(300)
                    .start()

                //animazione di scomparsa del btnMyLocation
                btnMyLocation.animate()
                    .translationY(600f)
                    .setDuration(300)
                    .start()


                // animazione di comparsa della card
                card.visibility = View.VISIBLE
                card.alpha = 0f
                card.animate().alpha(1f).setDuration(300).start()

                card.setOnClickListener {
                    if (data is Benzinaio) {
                        apriDettaglio(data.id.toLong(), "BENZINA")
                    } else if (data is ColonninaEV) {
                        apriDettaglio(data.id.toLong(), "EV")
                    }
                }

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

                    //mostro la bottomNav
                    bottomNav.animate()
                        .translationY(0f) //torna alla posizione iniziale
                        .setDuration(300)
                        .start()

                    //mostro il btnMyLocation
                    btnMyLocation.animate()
                        .translationY(0f)
                        .setDuration(300)
                        .start()
                }
            }


        } catch (e: Resources.NotFoundException) {
            Log.e("Fuelly", "File map_style non trovato: ", e)
        }
    }

    private fun moveToCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val currentLatLng = LatLng(location.latitude, location.longitude)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 14f))
            }
        }
    }

    //funzione di setup della card per i benzinai e per le colonnine
    private fun setupCardBenzinaio(b: Benzinaio) {
        val card = findViewById<androidx.cardview.widget.CardView>(R.id.stationCard)
        //impostazione del colore della card
        card.setCardBackgroundColor("#0B3D2E".toColorInt())

        //testo della card
        findViewById<TextView>(R.id.txtStationName).setTextColor("#DFFF00".toColorInt())
        findViewById<TextView>(R.id.txtStationAddress).setTextColor("#DFFF00".toColorInt())
        findViewById<TextView>(R.id.txtPrice).setTextColor("#DFFF00".toColorInt())
        findViewById<TextView>(R.id.txtStationName).text = b.bandiera+" "
        findViewById<TextView>(R.id.txtStationAddress).text = b.indirizzo
        //prezzi della card
        val benzinaStr = if (b.prezzoBenzina > 0) "${b.prezzoBenzina}" else "N.D."
        val dieselStr = if (b.prezzoDiesel > 0) "${b.prezzoDiesel}" else "N.D."

        val testoPrezzi = "Self | B: $benzinaStr · D: $dieselStr"
        findViewById<TextView>(R.id.txtPrice).text = testoPrezzi


        val logoRes = b.getLogoResource() //ricavo l'icona
        findViewById<ImageView>(R.id.imgPompa).setImageResource(logoRes)
    }
    private fun setupCardElettrica(ev: ColonninaEV) {
        val card = findViewById<androidx.cardview.widget.CardView>(R.id.stationCard)
        //impostazione del colore della card//impostazione del colore della card
        card.setCardBackgroundColor("#0B101E".toColorInt())

        //testo della card
        findViewById<TextView>(R.id.txtStationName).setTextColor("#00FFC2".toColorInt())
        findViewById<TextView>(R.id.txtStationAddress).setTextColor("#00FFC2".toColorInt())
        findViewById<TextView>(R.id.txtPrice).setTextColor("#00FFC2".toColorInt())
        findViewById<TextView>(R.id.txtStationName).text = ev.titolo+" "
        findViewById<TextView>(R.id.txtStationAddress).text = ev.indirizzo

        val infoElettrica = "${ev.potenzaKW} kW • ${ev.numPunti} prese"
        findViewById<TextView>(R.id.txtPrice).text = infoElettrica
        val logoRes = ev.getLogoResource() //ricavo l'icona
        findViewById<ImageView>(R.id.imgPompa).setImageResource(logoRes)
    }

    //funzione di filtro dei marker in base al booleano passato
    private fun filtraMarker(mostraBenzina: Boolean, mostraEV: Boolean) {
        markersBenzina.forEach { it.isVisible = mostraBenzina }
        markersEV.forEach { it.isVisible = mostraEV }

        val card = findViewById<androidx.cardview.widget.CardView>(R.id.stationCard)
        card.visibility = View.GONE
    }

    private fun apriDettaglio(id: Long, tipo: String) {
        //creo un intent per aprire la nuova activity
            val intent = Intent(this@MapsActivity, DettagliActivity::class.java).apply {
                putExtra("ID_ELEMENTO", id)
                putExtra("TIPO_ELEMENTO", tipo)
            }

            //controllo per verificare i permessi necessari alla posizione
            if (ActivityCompat.checkSelfPermission(this@MapsActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    // passo la posizione solo se disponibile
                    intent.putExtra("USER_LAT", location?.latitude ?: 0.0)
                    intent.putExtra("USER_LON", location?.longitude ?: 0.0)
                    startActivity(intent)
                }

            } else {
                //se i permessi mancano, apriamo comunque l'activity ma senza i dati della posizione
                startActivity(intent)
            }
    }

}