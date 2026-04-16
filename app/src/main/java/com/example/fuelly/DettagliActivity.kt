package com.example.fuelly

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.fuelly.classes.*

class DettagliActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dettagli)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        //recupero i dati del benzinaio cliccato dalla activity precedente
        val idRicevuto = intent.getLongExtra("ID_ELEMENTO", -1L)
        val tipoRicevuto = intent.getStringExtra("TIPO_ELEMENTO")

        //in base al tipo di elemento, chiamo la funzione di setup corretta
        if (tipoRicevuto == "BENZINA") {
            //cerco nella lista statica dei benzinai vicini l'elemento con l'id corrispondente
            val stazione = Benzinaio.listaVicini.find { it.id.toLong() == idRicevuto }
            stazione?.let { setupUIBenzina(it) }
        } else if (tipoRicevuto == "EV") {
            //cerco nella lista statica delle colonnine EV l'elemento con l'id corrispondente
            val colonnina = ColonninaEV.listaVicini.find { it.id.toLong() == idRicevuto }
            colonnina?.let { setupUIElettrica(it) }
        }
    }

    //funzioni di popolamento della card con i dati del benzinaio o della colonnina EV
    private fun setupUIBenzina(b: Benzinaio) {
        val card = findViewById<androidx.cardview.widget.CardView>(R.id.stationCard)
        //impostazione del colore della card
        card.setCardBackgroundColor("#0B3D2E".toColorInt())

        //testo della card
        findViewById<TextView>(R.id.txtStationName).setTextColor("#DFFF00".toColorInt())
        findViewById<TextView>(R.id.txtStationAddress).setTextColor("#DFFF00".toColorInt())
        findViewById<TextView>(R.id.txtPrice).setTextColor("#DFFF00".toColorInt())
        findViewById<TextView>(R.id.txtStationName).text = b.bandiera
        findViewById<TextView>(R.id.txtStationAddress).text = b.indirizzo
        //prezzi della card
        val benzinaStr = if (b.prezzoBenzina > 0) "${b.prezzoBenzina}" else "N.D."
        val dieselStr = if (b.prezzoDiesel > 0) "${b.prezzoDiesel}" else "N.D."

        findViewById<TextView>(R.id.txtPrice).text =""

        val logoRes = b.getLogoResource() //ricavo l'icona
        findViewById<ImageView>(R.id.imgPompa).setImageResource(logoRes)
    }

    private fun setupUIElettrica(ev: ColonninaEV) {
        val card = findViewById<androidx.cardview.widget.CardView>(R.id.stationCard)
        //impostazione del colore della card//impostazione del colore della card
        card.setCardBackgroundColor("#0B101E".toColorInt())

        //testo della card
        findViewById<TextView>(R.id.txtStationName).setTextColor("#00FFC2".toColorInt())
        findViewById<TextView>(R.id.txtStationAddress).setTextColor("#00FFC2".toColorInt())
        findViewById<TextView>(R.id.txtPrice).setTextColor("#00FFC2".toColorInt())
        findViewById<TextView>(R.id.txtStationName).text = ev.titolo
        findViewById<TextView>(R.id.txtStationAddress).text = ev.indirizzo

        val infoElettrica = "${ev.numPunti} prese"
        findViewById<TextView>(R.id.txtPrice).text = infoElettrica
        val logoRes = ev.getLogoResource() //ricavo l'icona
        findViewById<ImageView>(R.id.imgPompa).setImageResource(logoRes)
    }
}