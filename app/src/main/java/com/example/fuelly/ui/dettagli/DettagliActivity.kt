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
import com.example.fuelly.repository.supabase.SupabaseInstance
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.example.fuelly.R
import com.example.fuelly.repository.model.Benzinaio
import com.example.fuelly.repository.model.ColonninaEV
import com.example.fuelly.repository.data.BenzinaiRepository
import com.example.fuelly.repository.data.ColonnineRepository
import com.example.fuelly.repository.model.Salvato
import com.example.fuelly.utils.Utils

class DettagliActivity : AppCompatActivity() {


    private var idRicevuto: Long = -1L
    private var tipoRicevuto: String? = null
    private var distanzaSalvata: Double = 0.0
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dettagli)

        //imposta lo sfondo della barra di navigazione e della barra di stato
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
        windowInsetsController.isAppearanceLightNavigationBars = true

        //inizializza il fusedLocationClient (servizio per ottenere la posizione dell'utente)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        //recupero i dati passati dall'intent della MapsActivity
        idRicevuto = intent.getLongExtra("ID_ELEMENTO", -1L)
        tipoRicevuto = intent.getStringExtra("TIPO_ELEMENTO")

        setupHeader() //inizializza l'header della pagina
        setupViewPager() //inizializza il ViewPager2 e il TabLayout con i relativi fragment
        setupListeners() //inizializza i listener per i bottoni di navigazione, salvataggio e condivisione
        verificaSeSalvato(idRicevuto) //verifica se l'elemento è già salvato dall'utente e aggiorna l'icona di conseguenza

        //se non abbiamo già la posizione dell'utente per qualche motivo
        //la recuperiamo e aggiorniamo la distanza mostrata
        if (intent.getDoubleExtra("USER_LAT", 0.0) == 0.0) {
            recuperaPosizioneEAggiorna()
        }
    }

    //funzione di inizializzazione dell'header della pagina
    private fun setupHeader() {
        //in base al tipo di elemento ricevuto, aggiorno l'UI dell'header
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

    //funzione di inizializzazione del ViewPager2 e del TabLayout
    private fun setupViewPager() {
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)

        //espando i tab su tutta la larghezza (equamente distribuiti)
        tabLayout.tabMode = TabLayout.MODE_FIXED
        tabLayout.tabGravity = TabLayout.GRAVITY_FILL

        //inizializzo l'adapter passando il tipo ricevuto
        val adapter = DettagliPagerAdapter(this, tipoRicevuto)
        viewPager.adapter = adapter

        //sincronizzo TabLayout e ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "PREZZI"
                1 -> "RECENSIONI"
                2 -> "INFO"
                else -> null
            }
        }.attach()
    }

    //funzione di inizializzazione dell'UI dell'header per i benzinai
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

    //funzione di inizializzazione dell'UI dell'header per le colonnine
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

        // Colori tab specifici per EV
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        tabLayout?.setSelectedTabIndicatorColor(color)
        tabLayout?.setTabTextColors(Color.GRAY, color)
        findViewById<Button>(R.id.btnOttieniIndicazioni)?.setBackgroundColor(color)

        calcolaDistanzaDettaglio(ev.lat, ev.lon)
    }

    //funzione di inizializzazione dei listener
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

        findViewById<ImageButton>(R.id.btnCondividi)?.setOnClickListener {
            shareStazione()
        }
    }

    //funzione che recupera la posizione dell'utente e aggiorna la distanza mostrata nell'header
    // (usato come fallback in caso di non averla già recuperata in precedenza)
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
                    //dopo aver ottenuto la posizione, calcoliamo la distanza per mostrarla correttamente
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

    //funzione che calcola la distanza tra la posizione dell'utente e quella
    //della stazione/colonnina e aggiorna il testo nell'header
    private fun calcolaDistanzaDettaglio(latDest: Double, lonDest: Double) {
        val latUser = intent.getDoubleExtra("USER_LAT", 0.0)
        val lonUser = intent.getDoubleExtra("USER_LON", 0.0)

        if (latUser != 0.0 && lonUser != 0.0) {
            distanzaSalvata = Utils.calcolaDistanza(latUser, lonUser, latDest, lonDest)

            //se la distanza è maggiore di 1000 metri,
            //la mostriamo in km con una cifra decimale, altrimenti in metri senza decimali
            if (distanzaSalvata >= 1000) {
                distanzaSalvata /= 1000
                findViewById<TextView>(R.id.txtDistance)?.text = "${String.format("%.1f", distanzaSalvata)} km"
            } else {
                findViewById<TextView>(R.id.txtDistance)?.text = "${String.format("%.0f", distanzaSalvata)} m"

            }

        }
    }

    //funzione che gestisce il salvataggio o la rimozione dai salvati dell'elemento visualizzato
    private fun salvaElemento(idImpianto: Long) {
        //verifico se l'utente è loggato, altrimenti mostro un messaggio ed esco dalla funzione
        val session = SupabaseInstance.client.auth.currentSessionOrNull()
        if (session == null) {
            Toast.makeText(this, getString(R.string.login_required), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                //in base al tipo di elemento, scelgo la tabella corretta su cui operare
                val tabella = if (tipoRicevuto == "BENZINA") "salvati_benzinai" else "salvati_ev"

                //verifico se l'elemento è già salvato dall'utente
                val esistente = SupabaseInstance.client.from(tabella)
                    .select {
                        filter {
                            eq("idUtente", session.user?.id.toString())
                            eq("idImpianto", idImpianto)
                        }
                    }.decodeList<Salvato>()

                //se esiste già, lo rimuovo dai salvati, altrimenti lo aggiungo
                if (esistente.isNotEmpty()) {
                    SupabaseInstance.client.from(tabella).delete {
                        filter {
                            eq("idUtente", session.user?.id.toString())
                            eq("idImpianto", idImpianto)
                        }
                    }
                    runOnUiThread {
                        Toast.makeText(this@DettagliActivity, getString(R.string.details_removed), Toast.LENGTH_SHORT).show()
                        findViewById<ImageButton>(R.id.btnSalva)?.setImageResource(R.drawable.ic_bookmark)
                    }
                } else {
                    val nuovoSalvato = Salvato(
                        idUtente = session.user?.id.toString(),
                        idBenzinaio = idImpianto
                    )
                    SupabaseInstance.client.from(tabella).insert(nuovoSalvato)
                    runOnUiThread {
                        Toast.makeText(this@DettagliActivity, getString(R.string.details_saved), Toast.LENGTH_SHORT).show()
                        findViewById<ImageButton>(R.id.btnSalva)?.setImageResource(R.drawable.ic_bookmark_saved)
                    }
                }
                Utils.benzinaiSalvati(session)
                Utils.colonnineSalvate(session)
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@DettagliActivity, "Errore nella gestione dei salvati", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    //funzione che verifica se l'elemento visualizzato è già salvato
    // dall'utente e aggiorna l'icona di conseguenza
    private fun verificaSeSalvato(idImpianto: Long) {
        //verifico se l'utente è loggato
        val session = SupabaseInstance.client.auth.currentSessionOrNull() ?: return
        lifecycleScope.launch {
            try {
                //in base al tipo di elemento, scelgo la tabella corretta su cui operare
                val tabella = if (tipoRicevuto == "BENZINA") "salvati_benzinai" else "salvati_ev"
                //verifico se l'elemento è già salvato dall'utente
                val esistente = SupabaseInstance.client.from(tabella)
                    .select {
                        filter {
                            eq("idUtente", session.user?.id.toString())
                            eq("idImpianto", idImpianto)
                        }
                    }.decodeList<Salvato>()

                //se esiste già, aggiorno l'icona per indicare che è salvato, altrimenti lascio l'icona di default
                if (esistente.isNotEmpty()) {
                    runOnUiThread {
                        findViewById<ImageButton>(R.id.btnSalva)?.setImageResource(R.drawable.ic_bookmark_saved)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@DettagliActivity, "Errore nella verifica dei salvati", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    //funzione che gestisce l'intent di navigazione verso Google Maps
    private fun avviaNavigatore() {
        var lat: Double?=0.0
        var lon: Double?=0.0
        //recupero le coordinate della stazione/colonnina
        if (tipoRicevuto == "BENZINA") {
            val s = BenzinaiRepository.listaCompleta.find { it.id.toLong() == idRicevuto }
            lat = s?.lat; lon = s?.lon
        } else if (tipoRicevuto == "EV") {
            val c = ColonnineRepository.listaCompleta.find { it.id.toLong() == idRicevuto }
            lat = c?.lat; lon = c?.lon
        }

        //avvio l'intent di navigazione se abbiamo le coordinate
        if (lat != null && lon != null) {
            //creo l'URI per Google Maps con le coordinate della destinazione
            val gmmIntentUri = "https://www.google.com/maps/dir/?api=1&destination=$lat,$lon".toUri()
            val intent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            intent.setPackage("com.google.android.apps.maps") //specifico di aprire con Google Maps
            startActivity(intent)
        }
    }

    //funzione che gestisce l'intent per la condivisione della posizione
    private fun shareStazione() {
        //recupero il tipo della stazione ricevuto e quindi il testo da condividere in base al tipo
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

        //se abbiamo il testo, facciamo partire l'intent di condivisione
        if (testoDaCondividere != null) {
            //crea un Intent di condivisione
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, testoDaCondividere)
            }

            //in base al tipo (stazione/colonnina), apriamo l'intent appropriato
            when (tipoRicevuto) {
                "BENZINA" -> startActivity(Intent.createChooser(shareIntent, "Condividi stazione tramite:"))
                "EV" -> startActivity(Intent.createChooser(shareIntent, "Condividi colonnina tramite:"))
            }

        } else {
            Toast.makeText(this, getString(R.string.details_share_error), Toast.LENGTH_SHORT).show()
        }
    }
}
