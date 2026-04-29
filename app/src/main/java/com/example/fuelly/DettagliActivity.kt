package com.example.fuelly

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.toColorInt
import com.example.fuelly.classes.*
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.fuelly.supabase.SupabaseInstance
import io.github.jan.supabase.auth.auth
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

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false  // icone status bar bianche
        windowInsetsController.isAppearanceLightNavigationBars = false  // icone nav bar biancheo

        idRicevuto = intent.getLongExtra("ID_ELEMENTO", -1L)
        tipoRicevuto = intent.getStringExtra("TIPO_ELEMENTO")

        verificaSeSalvato(idRicevuto)
        inizializzaInterfaccia()
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
                colonnina?.let { setupUIElettrica(it)
                    ricavaInfoEV(colonnina)}
                // UI specifica per EV
                findViewById<Switch>(R.id.switchServito)?.visibility = View.GONE
                findViewById<TextView>(R.id.lblSezione)?.text = "INFO RICARICA"
            }
        }
    }
    
    /*----FUNZIONI DI SETUP PER BENZINAI----*/
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

        //finito il setup grafico dell'activity, richiamo la funzione che calcola la distanza
        //e la funzione che ricava i prezzi dei carburanti per il benzinaio selezionato (di default mostra i prezzi del self-service)
        calcolaDistanzaDettaglio(b.lat, b.lon)
        ricavaPrezziBenzinaio(b.id, false)
    }

    //funzione di ricavo dei prezzi dei carburanti per un benzinaio specifico
    private fun ricavaPrezziBenzinaio(idImpianto: Int, soloServito: Boolean) {
        //elementi di UI da usare durante il caricamento dei dati

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

    //funzione che salva un elemento preferito nel database
    private fun salvaPreferito(idBenzinaio: Long) {
        val user = SupabaseInstance.client.auth.currentUserOrNull()

        //fallback se l'utente non è loggato (non dovrebbe mai succedere)
        if (user == null) {
            Toast.makeText(this, "Devi essere loggato per gestire i preferiti", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                //verifico se esiste già tra i preferiti
                val esistente = SupabaseInstance.client.from("salvati")
                    .select {
                        filter {
                            eq("idUtente", user.id)
                            eq("idImpianto", idBenzinaio)
                        }
                    }.decodeList<Salvato>()

                if (esistente.isNotEmpty()) {
                    //se esiste, lo rimuovo
                    SupabaseInstance.client.from("salvati").delete {
                        filter {
                            eq("idUtente", user.id)
                            eq("idImpianto", idBenzinaio)
                        }
                    }

                    runOnUiThread {
                        Toast.makeText(this@DettagliActivity, "Rimosso dai preferiti", Toast.LENGTH_SHORT).show()
                        findViewById<ImageButton>(R.id.btnSalva)?.setImageResource(R.drawable.bookmark_svg) // Icona vuota
                    }
                } else {
                    //se non esiste, lo aggiungo
                    val nuovoPreferito = Salvato(
                        idUtente = user.id,
                        idBenzinaio = idBenzinaio,
                    )
                    SupabaseInstance.client.from("salvati").insert(nuovoPreferito)

                    runOnUiThread {
                        Toast.makeText(this@DettagliActivity, "Salvato nei preferiti!", Toast.LENGTH_SHORT).show()
                        findViewById<ImageButton>(R.id.btnSalva)?.setImageResource(R.drawable.bookmark_salvato) // Icona piena
                    }
                }
            } catch (e: Exception) {
                Log.e("Fuelly", "Errore gestione preferiti: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@DettagliActivity, "Errore nella comunicazione con il database", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    //funzione che verifica nel onCreate se il benzinaio selezionato è tra i salvati nel DB
    private fun verificaSeSalvato(idBenzinaio: Long) {
        val user = SupabaseInstance.client.auth.currentUserOrNull() ?: return

        lifecycleScope.launch {
            try {
                // Cerchiamo se esiste il record nella tabella salvati
                val esistente = SupabaseInstance.client.from("salvati")
                    .select {
                        filter {
                            eq("idUtente", user.id)
                            eq("idImpianto", idBenzinaio)
                        }
                    }.decodeList<Salvato>()

                // Se la lista non è vuota, l'elemento è tra i preferiti
                if (esistente.isNotEmpty()) {
                    runOnUiThread {
                        findViewById<ImageButton>(R.id.btnSalva)?.setImageResource(R.drawable.bookmark_salvato)
                    }
                } else {
                    runOnUiThread {
                        findViewById<ImageButton>(R.id.btnSalva)?.setImageResource(R.drawable.bookmark_svg)
                    }
                }
            } catch (e: Exception) {
                Log.e("Fuelly", "Errore verifica preferito: ${e.message}")
            }
        }
    }

    /*----FUNZIONI DI SETUP PER COLONNINA EV----*/
    //funzione di setup dell'interfaccia per le colonnine EV, che imposta colori, testi e immagini specifiche per questa tipologia
    private fun setupUIElettrica(ev: ColonninaEV) {
        findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.stationHeader)
            ?.setBackgroundColor("#0B101E".toColorInt())

        val color = "#00FFC2".toColorInt()
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
        findViewById<TextView>(R.id.txtDistance)?.setTextColor(color)

        calcolaDistanzaDettaglio(ev.lat, ev.lon)

    }

    //funzione di ricavo dei dati relativi alle colonnine EV vicine
    private fun ricavaInfoEV(ev: ColonninaEV) {
        //elementi di UI da usare durante il caricamento dei dati
        val container = findViewById<LinearLayout>(R.id.containerListaDettagli)
        val loader = findViewById<ProgressBar>(R.id.loadingPrezzi)
        val client = okhttp3.OkHttpClient()
        val apiKey=BuildConfig.EV_API_KEY


        //la richiesta di API, dove cerco la colonna specifica tramite la latitudine e longitudine della colonnina EV
        // (non funzionava benissimo con l'ID)
        val url = "https://api.openchargemap.io/v3/poi/?output=json" +
                "&latitude=${ev?.lat}&longitude=${ev?.lon}" +
                "&distance=0.1&distanceunit=km&maxresults=1&key=$apiKey"

        val request = okhttp3.Request.Builder().url(url).build()
        loader?.visibility = View.VISIBLE

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                runOnUiThread { loader?.visibility = View.GONE }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!response.isSuccessful) return
                    val body = response.body.string()
                    Log.d("Fuelly_OCM", "Response: $body")
                        val jsonArray = JSONArray(body)
                        if (jsonArray.length() > 0) {
                            //ricavo il point-of-interest (la colonnina) e poi le connessioni
                            val poi = jsonArray.getJSONObject(0)
                            val connections = poi.getJSONArray("Connections")

                            //finito di ricavare i dati, aggiorno l'interfaccia
                            runOnUiThread {
                                container?.removeAllViews()
                                popolaListaPreseOCM(connections) //chiamo la funzione di popolamento della lista delle prese
                                loader?.visibility = View.GONE
                            }
                        }
                }
            }
        })
    }

    //funzione di popolamento della lista delle prese
    private fun popolaListaPreseOCM(connections: JSONArray) {
        val container = findViewById<LinearLayout>(R.id.containerListaDettagli)

        //ciclo per tutte le prese raccolte
        for (i in 0 until connections.length()) {
            val conn = connections.getJSONObject(i)

            val view = layoutInflater.inflate(R.layout.item_ev, container, false)

            //estraggo i dati utili dalla risposta
            val connectionType = conn.optJSONObject("ConnectionType")
            val typeName = connectionType?.optString("Title") ?: "Connettore Standard" //nome del connettore
            val power = conn.optDouble("PowerKW", 0.0) //potenza erogata
            val quantity = conn.optInt("Quantity", 1) //quantita di quel connettore
            val statusType = conn.optJSONObject("StatusType")
            val isOperational = statusType?.optBoolean("IsOperational") ?: true //se il connettore è operativo o no

            //SVG del connettore
            val imgConnettore = view.findViewById<ImageView>(R.id.imgTipoPresa)
            imgConnettore.setImageResource(getIconaConnettore(typeName))

            //popolo l'activity
            val txtNome = view.findViewById<TextView>(R.id.lblNomePresa)
            txtNome.text = if (quantity > 1) "$typeName (x$quantity)" else typeName

            val txtPotenza = view.findViewById<TextView>(R.id.lblPotenza)
            txtPotenza.text = if (power > 0.0) "${power.toInt()} kW" else "Potenza N/D"

            val txtStato = view.findViewById<TextView>(R.id.lblStatoPresa)
            val pallino = view.findViewById<View>(R.id.viewStatoColore)

            //in base allo stato del connettore, cambio il colore del pallino e il testo
            if (isOperational) {
                txtStato.text = "OPERATIVA"
                val verde = Color.parseColor("#2E7D32")
                txtStato.setTextColor(verde)
                pallino.backgroundTintList =ColorStateList.valueOf(verde)
            } else {
                txtStato.text = "NON DISPONIBILE"
                val rosso = Color.RED
                txtStato.setTextColor(rosso)
                pallino.backgroundTintList =ColorStateList.valueOf(rosso)
            }

            container?.addView(view)
        }
    }

    //funzione di associamento delle icone ai tipi di connettore
    private fun getIconaConnettore(typeName: String?): Int {
        if (typeName == null) return R.drawable.ev_logo // Icona generica di default

        val name = typeName.lowercase()
        return when {
            name.contains("type 2") || name.contains("mennekes") -> R.drawable.type2_logo
            name.contains("ccs") || name.contains("combo") -> R.drawable.ccs_type2_logo
            name.contains("chademo") -> R.drawable.chademo_logo
            else -> R.drawable.ev_logo
        }
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

        findViewById<ImageButton>(R.id.btnSalva)?.setOnClickListener {
            salvaPreferito(idRicevuto)
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
