package com.example.fuelly

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.graphics.toColorInt
import com.example.fuelly.classes.*
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.fuelly.supabase.SupabaseInstance
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.example.fuelly.utils.Utils

class DettagliActivity : AppCompatActivity() {

    private var idRicevuto: Long = -1L //id dell'elemento ricevuto dall'intent (benzinaio o colonnina)
    private var tipoRicevuto: String? = null //tipo dell'elemento ricevuto dall'intent ("BENZINA" o "EV")
    private var distanzaSalvata: Double = 0.0
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        //setup edge-to-edge e colori di status e navigation bar
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dettagli)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
        windowInsetsController.isAppearanceLightNavigationBars = true

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        //recupero dati dall'intent
        idRicevuto = intent.getLongExtra("ID_ELEMENTO", -1L)
        tipoRicevuto = intent.getStringExtra("TIPO_ELEMENTO")

        setupHeader()
        setupViewPager()
        setupListeners()
        verificaSeSalvato(idRicevuto)

        //se non abbiamo ricevuto la posizione dall'intent, proviamo a recuperarla manualmente qui
        if (intent.getDoubleExtra("USER_LAT", 0.0) == 0.0) {
            recuperaPosizioneEAggiorna()
        }
    }

    //funzione per il setup dell'header in base al tipo di elemento ricevuto (benzinaio o colonnina)
    private fun setupHeader() {
        when (tipoRicevuto) {
            "BENZINA" -> {
                val stazione = Benzinaio.listaCompleta.find { it.id.toLong() == idRicevuto }
                stazione?.let { setupUIBenzina(it) }
            }
            "EV" -> {
                val colonnina = ColonninaEV.listaVicini.find { it.id.toLong() == idRicevuto }
                colonnina?.let { setupUIElettrica(it) }
            }
        }
    }

    //funzione per il setup del viewpager e delle tab in base al tipo di elemento ricevuto
    private fun setupViewPager() {
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)

        val adapter = DettagliPagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "PREZZI"
                1 -> "RECENSIONI"
                2 -> "INFO"
                else -> null
            }
        }.attach()
    }

    //funzione per il setup dell'interfaccia in caso di benzinaio
    private fun setupUIBenzina(b: Benzinaio) {
        //cambio colore header
        findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.stationHeader)
            ?.setBackgroundColor("#0B3D2E".toColorInt())

        val color = "#DFFF00".toColorInt()
        findViewById<TextView>(R.id.txtStationName)?.apply {
            setTextColor(color)
            text = "${b.bandiera} "
        }
        findViewById<TextView>(R.id.txtStationAddress)?.apply {
            setTextColor(color)
            text = b.indirizzo
            isSelected = true
        }
        //cambio colore dei vari elementi del fragment
        findViewById<TextView>(R.id.txtDistance)?.setTextColor(color)
        findViewById<ImageView>(R.id.imgPompa)?.setImageResource(b.getLogoResource())

        //richiamo la funzione di calcolo della distanza dal benzinaio
        calcolaDistanzaDettaglio(b.lat, b.lon)
    }

    //funzione per il setup dell'interfaccia in caso di colonnina elettrica
    private fun setupUIElettrica(ev: ColonninaEV) {
        //cambio colore header
        findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.stationHeader)
            ?.setBackgroundColor("#0B101E".toColorInt())

        val color = "#00FFC2".toColorInt()
        findViewById<TextView>(R.id.txtStationName)?.apply {
            setTextColor(color)
            text = "${ev.titolo} "
        }
        findViewById<TextView>(R.id.txtStationAddress)?.apply {
            setTextColor(color)
            text = ev.indirizzo
        }
        //cambio colore dei vari elementi del fragment
        findViewById<ImageView>(R.id.imgPompa)?.setImageResource(ev.getLogoResource())
        findViewById<TextView>(R.id.txtDistance)?.setTextColor(color)
        findViewById<TabLayout>(R.id.tabLayout)?.setSelectedTabIndicatorColor(color)
        findViewById<TabLayout>(R.id.tabLayout)?.setTabTextColors(Color.GRAY, color)
        findViewById<Button>(R.id.btnOttieniIndicazioni)?.setBackgroundColor(color)

        //richiamo la funzione di calcolo della distanza dalla colonnina
        calcolaDistanzaDettaglio(ev.lat, ev.lon)
    }

    //funzione che imposta i vari listener dei bottoni presenti nell'activity
    private fun setupListeners() {
        findViewById<Button>(R.id.btnOttieniIndicazioni)?.setOnClickListener {
            avviaNavigatore()
        }

        findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<ImageButton>(R.id.btnSalva)?.setOnClickListener {
            salvaElemento(idRicevuto)
        }
    }

    //funzione per recuperare la posizione dell'utente e aggiornare la distanza nell'header
    //(fallback in caso di mancata ricezione delle cordinate dall'intent)
    private fun recuperaPosizioneEAggiorna() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    intent.putExtra("USER_LAT", location.latitude)
                    intent.putExtra("USER_LON", location.longitude)
                    // Ricalcoliamo la distanza per l'header
                    when (tipoRicevuto) {
                        "BENZINA" -> {
                            val b = Benzinaio.listaCompleta.find { it.id.toLong() == idRicevuto }
                            b?.let { calcolaDistanzaDettaglio(it.lat, it.lon) }
                        }
                        "EV" -> {
                            val ev = ColonninaEV.listaVicini.find { it.id.toLong() == idRicevuto }
                            ev?.let { calcolaDistanzaDettaglio(it.lat, it.lon) }
                        }
                    }
                }
            }
        }
    }

    //funzione per calcolare la distanza tra l'utente e il punto di interesse (benzinaio o colonnina)
    // e aggiornare l'header con la distanza in km
    private fun calcolaDistanzaDettaglio(latDest: Double, lonDest: Double) {
        val latUser = intent.getDoubleExtra("USER_LAT", 0.0)
        val lonUser = intent.getDoubleExtra("USER_LON", 0.0)

        if (latUser != 0.0 && lonUser != 0.0) {
            val start = android.location.Location("A").apply { latitude = latUser; longitude = lonUser }
            val end = android.location.Location("B").apply { latitude = latDest; longitude = lonDest }
            distanzaSalvata = (start.distanceTo(end) / 1000).toDouble()

            findViewById<TextView>(R.id.txtDistance)?.text = "${String.format("%.1f", distanzaSalvata)} km"
        }
    }

    //funzione per salvare o rimuovere un elemento (benzinaio o colonnina) dai salvati dell'utente
    private fun salvaElemento(idBenzinaio: Long) {
        //verifico se l'utente è loggato, altrimenti mostro un messaggio di errore
        val session = SupabaseInstance.client.auth.currentSessionOrNull()
        if (session == null) {
            Toast.makeText(this, "Devi essere loggato per gestire i tuoi elementi salvati", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                //verifico se l'elemento è già presente nei salvati dell'utente
                val esistente = SupabaseInstance.client.from("salvati")
                    .select {
                        filter {
                            eq("idUtente", session.user?.id.toString())
                            eq("idImpianto", idBenzinaio)
                        }
                    }.decodeList<Salvato>()

                //se è già presente, lo rimuovo dai salvati
                if (esistente.isNotEmpty()) {
                    SupabaseInstance.client.from("salvati").delete {
                        filter {
                            eq("idUtente", session.user?.id.toString())
                            eq("idImpianto", idBenzinaio)
                        }
                    }
                    runOnUiThread {
                        Toast.makeText(this@DettagliActivity, "Benzinaio rimosso dai salvati", Toast.LENGTH_SHORT).show()
                        findViewById<ImageButton>(R.id.btnSalva)?.setImageResource(R.drawable.bookmark_svg)
                    }
                } else {
                    //altrimenti, se non è presente, lo aggiungo ai salvati dell'utente
                    val nuovoSalvato = Salvato(idUtente = session.user?.id.toString(), idBenzinaio = idBenzinaio)
                    SupabaseInstance.client.from("salvati").insert(nuovoSalvato)
                    runOnUiThread {
                        Toast.makeText(this@DettagliActivity, "Benzinaio salvato!", Toast.LENGTH_SHORT).show()
                        findViewById<ImageButton>(R.id.btnSalva)?.setImageResource(R.drawable.bookmark_salvato)
                    }
                }
                Utils.caricaSalvati(session)

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@DettagliActivity, "Errore nella gestione dei benzinai salvati", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    //funzione per verificare se l'elemento visualizzato è già presente nei salvati dell'utente
    // e aggiornare l'icona di conseguenza
    private fun verificaSeSalvato(idBenzinaio: Long) {
        val user = SupabaseInstance.client.auth.currentUserOrNull() ?: return
        lifecycleScope.launch {
            try {
                val esistente = SupabaseInstance.client.from("salvati")
                    .select {
                        filter {
                            eq("idUtente", user.id)
                            eq("idImpianto", idBenzinaio)
                        }
                    }.decodeList<Salvato>()

                if (esistente.isNotEmpty()) {
                    runOnUiThread {
                        findViewById<ImageButton>(R.id.btnSalva)?.setImageResource(R.drawable.bookmark_salvato)
                    }
                }
            } catch (e: Exception) { }
        }
    }

    //funzione per gestire l'intent di passaggio a Maps con le coordinate
    // del punto di interesse (benzinaio o colonnina) per ottenere indicazioni stradali
    private fun avviaNavigatore() {
        val lat: Double?
        val lon: Double?
        //ricavo le coordinate del punto di interesse in base al tipo di elemento (benzinaio o colonnina) e all'id ricevuto
        if (tipoRicevuto == "BENZINA") {
            val s = Benzinaio.listaCompleta.find { it.id.toLong() == idRicevuto }
            lat = s?.lat; lon = s?.lon
        } else {
            val c = ColonninaEV.listaVicini.find { it.id.toLong() == idRicevuto }
            lat = c?.lat; lon = c?.lon
        }

        //se abbiamo recuperato correttamente le coordinate, avviamo l'intent per Google Maps con la modalità di navigazione
        if (lat != null && lon != null) {
            val intent = Intent(Intent.ACTION_VIEW, "google.navigation:q=$lat,$lon".toUri())
            startActivity(intent)
        }
    }
}
