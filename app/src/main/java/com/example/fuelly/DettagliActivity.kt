package com.example.fuelly

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.fuelly.classes.*

class DettagliActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dettagli)

        // Gestione WindowInsets (già presente nel tuo codice)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 1. RECUPERO DATI DALL'INTENT
        val idRicevuto = intent.getLongExtra("ID_ELEMENTO", -1L)
        val tipoRicevuto = intent.getStringExtra("TIPO_ELEMENTO")

        // 2. LOGICA DI SELEZIONE
        if (tipoRicevuto == "BENZINA") {
            // Cerchiamo nella lista statica dei benzinai
            val stazione = Benzinaio.listaVicini.find { it.id.toLong() == idRicevuto }
            stazione?.let { setupUIBenzina(it) }
        } else if (tipoRicevuto == "EV") {
            // Cerchiamo nella lista statica delle colonnine
            val colonnina = ColonninaEV.listaVicini.find { it.id.toLong() == idRicevuto }
            colonnina?.let { setupUIElettrica(it) }
        }
    }

    // 3. FUNZIONI PER POPOLARE LA UI
    private fun setupUIBenzina(b: Benzinaio) {
        findViewById<TextView>(R.id.txtStationName).text = b.bandiera
        findViewById<TextView>(R.id.txtStationAddress).text = b.indirizzo

        // Qui puoi mostrare tutti i prezzi che vuoi
        val prezziFull = "Benzina: ${b.prezzoBenzina}€\nDiesel: ${b.prezzoDiesel}€"
        findViewById<TextView>(R.id.txtPrice).text = prezziFull

        // Cambia l'header o l'icona in base al brand
        findViewById<ImageView>(R.id.imgPompa).setImageResource(R.drawable.fuel_logo)
    }

    private fun setupUIElettrica(ev: ColonninaEV) {
        findViewById<TextView>(R.id.txtStationName).text = ev.titolo
        findViewById<TextView>(R.id.txtStationAddress).text = ev.indirizzo
        findViewById<TextView>(R.id.txtPrice).text = "${ev.potenzaKW} kW - ${ev.numPunti} Prese"

        findViewById<ImageView>(R.id.imgPompa).setImageResource(R.drawable.ev_logo)
    }
}