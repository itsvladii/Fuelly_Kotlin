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

        // Imposta lo status bar e la navigation bar come trasparenti
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
        windowInsetsController.isAppearanceLightNavigationBars = true

        //recupero della posizione dell'utente
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        //recupero dell'id e del tipo dell'impianto da mostrare dalla pagina mapsFragment
        idRicevuto = intent.getLongExtra("ID_ELEMENTO", -1L)
        tipoRicevuto = intent.getStringExtra("TIPO_ELEMENTO")

        //inizializzazione del viewModel (METODO NEL DETTAGLIVIEWMODEL)
        viewModel.initData(idRicevuto, tipoRicevuto)

        //vari metodi
        setupHeader()
        setupViewPager()
        setupListeners()
        observeViewModel()

        //recupero della posizione dell'utente --> richiamo il metodo
        if (intent.getDoubleExtra("USER_LAT", 0.0) == 0.0) {
            recuperaPosizioneEAggiorna()
        }
    }

    //Configura gli osservatori del ViewModel per aggiornare l'icona del salvataggio (preferiti) e mostrare eventuali messaggi di errore.
    private fun observeViewModel() {
        //osservo la  variabile di tipo mutableLiveData nel ViewModel per evdere se cambia
        viewModel.isSalvato.observe(this) { salvato ->
            //setto l'icona in base al salvataggio
            val icon = if (salvato) R.drawable.ic_bookmark_saved else R.drawable.ic_bookmark

            //setto l'icona nel pulsante
            findViewById<ImageButton>(R.id.btnSalva)?.setImageResource(icon)
        }
        //osservo la variabile di tipo mutableLiveData nel ViewModel per mostrare eventuali errori
        viewModel.error.observe(this) { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    //Identifica la tipologia di elemento (BENZINA o EV) e delega il popolamento dei dati dell'intestazione alla funzione specifica.
    private fun setupHeader() {
        when (tipoRicevuto) {
            "BENZINA" -> {
                //recupero del benzinaio dalla listaCompleta cercando per il suo ID
                val stazione = BenzinaiRepository.listaCompleta.find { it.id.toLong() == idRicevuto }

                //setto i dati nel layout
                stazione?.let { setupUIBenzina(it) }
            }
            "EV" -> {
                //recupero della colonnina dalla listaCompleta cercando per il suo ID
                val colonnina = ColonnineRepository.listaCompleta.find { it.id.toLong() == idRicevuto }

                //setto i dati nel layout
                colonnina?.let { setupUIElettrica(it) }
            }
        }
    }

    //Configura il componente ViewPager2 con il relativo TabLayoutMediator per gestire la navigazione tra le schede Prezzi, Recensioni e Info.
    //SETTAGGIO ADAPTER E GESTIONE DEI SOTTOFRAGMENT
    private fun setupViewPager() {

        //CONTENITORE PER I 3 FRAGMENT
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)

        //TAB CHE MI PERMETTE DI USARE LE LINGUETTE DEL TABLAYOUT
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)

        //imposto il numero di schede
        tabLayout.tabMode = TabLayout.MODE_FIXED
        tabLayout.tabGravity = TabLayout.GRAVITY_FILL

        //USO L'ADAPTER: si aspetta che gli passo un tipo di carburante
        //nel DettagliPagerAdapter Scelgo quanti sottoFragment Mostrare
        val adapter = DettagliPagerAdapter(this, tipoRicevuto)

        //associa l'adapter al ViewPager dell,activity
        viewPager.adapter = adapter

        //uso il TabLayoutMediator per collegare TabLayout e ViewPager
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                //in base alla posizione del sottofragment salvo un qualcosa nel tab
                0 -> "PREZZI"
                1 -> "RECENSIONI"
                2 -> "INFO"
                else -> null
            }
        }.attach()
    }

    // Personalizza la grafica dell'intestazione con i colori e i dati del distributore di carburante, calcolandone la distanza.
    private fun setupUIBenzina(b: Benzinaio) {

        //recupero il CostraintLayout (CONTENITORE) e setto il colore di sfondo
        findViewById<ConstraintLayout>(R.id.stationHeader)
            ?.setBackgroundColor(ContextCompat.getColor(this, R.color.fuelly_green_dark))

        //setto i dati nel layout
        val color = ContextCompat.getColor(this, R.color.fuelly_yellow_fluo)

        //BANDIERA
        findViewById<TextView>(R.id.txtStationName)?.apply {
            setTextColor(color)
            text = b.bandiera + " " //aggiungo la bandiera al titolo
        }

        //CITTA' / PROVINCIA
        findViewById<TextView>(R.id.txtStationCity)?.apply {
            setTextColor(color)
            text = "${b.comune} (${b.provincia})" //aggiungo la provincia al comune
            isSelected = true
        }

        //INDIRIZZO
        findViewById<TextView>(R.id.txtStationAddress)?.apply {
            setTextColor(color)
            text = b.indirizzo
            isSelected = true
        }

        //recupero gli id delle view per la distanza e la pompa e setto i dati
        findViewById<TextView>(R.id.txtDistance)?.setTextColor(color)
        findViewById<ImageView>(R.id.imgPompa)?.setImageResource(b.getLogoResource())

        //calcolo e setto la distanza tra la posizione dell'utente e la stazione
        calcolaDistanzaDettaglio(b.lat, b.lon)
    }

    //Personalizza la grafica dell'intestazione con i colori e i dati della colonnina elettrica, modificando anche lo stile dei tab e dei pulsanti.
    private fun setupUIElettrica(ev: ColonninaEV) {

        //recupero il CostraintLayout (CONTENITORE) e setto il colore di sfondo
        findViewById<ConstraintLayout>(R.id.stationHeader)
            ?.setBackgroundColor(ContextCompat.getColor(this, R.color.ev_dark_blue))

        //assegno il colore
        val color = ContextCompat.getColor(this, R.color.ev_cyan)

        //TITOLO
        findViewById<TextView>(R.id.txtStationName)?.apply {
            setTextColor(color)
            text = ev.titolo + " "
        }

        //CITTA'
        findViewById<TextView>(R.id.txtStationCity)?.apply {
            setTextColor(color)
            text = ev.comune
            isSelected = true
        }

        //INDIRIZZO
        findViewById<TextView>(R.id.txtStationAddress)?.apply {
            setTextColor(color)
            text = ev.indirizzo
            isSelected = true
        }
        //pompa e distanza
        findViewById<ImageView>(R.id.imgPompa)?.setImageResource(ev.getLogoResource())
        findViewById<TextView>(R.id.txtDistance)?.setTextColor(color)

        //tablayout, serve per cambiare lo stile dei tab
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        tabLayout?.setSelectedTabIndicatorColor(color)
        tabLayout?.setTabTextColors(Color.GRAY, color)

        //bottone ottieni indicazioni
        findViewById<Button>(R.id.btnOttieniIndicazioni)?.setBackgroundColor(color)

        //calcolo e setto la distanza tra la posizione dell'utente e la colonnina
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

        //controllo se ci sono i permessi
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            //recupero la posizione dell'utente
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->

                //se ho la posizione
                if (location != null) {
                    //passo dei parametri all'intent
                    intent.putExtra("USER_LAT", location.latitude)
                    intent.putExtra("USER_LON", location.longitude)

                    when (tipoRicevuto) {
                        "BENZINA" -> {
                            //recupero la stazione dal repository locale
                            val b = BenzinaiRepository.listaCompleta.find { it.id.toLong() == idRicevuto }
                            //aggiorno la distanza
                            b?.let { calcolaDistanzaDettaglio(it.lat, it.lon) }
                        }
                        "EV" -> {
                            //recupero la colonnina dal repository locale
                            val ev = ColonnineRepository.listaCompleta.find { it.id.toLong() == idRicevuto }
                            //aggiorno la distanza
                            ev?.let { calcolaDistanzaDettaglio(it.lat, it.lon) }
                        }
                    }
                }
            }
        }
    }

    //Calcola la distanza tra la posizione dell'utente e la stazione, formattando il testo in metri o chilometri nella UI.
    private fun calcolaDistanzaDettaglio(latDest: Double, lonDest: Double) {

        //recupero variabili dall'intent passato da MapsFragment
        val latUser = intent.getDoubleExtra("USER_LAT", 0.0)
        val lonUser = intent.getDoubleExtra("USER_LON", 0.0)

        //se ho dei valori positivi
        if (latUser != 0.0 && lonUser != 0.0) {

            //calcolo la distanza
            distanzaSalvata = Utils.calcolaDistanza(latUser, lonUser, latDest, lonDest)
            //se la distanza è maggiore di 1000 metri la "formatto in KM"
            if (distanzaSalvata >= 1000) {
                distanzaSalvata /= 1000
                //setto il testo nel layout
                findViewById<TextView>(R.id.txtDistance)?.text = "${String.format("%.1f", distanzaSalvata)} km"
            } else {
                //visualizzo in METRI
                findViewById<TextView>(R.id.txtDistance)?.text = "${String.format("%.0f", distanzaSalvata)} m"
            }
        }
    }

    //Recupera le coordinate geografiche della struttura e lancia un Intent per avviare la navigazione stradale su Google Maps.
    private fun avviaNavigatore() {

        var lat: Double? = 0.0
        var lon: Double? = 0.0


        if (tipoRicevuto == "BENZINA") {
            //recupero la stazione dal repository locale
            val s = BenzinaiRepository.listaCompleta.find { it.id.toLong() == idRicevuto }

            //aggiorno le variabili
            lat = s?.lat; lon = s?.lon

        } else if (tipoRicevuto == "EV") {
            //recupero la colonnina dal repository locale
            val c = ColonnineRepository.listaCompleta.find { it.id.toLong() == idRicevuto }

            //aggiorno le variabili
            lat = c?.lat; lon = c?.lon
        }

        //se ho dei valori positivi
        if (lat != null && lon != null) {

            //avvio il navigatore
            val gmmIntentUri = "https://www.google.com/maps/dir/?api=1&destination=$lat,$lon".toUri()

            //uso l'intent di google maps
            val intent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            intent.setPackage("com.google.android.apps.maps")
            startActivity(intent)
        }
    }

    //Genera il testo riassuntivo specifico dell'impianto e apre il selettore di sistema per condividerlo tramite app esterne.
    private fun shareStazione() {

        val testoDaCondividere = when (tipoRicevuto) {

            "BENZINA" -> {
                //recupero la stazione dal repository locale
                val stazione = BenzinaiRepository.listaCompleta.find { it.id.toLong() == idRicevuto }
                //setto il testo da condividere
                stazione?.getShareText()
            }
            "EV" -> {
                //recupero la colonnina dal repository locale
                val colonnina = ColonnineRepository.listaVicini.find { it.id.toLong() == idRicevuto }
                //setto il testo da condividere
                colonnina?.getShareText()
            }
            else -> null
        }
        //se ho qualcosa da condividere
        if (testoDaCondividere != null) {

            //creo un intent di condivisione aggiungo quel parametro testodaCondividere
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, testoDaCondividere)
            }
            when (tipoRicevuto) {
                //scelgo poi dal telefono come condividere quell'intent
                "BENZINA" -> startActivity(Intent.createChooser(shareIntent, "Condividi stazione tramite:"))
                "EV" -> startActivity(Intent.createChooser(shareIntent, "Condividi colonnina tramite:"))
            }
        } else {
            Toast.makeText(this, getString(R.string.details_share_error), Toast.LENGTH_SHORT).show()
        }
    }
}
