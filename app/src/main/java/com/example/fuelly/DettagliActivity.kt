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

    private var idRicevuto: Long = -1L
    private var tipoRicevuto: String? = null
    private var distanzaSalvata: Double = 0.0
    private lateinit var fusedLocationClient: FusedLocationProviderClient

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

        setupHeader()
        setupViewPager()
        setupListeners()
        verificaSeSalvato(idRicevuto)

        if (intent.getDoubleExtra("USER_LAT", 0.0) == 0.0) {
            recuperaPosizioneEAggiorna()
        }
    }

    private fun setupHeader() {
        when (tipoRicevuto) {
            "BENZINA" -> {
                val stazione = Benzinaio.listaCompleta.find { it.id.toLong() == idRicevuto }
                stazione?.let { setupUIBenzina(it) }
            }
            "EV" -> {
                val colonnina = ColonninaEV.listaCompleta.find { it.id.toLong() == idRicevuto }
                colonnina?.let { setupUIElettrica(it) }
            }
        }
    }

    private fun setupViewPager() {
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)

        // Espande i tab su tutta la larghezza (equamente distribuiti)
        tabLayout.tabMode = TabLayout.MODE_FIXED
        tabLayout.tabGravity = TabLayout.GRAVITY_FILL

        // Inizializza l'adapter passando il tipo ricevuto
        val adapter = DettagliPagerAdapter(this, tipoRicevuto)
        viewPager.adapter = adapter

        // Sincronizza TabLayout e ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "PREZZI"
                1 -> "RECENSIONI"
                2 -> "INFO"
                else -> null
            }
        }.attach()
    }

    private fun setupUIBenzina(b: Benzinaio) {
        findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.stationHeader)
            ?.setBackgroundColor("#0B3D2E".toColorInt())

        val color = "#DFFF00".toColorInt()
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

    private fun setupUIElettrica(ev: ColonninaEV) {
        findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.stationHeader)
            ?.setBackgroundColor("#0B101E".toColorInt())

        val color = "#00FFC2".toColorInt()
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

    private fun recuperaPosizioneEAggiorna() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    intent.putExtra("USER_LAT", location.latitude)
                    intent.putExtra("USER_LON", location.longitude)
                    when (tipoRicevuto) {
                        "BENZINA" -> {
                            val b = Benzinaio.listaCompleta.find { it.id.toLong() == idRicevuto }
                            b?.let { calcolaDistanzaDettaglio(it.lat, it.lon) }
                        }
                        "EV" -> {
                            val ev = ColonninaEV.listaCompleta.find { it.id.toLong() == idRicevuto }
                            ev?.let { calcolaDistanzaDettaglio(it.lat, it.lon) }
                        }
                    }
                }
            }
        }
    }

    private fun calcolaDistanzaDettaglio(latDest: Double, lonDest: Double) {
        val latUser = intent.getDoubleExtra("USER_LAT", 0.0)
        val lonUser = intent.getDoubleExtra("USER_LON", 0.0)

        if (latUser != 0.0 && lonUser != 0.0) {
            distanzaSalvata = Utils.calcolaDistanza(latUser, lonUser, latDest, lonDest)

            if (distanzaSalvata >= 1000) {
                distanzaSalvata = distanzaSalvata/1000
                findViewById<TextView>(R.id.txtDistance)?.text = "${String.format("%.1f", distanzaSalvata)} km"
            }
            else{
                findViewById<TextView>(R.id.txtDistance)?.text = "${String.format("%.0f", distanzaSalvata)} m"

            }

        }
    }

    private fun salvaElemento(idImpianto: Long) {
        val session = SupabaseInstance.client.auth.currentSessionOrNull()
        if (session == null) {
            Toast.makeText(this, "Devi essere loggato per gestire i tuoi elementi salvati", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val tabella = if (tipoRicevuto == "BENZINA") "salvati_benzinai" else "salvati_ev"
                
                val esistente = SupabaseInstance.client.from(tabella)
                    .select {
                        filter {
                            eq("idUtente", session.user?.id.toString())
                            eq("idImpianto", idImpianto)
                        }
                    }.decodeList<Salvato>()

                if (esistente.isNotEmpty()) {
                    SupabaseInstance.client.from(tabella).delete {
                        filter {
                            eq("idUtente", session.user?.id.toString())
                            eq("idImpianto", idImpianto)
                        }
                    }
                    runOnUiThread {
                        Toast.makeText(this@DettagliActivity, "Rimosso dai salvati", Toast.LENGTH_SHORT).show()
                        findViewById<ImageButton>(R.id.btnSalva)?.setImageResource(R.drawable.ic_bookmark)
                    }
                } else {
                    val nuovoSalvato = Salvato(
                        idUtente = session.user?.id.toString(),
                        idBenzinaio = idImpianto
                    )
                    SupabaseInstance.client.from(tabella).insert(nuovoSalvato)
                    runOnUiThread {
                        Toast.makeText(this@DettagliActivity, "Salvato!", Toast.LENGTH_SHORT).show()
                        findViewById<ImageButton>(R.id.btnSalva)?.setImageResource(R.drawable.ic_bookmark_saved)
                    }
                }
                Utils.BenzinaiSalvati(session)
                Utils.ColonnineSalvate(session)
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@DettagliActivity, "Errore nella gestione dei salvati", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun verificaSeSalvato(idImpianto: Long) {
        val session = SupabaseInstance.client.auth.currentSessionOrNull() ?: return
        lifecycleScope.launch {
            try {
                val tabella = if (tipoRicevuto == "BENZINA") "salvati_benzinai" else "salvati_ev"
                val esistente = SupabaseInstance.client.from(tabella)
                    .select {
                        filter {
                            eq("idUtente", session.user?.id.toString())
                            eq("idImpianto", idImpianto)
                        }
                    }.decodeList<Salvato>()

                if (esistente.isNotEmpty()) {
                    runOnUiThread {
                        findViewById<ImageButton>(R.id.btnSalva)?.setImageResource(R.drawable.ic_bookmark_saved)
                    }
                }
            } catch (e: Exception) {
                // Silenzioso
            }
        }
    }

    private fun avviaNavigatore() {
        val lat: Double?
        val lon: Double?
        if (tipoRicevuto == "BENZINA") {
            val s = Benzinaio.listaCompleta.find { it.id.toLong() == idRicevuto }
            lat = s?.lat; lon = s?.lon
        } else {
            val c = ColonninaEV.listaCompleta.find { it.id.toLong() == idRicevuto }
            lat = c?.lat; lon = c?.lon
        }

        if (lat != null && lon != null) {
            val gmmIntentUri = "https://www.google.com/maps/dir/?api=1&destination=$lat,$lon".toUri()
            val intent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            intent.setPackage("com.google.android.apps.maps")
            startActivity(intent)
        }
    }

    //funzione che gestisce l'intent per la condivisione del benzinaio/colonnina
    private fun shareStazione() {
        //recupero il tipo della stazione ricevuto
        val testoDaCondividere = when (tipoRicevuto) {
            "BENZINA" -> {
                val stazione = Benzinaio.listaCompleta.find { it.id.toLong() == idRicevuto }
                stazione?.getShareText()
            }
            "EV" -> {
                val colonnina = ColonninaEV.listaVicini.find { it.id.toLong() == idRicevuto }
                colonnina?.getShareText()
            }
            else -> null
        }

        //se abbiamo il testo, facciamo partire l'intent di condivisione
        if (testoDaCondividere != null) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, testoDaCondividere)
            }
            startActivity(Intent.createChooser(shareIntent, "Condividi stazione tramite:"))
        } else {
            Toast.makeText(this, "Errore nel recupero dati per la condivisione", Toast.LENGTH_SHORT).show()
        }
    }
}
