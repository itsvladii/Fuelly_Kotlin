package com.example.fuelly.ui.dettagli

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.viewpager2.widget.ViewPager2
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.lifecycle.lifecycleScope
import androidx.activity.viewModels
import com.example.fuelly.R
import com.example.fuelly.repository.model.Benzinaio
import com.example.fuelly.repository.model.ColonninaEV
import com.example.fuelly.repository.data.BenzinaiRepository
import com.example.fuelly.repository.data.ColonnineRepository
import com.example.fuelly.utils.Utils

class DettagliActivity : AppCompatActivity() {

    private var idRicevuto: Long = -1L
    private var tipoRicevuto: String? = null
    private var distanzaSalvata: Double = 0.0
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val viewModel: DettagliViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dettagli)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
        windowInsetsController.isAppearanceLightNavigationBars = true

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        idRicevuto = intent.getLongExtra("ID_ELEMENTO", -1L)
        tipoRicevuto = intent.getStringExtra("TIPO_ELEMENTO")

        viewModel.initData(idRicevuto, tipoRicevuto)

        setupHeader()
        setupViewPager()
        setupListeners()
        observeViewModel()

        if (intent.getDoubleExtra("USER_LAT", 0.0) == 0.0) {
            recuperaPosizioneEAggiorna()
        }
    }
    //Configura gli osservatori del ViewModel per aggiornare l'icona del salvataggio (preferiti) e mostrare eventuali messaggi di errore.
    private fun observeViewModel() {
        viewModel.isSalvato.observe(this) { salvato ->
            val icon = if (salvato) R.drawable.ic_bookmark_saved else R.drawable.ic_bookmark
            findViewById<ImageButton>(R.id.btnSalva)?.setImageResource(icon)
        }

        viewModel.error.observe(this) { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    //Identifica la tipologia di elemento (BENZINA o EV) e delega il popolamento dei dati dell'intestazione alla funzione specifica.
    private fun setupHeader() {
        when (tipoRicevuto) {
            "BENZINA" -> {
                val stazione = BenzinaiRepository.listaCompleta.find { it.id.toLong() == idRicevuto }
                stazione?.let { setupUIBenzina(it) }
            }
            "EV" -> {
                val colonnina = ColonnineRepository.listaCompleta.find { it.id.toLong() == idRicevuto }
                colonnina?.let { setupUIElettrica(it) }
            }
        }
    }

    //Configura il componente ViewPager2 con il relativo TabLayoutMediator per gestire la navigazione tra le schede Prezzi, Recensioni e Info.
    private fun setupViewPager() {
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)

        tabLayout.tabMode = TabLayout.MODE_FIXED
        tabLayout.tabGravity = TabLayout.GRAVITY_FILL

        val adapter = DettagliPagerAdapter(this, tipoRicevuto)
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

    // Personalizza la grafica dell'intestazione con i colori e i dati del distributore di carburante, calcolandone la distanza.
    private fun setupUIBenzina(b: Benzinaio) {
        findViewById<ConstraintLayout>(R.id.stationHeader)
            ?.setBackgroundColor(ContextCompat.getColor(this, R.color.fuelly_green_dark))

        val color = ContextCompat.getColor(this, R.color.fuelly_yellow_fluo)
        findViewById<TextView>(R.id.txtStationName)?.apply {
            setTextColor(color)
            text = b.bandiera + " "
        }
        findViewById<TextView>(R.id.txtStationCity)?.apply {
            setTextColor(color)
            text = "${b.comune} (${b.provincia})"
            isSelected = true
        }
        findViewById<TextView>(R.id.txtStationAddress)?.apply {
            setTextColor(color)
            text = b.indirizzo
            isSelected = true
        }
        findViewById<TextView>(R.id.txtDistance)?.setTextColor(color)
        findViewById<ImageView>(R.id.imgPompa)?.setImageResource(b.getLogoResource())

        calcolaDistanzaDettaglio(b.lat, b.lon)
    }

    //Personalizza la grafica dell'intestazione con i colori e i dati della colonnina elettrica, modificando anche lo stile dei tab e dei pulsanti.
    private fun setupUIElettrica(ev: ColonninaEV) {
        findViewById<ConstraintLayout>(R.id.stationHeader)
            ?.setBackgroundColor(ContextCompat.getColor(this, R.color.ev_dark_blue))

        val color = ContextCompat.getColor(this, R.color.ev_cyan)
        findViewById<TextView>(R.id.txtStationName)?.apply {
            setTextColor(color)
            text = ev.titolo + " "
        }
        findViewById<TextView>(R.id.txtStationCity)?.apply {
            setTextColor(color)
            text = ev.comune
            isSelected = true
        }
        findViewById<TextView>(R.id.txtStationAddress)?.apply {
            setTextColor(color)
            text = ev.indirizzo
            isSelected = true
        }
        findViewById<ImageView>(R.id.imgPompa)?.setImageResource(ev.getLogoResource())
        findViewById<TextView>(R.id.txtDistance)?.setTextColor(color)

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        tabLayout?.setSelectedTabIndicatorColor(color)
        tabLayout?.setTabTextColors(Color.GRAY, color)
        findViewById<Button>(R.id.btnOttieniIndicazioni)?.setBackgroundColor(color)

        calcolaDistanzaDettaglio(ev.lat, ev.lon)
    }

    //Associa i comportamenti ai click sui pulsanti della UI (navigazione, ritorno alla schermata precedente, salvataggio e condivisione).
    private fun setupListeners() {
        findViewById<Button>(R.id.btnOttieniIndicazioni)?.setOnClickListener {
            avviaNavigatore()
        }
        findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        findViewById<ImageButton>(R.id.btnSalva)?.setOnClickListener {
            viewModel.toggleSalvataggio()
        }
        findViewById<ImageButton>(R.id.btnCondividi)?.setOnClickListener {
            shareStazione()
        }
    }

    //Richiede l'ultima posizione GPS nota del dispositivo per ricalcolare e aggiornare la distanza in tempo reale.
    private fun recuperaPosizioneEAggiorna() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    intent.putExtra("USER_LAT", location.latitude)
                    intent.putExtra("USER_LON", location.longitude)
                    when (tipoRicevuto) {
                        "BENZINA" -> {
                            val b = BenzinaiRepository.listaCompleta.find { it.id.toLong() == idRicevuto }
                            b?.let { calcolaDistanzaDettaglio(it.lat, it.lon) }
                        }
                        "EV" -> {
                            val ev = ColonnineRepository.listaCompleta.find { it.id.toLong() == idRicevuto }
                            ev?.let { calcolaDistanzaDettaglio(it.lat, it.lon) }
                        }
                    }
                }
            }
        }
    }

    //Calcola la distanza tra la posizione dell'utente e la stazione, formattando il testo in metri o chilometri nella UI.
    private fun calcolaDistanzaDettaglio(latDest: Double, lonDest: Double) {
        val latUser = intent.getDoubleExtra("USER_LAT", 0.0)
        val lonUser = intent.getDoubleExtra("USER_LON", 0.0)

        if (latUser != 0.0 && lonUser != 0.0) {
            distanzaSalvata = Utils.calcolaDistanza(latUser, lonUser, latDest, lonDest)
            if (distanzaSalvata >= 1000) {
                distanzaSalvata /= 1000
                findViewById<TextView>(R.id.txtDistance)?.text = "${String.format("%.1f", distanzaSalvata)} km"
            } else {
                findViewById<TextView>(R.id.txtDistance)?.text = "${String.format("%.0f", distanzaSalvata)} m"
            }
        }
    }

    //Recupera le coordinate geografiche della struttura e lancia un Intent per avviare la navigazione stradale su Google Maps.
    private fun avviaNavigatore() {
        var lat: Double? = 0.0
        var lon: Double? = 0.0
        if (tipoRicevuto == "BENZINA") {
            val s = BenzinaiRepository.listaCompleta.find { it.id.toLong() == idRicevuto }
            lat = s?.lat; lon = s?.lon
        } else if (tipoRicevuto == "EV") {
            val c = ColonnineRepository.listaCompleta.find { it.id.toLong() == idRicevuto }
            lat = c?.lat; lon = c?.lon
        }

        if (lat != null && lon != null) {
            val gmmIntentUri = "https://www.google.com/maps/dir/?api=1&destination=$lat,$lon".toUri()
            val intent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            intent.setPackage("com.google.android.apps.maps")
            startActivity(intent)
        }
    }

    //Genera il testo riassuntivo specifico dell'impianto e apre il selettore di sistema per condividerlo tramite app esterne.
    private fun shareStazione() {
        val testoDaCondividere = when (tipoRicevuto) {
            "BENZINA" -> {
                val stazione = BenzinaiRepository.listaCompleta.find { it.id.toLong() == idRicevuto }
                stazione?.getShareText()
            }
            "EV" -> {
                val colonnina = ColonnineRepository.listaVicini.find { it.id.toLong() == idRicevuto }
                colonnina?.getShareText()
            }
            else -> null
        }

        if (testoDaCondividere != null) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, testoDaCondividere)
            }
            when (tipoRicevuto) {
                "BENZINA" -> startActivity(Intent.createChooser(shareIntent, "Condividi stazione tramite:"))
                "EV" -> startActivity(Intent.createChooser(shareIntent, "Condividi colonnina tramite:"))
            }
        } else {
            Toast.makeText(this, getString(R.string.details_share_error), Toast.LENGTH_SHORT).show()
        }
    }
}
