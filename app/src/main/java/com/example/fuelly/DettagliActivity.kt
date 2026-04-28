package com.example.fuelly

import android.content.Intent
import android.graphics.Color
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
import java.text.SimpleDateFormat
import java.util.*

class DettagliActivity : AppCompatActivity() {

    private var idRicevuto: Long = -1L //id del elemento selezionato passato dalla MapsActivity
    private var tipoRicevuto: String? = null //tipologia di elemento selezionato passato dalla MapsActivity
    private var distanzaSalvata: Double = 0.0 // Memorizziamo la distanza per usarla ovunque

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dettagli)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        //ricavo i dati passati dalla MapsActivity (id e tipo dell'elemento selezionato)
        idRicevuto = intent.getLongExtra("ID_ELEMENTO", -1L)
        tipoRicevuto = intent.getStringExtra("TIPO_ELEMENTO")

        //richiamo la funzione che esegue la prima inizializzazione dell'activity
        inizializzaInterfaccia()
        //setup dei listeners per gli elementi interattivi dell'interfaccia (switch, pulsante indicazioni, pulsante back)
        setupListeners()
    }

    /* ----IDEA GENERALE-----
   SETUP INIZIALE DELL'ACTIVITY (in base alla tipologia dell'elemento selezionato) ->
   RICAVO TUTTI I DATI NECESSARI PER RIEMPIRE L'ACTIVITY ->
   SETUP FINALE DELL'ACTIVITY */

    //funzione di prima inizializzazione dell'interfaccia,
    // che differenzia tra benzinaio e colonnina EV e richiama le funzioni di setup specifiche per ogni tipologia
    private fun inizializzaInterfaccia() {
        //in base alla tipologia ricevuta, cerco l'elemento corrispondente nella lista dei vicini
        // e se lo trovo richiamo la funzione di setup specifica
        when (tipoRicevuto) {
            "BENZINA" -> {
                val stazione = Benzinaio.listaVicini.find { it.id.toLong() == idRicevuto }
                stazione?.let { setupUIBenzina(it) }
            }
            "EV" -> {
                val colonnina = ColonninaEV.listaVicini.find { it.id.toLong() == idRicevuto }
                colonnina?.let { setupUIElettrica(it) }
                // UI specifica per EV
                findViewById<Switch>(R.id.switchServito)?.visibility = View.GONE
                findViewById<TextView>(R.id.lblSezione)?.text = "INFO RICARICA"
            }
        }
    }


    /*----FUNZIONI DI SETUP PER BENZINAI----*/
    private fun setupUIBenzina(b: Benzinaio) {
        findViewById<androidx.cardview.widget.CardView>(R.id.stationCard)
            ?.setCardBackgroundColor("#0B3D2E".toColorInt())

        val color = "#DFFF00".toColorInt()
        findViewById<TextView>(R.id.txtStationName)?.apply {
            setTextColor(color)
            text = "${b.bandiera} "
        }
        findViewById<TextView>(R.id.txtStationAddress)?.apply {
            setTextColor(color)
            text = b.indirizzo
        }
        findViewById<TextView>(R.id.txtDistance)?.setTextColor(color)
        findViewById<ImageView>(R.id.imgPompa)?.setImageResource(b.getLogoResource())

        //finito il setup grafico dell'activity, richiamo la funzione che calcola la distanza
        //e la funzione che ricava i prezzi dei carburanti per il benzinaio selezionato (di default mostra i prezzi del self-service)
        calcolaDistanzaDettaglio(b.lat, b.lon)
        ricavaPrezziBenzinaio(b.id, false)
    }

    //funzione di ricavo dei prezzi dei carburanti per un benzinaio specifico
    private fun ricavaPrezziBenzinaio(idImpianto: Int, soloServito: Boolean) {
        //elemnti di UI da usare durante il caricamento dei dati

        val loader = findViewById<ProgressBar>(R.id.loadingPrezzi)
        val btnIndicazioni = findViewById<Button>(R.id.btnOttieniIndicazioni)

        val stazione = Benzinaio.listaVicini.find { it.id == idImpianto }
        val siglaProvincia = stazione?.provincia ?: ""

        lifecycleScope.launch {
            loader?.visibility = View.VISIBLE
            try {
                //eseguo le query in parallelo con la programmazione asincrona (per ridurre tempo)
                //query per ricavare la regione corrispondente alla provincia del benzinaio selezionato
                // (dal benzinaio ho solo la sigla della provincia, ma mi serve la regione per ricavare le medie regionali)
                val defMapping = async { SupabaseInstance.client.from("province_regioni").select{ filter{
                            eq("provincia", siglaProvincia)
                        }
                    }
                }

                //query per ricavare i prezzi dei carburanti per il benzinaio selezionato,
                // filtrando in base all'id dell'impianto
                val defPrezzi = async { SupabaseInstance.client.from("prezzi").select{ filter{
                            eq("idImpianto", idImpianto)
                        }
                    }
                }

                //attendo il completamento di entrambe le query
                val resMapping = defMapping.await()
                val resPrezzi = defPrezzi.await()

                //ricavo la regione dalla risposta della prima query
                val nomeRegione = JSONArray(resMapping.data).optJSONObject(0)?.optString("regione") ?: ""

                //ricavo le medie regionali per la regione del benzinaio selezionato
                // filtrando in base alla tipologia (servito/self)
                val resMedie = SupabaseInstance.client.from("media_regionale").select {
                    filter {
                        eq("regione", nomeRegione)
                        eq("isSelf", if (soloServito) "0" else "1")
                    }
                }

                //salvo tutto nei loro rispettivi array JSON
                val arrayPrezzi = JSONArray(resPrezzi.data)
                val arrayMedie = JSONArray(resMedie.data)

                //una volta ottenuti i dati, richiamo la funzione che popolo la lista dei carburanti e aggiorno l'interfaccia
                // (nascondo il loader e mostro il pulsante indicazioni)
                runOnUiThread {
                    popolaListaCarburante(arrayPrezzi, arrayMedie, soloServito)
                    loader?.visibility = View.GONE
                    btnIndicazioni?.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e("Fuelly", "Errore caricamento dati: ${e.message}")
                loader?.visibility = View.GONE
            }
        }
    }

    //funzione che popola la lista dei carburanti nell'interfaccia
    private fun popolaListaCarburante(arrayPrezzi: JSONArray, arrayMedie: JSONArray, soloServito: Boolean) {
        //pulisco la lista da eventuali dati precedenti (nel caso in cui l'utente cambi tra servito e self)
        val container = findViewById<LinearLayout>(R.id.containerListaDettagli)
        container?.removeAllViews()
        var pompeTrovate = 0

        //aggiorno la card con la data dell'ultimo aggiornamento
        if (arrayPrezzi.length() > 0) {
            //prendo la data del primo elemento, che è l'ultimo aggiornamento disponibile per quel benzinaio
            val dataGrezza = arrayPrezzi.getJSONObject(0).optString("dtComu")
            val dataFormattata = formattaDataAggiornamento(dataGrezza)
            findViewById<TextView>(R.id.txtDistance)?.text =
                "${String.format("%.1f", distanzaSalvata)} km • Aggiornato $dataFormattata"
        }

        //ciclo per ogni carburante ottenuto dalla query precedente
        for (i in 0 until arrayPrezzi.length()) {
            val obj = arrayPrezzi.getJSONObject(i)
            val isSelfDb = obj.optString("isSelf") == "1"

            // verifico se è del tipo (servito/self) che voglio mostrare in base allo switch,
            if (soloServito && isSelfDb) continue
            if (!soloServito && !isSelfDb) continue

            //se è del tipo giusto, incremento il contatore delle pompe trovate, tramite il layout inflater
            // creo una nuova view per il carburante
            pompeTrovate++
            val view = layoutInflater.inflate(R.layout.item_carburante, container, false)

            //imposto nome e prezzo del carburante
            val nome = obj.optString("descCarburante")
            val prezzo = obj.optDouble("prezzo")
            view.findViewById<TextView>(R.id.lblNomeCarburante).text = nome
            view.findViewById<TextView>(R.id.lblValorePrezzo).text = "${String.format("%.3f", prezzo)} €"

            //imposto l'icona del carburante e il suo colore in base al tipo di carburante (es. diesel, benzina, gpl, ecc.)
            val imgIcona = view.findViewById<ImageView>(R.id.imgIconaCarburante)
            imgIcona.setColorFilter(getColoreCarburante(nome).toColorInt())

            //richiamo la funzione che gestisce la differenza tra il prezzo del carburante del benzinaio selezionato e
            // la media regionale per quel tipo di carburante,
            gestisciDifferenzaMedia(view, nome, prezzo, arrayMedie)

            //raccolto tutti i dati necessari, aggiungo la view del carburante al container che le contiene tutte
            container?.addView(view)
        }

        //se alla fine del ciclo non ho trovato pompe del tipo richiesto (servito/self) mostro un messaggio
        if (pompeTrovate == 0) mostrateMessaggioVuoto(container, soloServito)
    }

    //funzione che gestisce la differenza tra il prezzo del carburante del benzinaio selezionato e la media regionale per quel tipo di carburante,
    private fun gestisciDifferenzaMedia(view: View, nome: String, prezzo: Double, arrayMedie: JSONArray) {
        val freccia = view.findViewById<ImageView>(R.id.imgFrecciaMedia)
        val txtDiff = view.findViewById<TextView>(R.id.txtDifferenzaMedia)
        val categoria = getCategoriaPerMedia(nome)

        var mediaRegionale = 0.0
        for (j in 0 until arrayMedie.length()) {
            val m = arrayMedie.getJSONObject(j)
            //cerco la media regionale corrispondente alla categoria del carburante (es. benzina, diesel, gpl, ecc.)
            if (m.optString("tipologia").equals(categoria, ignoreCase = true)) {
                mediaRegionale = m.optDouble("prezzo_medio")
                break
            }
        }

        //se trovo una media regionale valida, confronto il prezzo del carburante del benzinaio
        // con la media regionale e aggiorno l'interfaccia di conseguenza
        if (mediaRegionale > 0) {
            val diff = prezzo - mediaRegionale
            val isPremium = !nome.equals(categoria, ignoreCase = true)

            if (diff > 0) {
                freccia.setImageResource(R.drawable.up_arrow)
                freccia.setColorFilter(Color.RED)
                txtDiff.text = "+${String.format("%.3f", diff)}" + (if (isPremium) " (Prem.)" else "")
                txtDiff.setTextColor(Color.RED)
            } else {
                freccia.setImageResource(R.drawable.down_arrow)
                freccia.setColorFilter("#2E7D32".toColorInt())
                txtDiff.text = String.format("%.3f", diff)
                txtDiff.setTextColor("#2E7D32".toColorInt())
            }
        } else {
            //se non trovo una media regionale valida, nascondo la freccia e il testo della differenza
            freccia.visibility = View.GONE
            txtDiff.visibility = View.GONE
        }
    }

    //funzione che restituisce il colore associato a un tipo di carburante in base al nome del carburante stesso
    private fun getColoreCarburante(nome: String): String {
        val n = nome.lowercase()
        return when {
            n.contains("diesel") || n.contains("gasolio") -> "#424242"
            n.contains("hvo") || n.contains("rehvo") -> "#76FF03"
            n.contains("benzina") -> "#2E7D32"
            n.contains("gpl") || n.contains("lpg") -> "#00574B"
            n.contains("metano") || n.contains("gnc") -> "#01579B"
            n.contains("gnl") || n.contains("lng") -> "#00B8D4"
            else -> "#FFFFFF"
        }
    }

    //funzione che restituisce la macrocategoria di carburante (es. benzina, diesel, gpl, ecc.) da usare per il confronto con la media regionale,
    private fun getCategoriaPerMedia(nome: String): String {
        val n = nome.lowercase()
        return when {
            n.contains("benzina") || n.contains("v-power") || n.contains("super") -> "Benzina"
            n.contains("diesel") || n.contains("gasolio") || n.contains("hvo") -> "Gasolio"
            n.contains("gpl") -> "GPL"
            n.contains("metano") -> "Metano"
            else -> ""
        }
    }


    /*----FUNZIONI DI SETUP PER COLONNINA EV----*/
    //funzione di setup dell'interfaccia per le colonnine EV, che imposta colori, testi e immagini specifiche per questa tipologia
    private fun setupUIElettrica(ev: ColonninaEV) {
        findViewById<androidx.cardview.widget.CardView>(R.id.stationCard)
            ?.setCardBackgroundColor("#0B101E".toColorInt())

        val color = Color.parseColor("#00FFC2")
        findViewById<TextView>(R.id.txtStationName)?.apply {
            setTextColor(color)
            text = "${ev.titolo} "
        }
        findViewById<TextView>(R.id.txtStationAddress)?.apply {
            setTextColor(color)
            text = ev.indirizzo
        }
        findViewById<TextView>(R.id.txtPrice)?.apply {
            setTextColor(color)
            text = "${ev.numPunti} prese disponibili"
        }
        findViewById<ImageView>(R.id.imgPompa)?.setImageResource(ev.getLogoResource())

        //TODO: implementare la funzione per ricavare i prezzi di ricarica per le colonnine EV
        calcolaDistanzaDettaglio(ev.lat, ev.lon)
    }


    /*-----HELPER GENERALI (EV/BENZINA)-----*/
    //funzione che imposta i listeners per gli elementi interattivi dell'interfaccia
    // (switch, pulsante "Ottieni indicazioni", pulsante indietro)
    private fun setupListeners() {
        //listener Switch Self/Servito
        findViewById<Switch>(R.id.switchServito)?.setOnCheckedChangeListener { btn, isChecked ->
            btn.text = if (isChecked) "Servito" else "Self-Service"
            if (tipoRicevuto == "BENZINA") {
                ricavaPrezziBenzinaio(idRicevuto.toInt(), isChecked)
            }
        }

        //listener pulsante "Ottieni indicazioni"
        findViewById<Button>(R.id.btnOttieniIndicazioni)?.setOnClickListener {
            avviaNavigatore()
        }

        //listener pulsante indietro
        findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    //funzione che calcola la distanza tra la posizione dell'utente (passata dalla MapsActivity)
    //e la posizione del benzinaio/colonnina EV selezionato,
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

    //funzione che mostra un messaggio nell'interfaccia quando non sono disponibili prezzi per il tipo di servizio selezionato (servito/self)
    private fun mostrateMessaggioVuoto(container: LinearLayout?, soloServito: Boolean) {
        val txt = TextView(this).apply {
            text = "Nessun prezzo disponibile per il ${if (soloServito) "servito" else "self"}."
            setTextColor(Color.GRAY)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 50, 0, 50)
            typeface = ResourcesCompat.getFont(this@DettagliActivity, R.font.dm_sans_medium)
        }
        container?.addView(txt)
    }

    //funzione che avvia (tramite intent) il navigatore del telefono con le coordinate del benzinaio/colonnina EV selezionato
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

    //funzione che formatta la data dell'ultimo aggiornamento dei prezzi, restituendo una stringa più leggibile per l'utente
    private fun formattaDataAggiornamento(dataString: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ITALY)
            val data = sdf.parse(dataString) ?: return "Recentemente"
            val adesso = Calendar.getInstance()
            val dataCom = Calendar.getInstance().apply { time = data }

            if (adesso.get(Calendar.DAY_OF_YEAR) == dataCom.get(Calendar.DAY_OF_YEAR)) {
                "Oggi alle ${SimpleDateFormat("HH:mm", Locale.ITALY).format(data)}"
            } else {
                SimpleDateFormat("dd/MM", Locale.ITALY).format(data)
            }
        } catch (e: Exception) { "Recentemente" }
    }

}
