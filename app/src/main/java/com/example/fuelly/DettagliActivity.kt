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
import androidx.core.content.res.ResourcesCompat
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

    //variabili per memorizzare ID e tipo dell'elemento selezionato, ricevuti dalla MapsActivity tramite intent
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

        //raccolgo l'ID e il tipo dell'elemento selezionato dalla MapsActivity
        idRicevuto = intent.getLongExtra("ID_ELEMENTO", -1L)
        tipoRicevuto = intent.getStringExtra("TIPO_ELEMENTO")

        //in base allo tipo, carico i dettagli specifici e popolo l'interfaccia nell'activity
        if (tipoRicevuto == "BENZINA") {
            val stazione = Benzinaio.listaVicini.find { it.id.toLong() == idRicevuto }
            stazione?.let { setupUIBenzina(it) }
        } else if (tipoRicevuto == "EV") {
            val colonnina = ColonninaEV.listaVicini.find { it.id.toLong() == idRicevuto }
            colonnina?.let { setupUIElettrica(it) }
            //per le colonnine EV nascondiamo lo switch (non ha senso) e cambiamo il titolo della sezione dettagli
            findViewById<Switch>(R.id.switchServito).visibility = android.view.View.GONE
            findViewById<TextView>(R.id.lblSezione).text = "INFO RICARICA"
        }

        val switchServito = findViewById<Switch>(R.id.switchServito)

        switchServito.setOnCheckedChangeListener { _, isChecked ->
            // Cambiamo il testo in base allo stato
            switchServito.text = if (isChecked) "Servito" else "Self-Service"

            if (tipoRicevuto == "BENZINA") {
                val stazione = Benzinaio.listaVicini.find { it.id.toLong() == idRicevuto }
                stazione?.let {
                    ricavaPrezziBenzinaio(stazione.id, isChecked)
                }
            }
        }

        //listener per il pulsante "Ottieni indicazioni"
        findViewById<Button>(R.id.btnOttieniIndicazioni).setOnClickListener {
            val lat: Double?
            val lon: Double?

            //ricavo le coordinate dell'elemento selezionato
            if (tipoRicevuto == "BENZINA") {
                val s = Benzinaio.listaVicini.find { it.id.toLong() == idRicevuto }
                lat = s?.lat; lon = s?.lon
            } else {
                val c = ColonninaEV.listaVicini.find { it.id.toLong() == idRicevuto }
                lat = c?.lat; lon = c?.lon
            }

            if (lat != null && lon != null) {
                //tramite intent apro Google Maps con le coordinate del punto di interesse
                val gmmIntentUri = "google.navigation:q=$lat,$lon".toUri()
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                startActivity(mapIntent)
            }
        }

        //listener per il pulsante "Indietro"
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    /*IDEA GENERALE: Setup Iniziale UI -> Ricavo Prezzi -> Setup Finale UI*/

    //funzione di "setup iniziale" dell'interfaccia di DettagliActivity (per i benzinai)
    // impostazione della card con i dati generali del benzinaio (nome, indirizzo, logo) e con lo stile grafico specifico (colori, font, ecc.)
    private fun setupUIBenzina(b: Benzinaio) {
        //impostazione grafica generale della card (colore di sfondo, testo, logo)
        val card = findViewById<androidx.cardview.widget.CardView>(R.id.stationCard)
        card.setCardBackgroundColor("#0B3D2E".toColorInt())

        findViewById<TextView>(R.id.txtStationName).apply {
            setTextColor(Color.parseColor("#DFFF00"))
            text = b.bandiera+" "
        }
        findViewById<TextView>(R.id.txtStationAddress).apply {
            setTextColor(Color.parseColor("#DFFF00"))
            text = b.indirizzo
        }

        findViewById<TextView>(R.id.txtPrice).text = "" // Puliamo il prezzo generico (TODO: da capire cosa mettere)
        findViewById<ImageView>(R.id.imgPompa).setImageResource(b.getLogoResource())

        //invoco la funzione di caricamento dei prezzi delle pompe disponibili
        ricavaPrezziBenzinaio(b.id, false)
    }

    //funzione che carica i prezzi delle pompe disponibili per un dato benzinaio
    private fun ricavaPrezziBenzinaio(idImpianto: Int, soloServito: Boolean) {
        //container dove inseriremo dinamicamente i prezzi
        val container = findViewById<LinearLayout>(R.id.containerListaDettagli)
        //raccolgo tutte le informazioni necessarie per le query (id e provincia)
        val stazione = Benzinaio.listaVicini.find { it.id == idImpianto }
        val siglaProvincia = stazione?.provincia ?: ""
        //mostriamo il loader mentre carichiamo i dati (c'è un leggero delay durante la richiesta al database)
        val loader = findViewById<ProgressBar>(R.id.loadingPrezzi)

        //inizia una coroutine per eseguire le query in background senza bloccare l'interfaccia utente
        lifecycleScope.launch {
            // mostriamo il loader prima di iniziare le query
            runOnUiThread { loader.visibility = View.VISIBLE }
            try {
                //avvio le prime due query in parallelo (mapping provincia-regione e prezzi dell'impianto)
                val deferredMapping = async {
                    SupabaseInstance.client.from("province_regioni")
                        .select { filter { eq("provincia", siglaProvincia) } }
                }
                val deferredPrezzi = async {
                    SupabaseInstance.client.from("prezzi").select { filter { eq("idImpianto", idImpianto) } }
                }

                //attendo il completamento delle prime due query
                val resMapping = deferredMapping.await()
                val resPrezzi = deferredPrezzi.await()

                //ricavo il nome della regione dal risultato del mapping (con fallback in caso di errori o dati mancanti)
                val nomeRegione = JSONArray(resMapping.data).optJSONObject(0)?.optString("regione") ?: ""

                //ricavo le medie regionali per la regione corrispondente (filtrando in base alla tipologia di prezzo (servito o self-service))
                val resMedie = SupabaseInstance.client.from("media_regionale").select {
                    filter {
                        eq("regione", nomeRegione)
                        eq("isSelf", if (soloServito) "0" else "1")
                    }
                }

                //creo due array JSON dai risultati delle query per poterli manipolare più facilmente nella funzione di popolamento dell'interfaccia
                val arrayPrezzi = JSONArray(resPrezzi.data)
                val arrayMedie = JSONArray(resMedie.data)

                //una volta ottenuti tutti i dati necessari, rimuovo il loader e richiamo la funzione di popolamento dell'interfaccia, passando i dati ottenuti dalle query
                runOnUiThread {
                    container.removeAllViews()
                    popolaListaCarburante(arrayPrezzi, arrayMedie, soloServito)
                    loader.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("Fuelly", "Errore: ${e.message}")
            }
        }
    }

    //funzione che popola l'activity con i prezzi delle pompe disponibili,
    //evidenziando la differenza rispetto alla media regionale e filtrando in base al tipo di prezzo (servito o self-service)
    private fun popolaListaCarburante(arrayPrezzi: JSONArray, arrayMedie: JSONArray, soloServito: Boolean) {
        //container dove inseriremo dinamicamente i prezzi
        val container = findViewById<LinearLayout>(R.id.containerListaDettagli)
        container.removeAllViews() // Pulizia iniziale
        var pompeTrovate=0

        //per ogni prezzo ottenuto dalla query, creo
        for (i in 0 until arrayPrezzi.length()) {
            val obj = arrayPrezzi.getJSONObject(i)
            val isSelfDb = obj.optString("isSelf") == "1"

            //filtro switch (self o servito)
            if (soloServito && isSelfDb) continue
            if (!soloServito && !isSelfDb) continue

            pompeTrovate++ // Abbiamo trovato almeno una pompa valida per il filtro attuale

            //inflating del layout dell'item (item_carburante.xml)
            // e popolamento dei dati (nome carburante, prezzo, differenza rispetto alla media regionale)
            val view = layoutInflater.inflate(R.layout.item_carburante, container, false)
            val nome = obj.optString("descCarburante")
            val nomeLower = nome.lowercase()
            val prezzo = obj.optDouble("prezzo")
            val categoriaRiferimento = getCategoriaPerMedia(nome)

            //settaggio del colore dell'icona in base al tipo di carburante (per una rapida identificazione visiva)
            val imgPompa = view.findViewById<ImageView>(R.id.imgIconaCarburante)
            val colore = when {
                // 1. DIESEL / GASOLIO (Grigio Antracite)
                nomeLower.contains("diesel") || nomeLower.contains("gasolio") -> "#424242"

                // 2. BIOCARBURANTI / HVO (Verde Lime / Brillante)
                nomeLower.contains("hvo") || nomeLower.contains("rehvo") -> "#76FF03"

                // 3. BENZINA (Verde Benzina Standard)
                nomeLower.contains("benzina") -> "#2E7D32"

                // 4. GPL (Ottanio / Teal)
                nomeLower.contains("gpl") || nomeLower.contains("lpg") -> "#00574B"

                // 5. METANO / GNC (Blu Notte)
                nomeLower.contains("metano") || nomeLower.contains("gnc") -> "#01579B"

                // 6. GNL / LNG (Ciano Freddo)
                nomeLower.contains("gnl") || nomeLower.contains("lng") -> "#00B8D4"

                else -> "#ffffff" //nero per tutto il resto
            }
            imgPompa.setColorFilter(colore.toColorInt())

            //calcolo della differenza rispetto alla media regionale per quel tipo di carburante,
            val freccia = view.findViewById<ImageView>(R.id.imgFrecciaMedia)
            val txtDiff = view.findViewById<TextView>(R.id.txtDifferenzaMedia)

            var mediaRegionale = 0.0
            for (j in 0 until arrayMedie.length()) {
                val m = arrayMedie.getJSONObject(j)
                //confrontiamo con la categoria mappata, non con il nome originale
                if (m.optString("tipologia").equals(categoriaRiferimento, ignoreCase = true)) {
                    mediaRegionale = m.optDouble("prezzo_medio")
                    break
                }
            }

            //se la media regionale è disponibile, confronto il prezzo del carburante con la media e
            // aggiorno l'interfaccia di conseguenza (freccia rossa se il prezzo è superiore alla media, verde se è inferiore)
            if (mediaRegionale > 0) {
                val diff = prezzo - mediaRegionale
                val isPremium = !nome.equals(categoriaRiferimento, ignoreCase = true)

                if (diff > 0) {
                    freccia.setImageResource(R.drawable.up_arrow)
                    freccia.setColorFilter(Color.RED)
                    //se è un carburante "premium" (v-power ect.) lo evidenziamo con (Premium)
                    txtDiff.text = "+${String.format("%.3f", diff)}" + (if (isPremium) " (Premium)" else "")
                    txtDiff.setTextColor(Color.RED)
                } else {
                    freccia.setImageResource(R.drawable.down_arrow)
                    freccia.setColorFilter(Color.parseColor("#2E7D32")) // Verde
                    txtDiff.text = String.format("%.3f", diff)
                    txtDiff.setTextColor(Color.parseColor("#2E7D32"))
                }
            }

            //settaggio dei dati del carburante (nome e prezzo formattato) nei rispettivi TextView all'interno dell'item
            view.findViewById<TextView>(R.id.lblNomeCarburante).text = nome
            view.findViewById<TextView>(R.id.lblValorePrezzo).text = "${String.format("%.3f", prezzo)} €"
            container.addView(view)

            var btnIndicazione=findViewById<Button>(R.id.btnOttieniIndicazioni)
            btnIndicazione.visibility=View.VISIBLE

        }

        if (pompeTrovate == 0) {
            val txtEmpty = TextView(this).apply {
                val modalita = if (soloServito) "servito" else "self-service"
                text = "Nessun prezzo disponibile per il $modalita in questa stazione."
                setTextColor(Color.GRAY)
                gravity = android.view.Gravity.CENTER
                setPadding(0, 50, 0, 50)
                typeface = ResourcesCompat.getFont(this@DettagliActivity, R.font.dm_sans_medium)
            }
            container.addView(txtEmpty)
        }
    }

    //funzione di "aggregamento" dei vari carburanti in macrogruppi
    private fun getCategoriaPerMedia(nomeCarburante: String): String {
        val n = nomeCarburante.lowercase()
        return when {
            // Se contiene queste parole, confrontalo con la media "Benzina"
            n.contains("benzina") || n.contains("v-power") || n.contains("speciale") || n.contains("super") -> "Benzina"

            // Se contiene queste, confrontalo con "Gasolio"
            n.contains("diesel") || n.contains("gasolio") || n.contains("hvo") -> "Gasolio"

            n.contains("gpl") -> "GPL"
            n.contains("metano") -> "Metano"
            else -> ""
        }
    }

    //funzione di "setup iniziale" dell'interfaccia di DettagliActivity (per le colonnine EV)
    // impostazione della card con i dati generali della colonnina (nome, indirizzo, logo) e con lo stile grafico specifico (colori, font, ecc.)
    private fun setupUIElettrica(ev: ColonninaEV) {
        val card = findViewById<androidx.cardview.widget.CardView>(R.id.stationCard)
        card.setCardBackgroundColor("#0B101E".toColorInt())

        findViewById<TextView>(R.id.txtStationName).apply {
            setTextColor(Color.parseColor("#00FFC2"))
            text = ev.titolo+" "
        }
        findViewById<TextView>(R.id.txtStationAddress).apply {
            setTextColor(Color.parseColor("#00FFC2"))
            text = ev.indirizzo+" "
        }

        findViewById<TextView>(R.id.txtPrice).apply {
            setTextColor(Color.parseColor("#00FFC2"))
            text = "${ev.numPunti} prese disponibili"
        }

        findViewById<ImageView>(R.id.imgPompa).setImageResource(ev.getLogoResource())
    }
}
