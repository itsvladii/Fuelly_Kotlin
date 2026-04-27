package com.example.fuelly

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.fuelly.classes.*
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.fuelly.supabase.SupabaseInstance
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.json.JSONArray

class DettagliActivity : AppCompatActivity() {

    private var idRicevuto: Long = -1L
    private var tipoRicevuto: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dettagli)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        idRicevuto = intent.getLongExtra("ID_ELEMENTO", -1L)
        tipoRicevuto = intent.getStringExtra("TIPO_ELEMENTO")

        // 1. Setup UI Iniziale
        if (tipoRicevuto == "BENZINA") {
            val stazione = Benzinaio.listaVicini.find { it.id.toLong() == idRicevuto }
            stazione?.let { setupUIBenzina(it) }
        } else if (tipoRicevuto == "EV") {
            val colonnina = ColonninaEV.listaVicini.find { it.id.toLong() == idRicevuto }
            colonnina?.let { setupUIElettrica(it) }
            // Nascondiamo lo switch se siamo su una colonnina elettrica
            findViewById<Switch>(R.id.switchServito).visibility = android.view.View.GONE
            findViewById<TextView>(R.id.lblSezione).text = "INFO RICARICA"
        }

        // 2. Listener per lo Switch (Filtro Self/Servito)
        findViewById<Switch>(R.id.switchServito).setOnCheckedChangeListener { _, isChecked ->
            if (tipoRicevuto == "BENZINA") {
                caricaPrezziDettagliati(idRicevuto.toInt(), isChecked)
            }
        }

        // 3. Listener Bottone Navigatore
        findViewById<Button>(R.id.btnOttieniIndicazioni).setOnClickListener {
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
                val gmmIntentUri = Uri.parse("google.navigation:q=$lat,$lon")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                startActivity(mapIntent)
            }
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupUIBenzina(b: Benzinaio) {
        val card = findViewById<androidx.cardview.widget.CardView>(R.id.stationCard)
        card.setCardBackgroundColor("#0B3D2E".toColorInt())

        findViewById<TextView>(R.id.txtStationName).apply {
            setTextColor(Color.parseColor("#DFFF00"))
            text = b.bandiera
        }
        findViewById<TextView>(R.id.txtStationAddress).apply {
            setTextColor(Color.parseColor("#DFFF00"))
            text = b.indirizzo
        }

        findViewById<TextView>(R.id.txtPrice).text = "" // Puliamo il prezzo generico
        findViewById<ImageView>(R.id.imgPompa).setImageResource(b.getLogoResource())

        // Carichiamo i prezzi (default: isSelf = 1, quindi soloServito = false)
        caricaPrezziDettagliati(b.id, false)
    }


    private fun caricaPrezziDettagliati(idImpianto: Int, soloServito: Boolean) {
        val container = findViewById<LinearLayout>(R.id.containerListaDettagli)
        // Troviamo il benzinaio per sapere la sua provincia (es. "RM")
        val stazione = Benzinaio.listaVicini.find { it.id == idImpianto }
        val siglaProvincia = stazione?.provincia ?: ""
        val loader = findViewById<ProgressBar>(R.id.loadingPrezzi)

        lifecycleScope.launch {
            runOnUiThread { loader.visibility = View.VISIBLE }
            try {
                // Avviamo tutte le query contemporaneamente
                val deferredMapping = async {
                    SupabaseInstance.client.from("province_regioni").select { filter { eq("provincia", siglaProvincia) } }
                }
                val deferredPrezzi = async {
                    SupabaseInstance.client.from("prezzi").select { filter { eq("idImpianto", idImpianto) } }
                }

                // Attendiamo i primi due risultati
                val resMapping = deferredMapping.await()
                val resPrezzi = deferredPrezzi.await()

                val nomeRegione = JSONArray(resMapping.data).optJSONObject(0)?.optString("regione") ?: ""

                // Ora che abbiamo la regione, prendiamo le medie (questa deve per forza aspettare la prima)
                val resMedie = SupabaseInstance.client.from("media_regionale").select {
                    filter {
                        eq("regione", nomeRegione)
                        eq("isSelf", if (soloServito) "0" else "1")
                    }
                }

                val arrayPrezzi = JSONArray(resPrezzi.data)
                val arrayMedie = JSONArray(resMedie.data)

                runOnUiThread {
                    container.removeAllViews()
                    popolaInterfaccia(arrayPrezzi, arrayMedie, soloServito)
                    loader.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("Fuelly", "Errore: ${e.message}")
            }
        }
    }

    private fun popolaInterfaccia(arrayPrezzi: JSONArray, arrayMedie: JSONArray, soloServito: Boolean) {
        val container = findViewById<LinearLayout>(R.id.containerListaDettagli)

        for (i in 0 until arrayPrezzi.length()) {
            val obj = arrayPrezzi.getJSONObject(i)
            val isSelfDb = obj.optString("isSelf") == "1"

            // Filtro switch
            if (soloServito && isSelfDb) continue
            if (!soloServito && !isSelfDb) continue

            val view = layoutInflater.inflate(R.layout.item_carburante, container, false)
            val nome = obj.optString("descCarburante")
            val prezzo = obj.optDouble("prezzo")

            // -- Setup Icona Pompa --
            val imgPompa = view.findViewById<ImageView>(R.id.imgIconaCarburante)
            val colore = when {
                nome.contains("Diesel", true) -> "#424242"   // Grigio
                nome.contains("Benzina", true) -> "#2E7D32"  // Verde
                nome.contains("GPL", true) -> "#00574B"      // Ottanio
                nome.contains("Metano", true) -> "#01579B"   // Blu
                else -> "#757575"                            // Default
            }
            imgPompa.setColorFilter(Color.parseColor(colore))

            // -- Calcolo Differenza Media --
            val freccia = view.findViewById<ImageView>(R.id.imgFrecciaMedia)
            val txtDiff = view.findViewById<TextView>(R.id.txtDifferenzaMedia)

            var mediaRegionale = 0.0
            for (j in 0 until arrayMedie.length()) {
                val m = arrayMedie.getJSONObject(j)
                if (m.optString("tipologia").equals(nome, ignoreCase = true)) {
                    mediaRegionale = m.optDouble("prezzo_medio")
                    break
                }
            }

            if (mediaRegionale > 0) {
                val diff = prezzo - mediaRegionale
                if (diff > 0) {
                    freccia.setImageResource(R.drawable.up_arrow)
                    freccia.setColorFilter(Color.RED)
                    txtDiff.text = "+${String.format("%.3f", diff)}"
                    txtDiff.setTextColor(Color.RED)
                } else {
                    freccia.setImageResource(R.drawable.down_arrow)
                    freccia.setColorFilter(Color.parseColor("#2E7D32"))
                    txtDiff.text = String.format("%.3f", diff)
                    txtDiff.setTextColor(Color.parseColor("#2E7D32"))
                }
            }

            view.findViewById<TextView>(R.id.lblNomeCarburante).text = nome
            view.findViewById<TextView>(R.id.lblValorePrezzo).text = "${String.format("%.3f", prezzo)} €"
            container.addView(view)
        }
    }

    private fun setupUIElettrica(ev: ColonninaEV) {
        val card = findViewById<androidx.cardview.widget.CardView>(R.id.stationCard)
        card.setCardBackgroundColor("#0B101E".toColorInt())

        findViewById<TextView>(R.id.txtStationName).apply {
            setTextColor(Color.parseColor("#00FFC2"))
            text = ev.titolo
        }
        findViewById<TextView>(R.id.txtStationAddress).apply {
            setTextColor(Color.parseColor("#00FFC2"))
            text = ev.indirizzo
        }

        findViewById<TextView>(R.id.txtPrice).apply {
            setTextColor(Color.parseColor("#00FFC2"))
            text = "${ev.numPunti} prese disponibili"
        }

        findViewById<ImageView>(R.id.imgPompa).setImageResource(ev.getLogoResource())
    }
}