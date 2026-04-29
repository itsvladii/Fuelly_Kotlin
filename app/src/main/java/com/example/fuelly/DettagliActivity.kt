package com.example.fuelly

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.example.fuelly.classes.*
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.fuelly.supabase.SupabaseInstance
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class DettagliActivity : AppCompatActivity() {

    private var idRicevuto: Long = -1L
    private var tipoRicevuto: String? = null
    private var distanzaSalvata: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_dettagli)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
        windowInsetsController.isAppearanceLightNavigationBars = false

        idRicevuto = intent.getLongExtra("ID_ELEMENTO", -1L)
        tipoRicevuto = intent.getStringExtra("TIPO_ELEMENTO")

        setupHeader()
        setupViewPager()
        setupListeners()
        verificaSeSalvato(idRicevuto)
    }

    private fun setupHeader() {
        when (tipoRicevuto) {
            "BENZINA" -> {
                val stazione = Benzinaio.listaVicini.find { it.id.toLong() == idRicevuto }
                stazione?.let { setupUIBenzina(it) }
            }
            "EV" -> {
                val colonnina = ColonninaEV.listaVicini.find { it.id.toLong() == idRicevuto }
                colonnina?.let { setupUIElettrica(it) }
            }
        }
    }

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

    private fun setupUIBenzina(b: Benzinaio) {
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
        findViewById<TextView>(R.id.txtDistance)?.setTextColor(color)
        findViewById<ImageView>(R.id.imgPompa)?.setImageResource(b.getLogoResource())

        calcolaDistanzaDettaglio(b.lat, b.lon)
    }

    private fun setupUIElettrica(ev: ColonninaEV) {
        findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.stationHeader)
            ?.setBackgroundColor("#0B101E".toColorInt())

        val color = Color.parseColor("#00FFC2")
        findViewById<TextView>(R.id.txtStationName)?.apply {
            setTextColor(color)
            text = "${ev.titolo} "
        }
        findViewById<TextView>(R.id.txtStationAddress)?.apply {
            setTextColor(color)
            text = ev.indirizzo
        }
        findViewById<ImageView>(R.id.imgPompa)?.setImageResource(ev.getLogoResource())
        findViewById<TextView>(R.id.txtDistance)?.setTextColor(color)

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
    }

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

    private fun salvaElemento(idBenzinaio: Long) {
        val user = SupabaseInstance.client.auth.currentUserOrNull()
        if (user == null) {
            Toast.makeText(this, "Devi essere loggato per gestire i tuoi elementi salvati", Toast.LENGTH_SHORT).show()
            return
        }

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
                    SupabaseInstance.client.from("salvati").delete {
                        filter {
                            eq("idUtente", user.id)
                            eq("idImpianto", idBenzinaio)
                        }
                    }
                    runOnUiThread {
                        Toast.makeText(this@DettagliActivity, "Benzinaio rimosso dai salvati", Toast.LENGTH_SHORT).show()
                        findViewById<ImageButton>(R.id.btnSalva)?.setImageResource(R.drawable.bookmark_svg)
                    }
                } else {
                    val nuovoSalvato = Salvato(idUtente = user.id, idBenzinaio = idBenzinaio)
                    SupabaseInstance.client.from("salvati").insert(nuovoSalvato)
                    runOnUiThread {
                        Toast.makeText(this@DettagliActivity, "Benzinaio salvato!", Toast.LENGTH_SHORT).show()
                        findViewById<ImageButton>(R.id.btnSalva)?.setImageResource(R.drawable.bookmark_salvato)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@DettagliActivity, "Errore nella gestione dei benzinai salvati", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

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

    private fun avviaNavigatore() {
        val lat: Double?
        val lon: Double?
        if (tipoRicevuto == "BENZINA") {
            val s = Benzinaio.listaVicini.find { it.id.toLong() == idRicevuto }
            lat = s?.lat; lon = s?.lon
        } else {
            val c = ColonninaEV.listaVicini.find { it.id.toLong() == idRicevuto }
            lat = c?.lat; lon = c?.lon
        }

        if (lat != null && lon != null) {
            val intent = Intent(Intent.ACTION_VIEW, "google.navigation:q=$lat,$lon".toUri())
            startActivity(intent)
        }
    }
}