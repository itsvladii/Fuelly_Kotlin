package com.example.fuelly.ui.dettagli

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.fuelly.R
import com.example.fuelly.repository.model.Benzinaio
import com.example.fuelly.repository.model.ColonninaEV
import com.example.fuelly.repository.data.BenzinaiRepository
import com.example.fuelly.repository.data.ColonnineRepository
import com.example.fuelly.repository.supabase.SupabaseInstance
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

class PrezziFragment : Fragment() {

    private var idRicevuto: Long = -1L
    private var tipoRicevuto: String? = null
    private var userLat: Double = 0.0
    private var userLon: Double = 0.0
    private val viewModel: DettagliViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        //inflate dell'interfaccia del fagment
        val view = inflater.inflate(R.layout.fragment_prezzi, container, false)

        //ricavo le info dall'intent dell'activity
        idRicevuto = activity?.intent?.getLongExtra("ID_ELEMENTO", -1L) ?: -1L
        tipoRicevuto = activity?.intent?.getStringExtra("TIPO_ELEMENTO")
        userLat = activity?.intent?.getDoubleExtra("USER_LAT", 0.0) ?: 0.0
        userLon = activity?.intent?.getDoubleExtra("USER_LON", 0.0) ?: 0.0

        setupListeners(view)
        observeViewModel(view)
        inizializzaDati(view)
        
        return view
    }

    //funzione per l'osservazione dei dati dal viewModel
    private fun observeViewModel(view: View) {
        //se cambia prezzi e medie di prezzo, aggiorno la lista dei carburanti
        viewModel.prezziBenzinaio.observe(viewLifecycleOwner) { (prezzi, medie) ->
            popolaListaCarburante(view, prezzi, medie, view.findViewById<MaterialSwitch>(R.id.switchServito)?.isChecked ?: false)
        }
        //se cambia lo stato di loading, cambio la visibilità della progressbar
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            view.findViewById<ProgressBar>(R.id.loadingPrezzi)?.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    //funzione di setup dei listeners nel fragment
    private fun setupListeners(view: View) {
        //switch servito-self (attivo=servito, disattivato=self)
        val switchServito = view.findViewById<MaterialSwitch>(R.id.switchServito)

        //se clicco sullo switch
        switchServito?.setOnCheckedChangeListener { btn, isChecked ->

            //setto il testo a servito o self-service
            btn.text = if (isChecked) "Servito" else "Self-Service"

            //se sono su un benzinaio, ricavo i prezzi in base allo stato dello switch
            if (tipoRicevuto == "BENZINA") {
                //ricavo il benzinaio dalla lista completa
                val stazione = BenzinaiRepository.listaCompleta.find { it.id == idRicevuto.toInt() }
                //richiedo i prezzi al viewModel
                viewModel.caricaPrezzi(stazione?.provincia ?: "", isChecked)
            }
        }

        //se sono su una colonnina EV, nascondo lo switch
        if (tipoRicevuto == "EV") {

            switchServito?.visibility = View.GONE
            //setto il testo della sezione
            view.findViewById<TextView>(R.id.lblSezione)?.text = "INFO RICARICA"
        }
    }

    //funzione di prima inizializzazione dei prezzi/prese
    private fun inizializzaDati(view: View) {
        when (tipoRicevuto) {
            "BENZINA" -> {
                //recupero i dati
                val stazione = BenzinaiRepository.listaCompleta.find { it.id == idRicevuto.toInt() }

                //richiamo il metodo passando la provincia e il booleano per il servito
                viewModel.caricaPrezzi(stazione?.provincia ?: "", false)
            }
            "EV" -> {
                //recupero i dati della specifica colonnina
                val colonnina = ColonnineRepository.listaCompleta.find { it.id.toLong() == idRicevuto }

                //richiamo il metodo per ricavare le info
                colonnina?.let { ricavaInfoEV(view, it) }
            }
        }
    }

    //funzione di popolamento della lista dei carburanti per la specifica stazione
    private fun popolaListaCarburante(view: View, arrayPrezzi: JSONArray, arrayMedie: JSONArray, soloServito: Boolean) {

        //container (LINEAR LAYOUT)
        val container = view.findViewById<LinearLayout>(R.id.containerListaDettagli)

        //svuoto il container
        container?.removeAllViews()

        var pompeTrovate = 0

        //"HEADER" DELLA SCHERMATA DOVE VISUALIZZO NOME, VIA, DISTANZA E DATA DI AGGIORNAMENTO
        //se i prezzi trovati sono piu di 0, aggiorno l'header con la data dell'ultimo aggiornamento
        if (arrayPrezzi.length() > 0) {

            //DATA DELL'ULTIMO AGGIORNAMENTO
            val dataGrezza = arrayPrezzi.getJSONObject(0).optString("dtComu")
            //DATA FORMATTATA
            val dataFormattata = formattaDataAggiornamento(dataGrezza)

            //recupero dall'interfaccia l'id della view per la text della distanza
            val txtDistance = activity?.findViewById<TextView>(R.id.txtDistance)

            //DISTANZA
            val testoDistanza = txtDistance?.text?.toString()?.split(" • ")?.get(0) ?: ""
            txtDistance?.text = "$testoDistanza • Aggiornato $dataFormattata"
        }
        //NELLA RECYCLER VIEW PER OGNI POMPA DELLO SPECIFICO BENZINAIO
        //ciclo per ogni pompa del benzinaio e riempo la lista
        for (i in 0 until arrayPrezzi.length()) {

            //prendo il singolo oggetto json
            val obj = arrayPrezzi.getJSONObject(i)

            //variabile per il selfService (0=no, 1=si)
            val isSelfDb = obj.optString("isSelf") == "1"

            /*
            Se l'utente sta guardando il Servito ma la riga del database è Self,
            salta alla riga successiva (continue).

            Se l'utente sta guardando il Self ma la riga del database è Servito,
            viene scartata allo stesso modo.
             */
            if (soloServito && isSelfDb) continue
            if (!soloServito && !isSelfDb) continue

            pompeTrovate++

            /*
            Per ogni carburante che supera il filtro, la funzione esegue l'inflate del file di layout item_carburante.xml.
            Significa che prende quel piccolo pezzo di codice XML e lo trasforma in un oggetto visivo reale in memoria.
             */
            val itemView = layoutInflater.inflate(R.layout.item_carburante, container, false)

            //assegno alle variabili la proprietà descCarburante e prezzo dell'oggetto Json
            val nome = obj.optString("descCarburante")
            val prezzo = obj.optDouble("prezzo")

            //TIPO CARURANTE E PREZZO (ES: BENZINA 1,88€)
            itemView.findViewById<TextView>(R.id.lblNomeCarburante).text = nome
            itemView.findViewById<TextView>(R.id.lblValorePrezzo).text = "${String.format("%.3f", prezzo)} €"

            //ICONA DELLA POMPA (COLORATA IN MANIERA DIFFERENTE IN BASE ALLA TIPOLOGIA DI CARBURANTE)
            val imgIcona = itemView.findViewById<ImageView>(R.id.imgIconaCarburante)
            imgIcona.setColorFilter(getColoreCarburante(nome).toColorInt())

            //chiamo il metodo che controlla il prezzo del benzinaio con quello della media
            gestisciDifferenzaMedia(itemView, nome, prezzo, arrayMedie)

            /*
            1) prende il container (un LinearLayout verticale vuoto nel tuo layout principale).
            2) Per ogni elemento valido nel JSONArray, "clona" il file item_carburante.xml.
            3) Riempie i testi, colora l'icona e infine lo inserisce dentro il contenitore con container.addView(itemView), spingendo verso il basso i successivi.
            4) Aggiungo al linearlayout l'elemento itemView che rappresenta una singola riga della lista (un tipo di carburante)*/
            container?.addView(itemView)
        }

        if (pompeTrovate == 0) mostrateMessaggioVuoto(container, soloServito)
    }

    //funzione di calcolo della differenza tra prezzo del benzinaio e la media regionale
    private fun gestisciDifferenzaMedia(view: View, nome: String, prezzo: Double, arrayMedie: JSONArray) {

        //recupero gli elementi dall'interfaccia
        val freccia = view.findViewById<ImageView>(R.id.imgFrecciaMedia)
        val txtDiff = view.findViewById<TextView>(R.id.txtDifferenzaMedia)
        // 2. Converte il nome commerciale (es. "Hi-Q Diesel" o "V-Power") in una categoria standard (es. "diesel")
        val categoria = getCategoriaPerMedia(nome) //richiamo il metodo per ricavare la categoria

        var mediaRegionale = 0.0

        // 3. Cicla l'array delle medie regionali per trovare il prezzo di riferimento corretto
        for (j in 0 until arrayMedie.length()) {

            //prendo il singolo oggetto json
            val m = arrayMedie.getJSONObject(j)

            // 4. Se la tipologia nel JSON coincide con la categoria che stiamo cercando (es. "benzina" == "benzina"),
            // salvo il prezzo medio regionale e interrompo il ciclo
            if (m.optString("tipologia").equals(categoria, ignoreCase = true)) {

                //salvo il prezzo
                mediaRegionale = m.optDouble("prezzo_medio")
                break
            }
        }
        // 5. Se è stata trovata una media regionale valida (maggiore di zero)
        if (mediaRegionale > 0) {

            // se il risultato è positivo, il distributore è più caro della media
            val diff = prezzo - mediaRegionale
            // 6. Controlla se il carburante è di tipo "Premium" (speciali/più costosi).
            // Se il nome commerciale non coincide esattamente con la categoria base, viene considerato Premium.
            val isPremium = !nome.equals(categoria, ignoreCase = true)
            //il distributore costa di più rispetto la media
            if (diff > 0) {
                freccia.setImageResource(R.drawable.ic_up_arrow) // Imposta la freccia verso l'alto
                freccia.setColorFilter(Color.RED) //colore rosso
                txtDiff.text = "+${String.format("%.3f", diff)}" + (if (isPremium) " (Prem.)" else "") //aggiunge il testo prem.
                txtDiff.setTextColor(Color.RED) //backgroud rosso
            } else {
                freccia.setImageResource(R.drawable.ic_downarrow) //freccia in basso
                freccia.setColorFilter("#2E7D32".toColorInt()) //codice del colore
                txtDiff.text = String.format("%.3f", diff) //aggiunge il testo diff del prezzo
                txtDiff.setTextColor("#2E7D32".toColorInt())
            }
        } else {
            freccia.visibility = View.GONE
            txtDiff.visibility = View.GONE
        }
    }

    //funzione per ricavare le info delle colonnine EV dal DB
    private fun ricavaInfoEV(view: View, ev: ColonninaEV) {

        //recupero il container della lista (Linear Layout)
        val container = view.findViewById<LinearLayout>(R.id.containerListaDettagli)
        //recupero la progressbar
        val loader = view.findViewById<ProgressBar>(R.id.loadingPrezzi)
        //rendo la barra visibile
        loader?.visibility = View.VISIBLE
        //ripulisco il linearLayout
        container?.removeAllViews()

        // Usiamo il JSON che abbiamo già salvato nell'oggetto ColonninaEV
        if (!ev.connettoriJson.isNullOrEmpty()) {
            try {
                //assegno a collection un JsonArray( con parametro ev.connettoriJson)
                val connections = JSONArray(ev.connettoriJson)

                //parse della lista di connettori EV
                popolaListaPreseOCM(view, connections)


            } catch (e: Exception) {
                // Gestisci l'eccezione in caso di errore nel parsing
                Log.e("Fuelly", "Errore parsing connettori locali: ${e.message}")
                mostrateMessaggioVuoto(container, false)
            }
        } else {
            mostrateMessaggioVuoto(container, false)
        }

        loader?.visibility = View.GONE
    }

    //funzione di popolamento della lista di prese per le colonnine EV
    private fun popolaListaPreseOCM(view: View, connettori: JSONArray) {

        //LINEAR LAYOUT
        val container = view.findViewById<LinearLayout>(R.id.containerListaDettagli)

        //ciclo per tutte le prese della colonnina
        for (i in 0 until connettori.length()) {

            //singolo oggetto Json
            val conn = connettori.getJSONObject(i)

            //SINGOLO ELEMENTO VISIVO
            val itemView = layoutInflater.inflate(R.layout.item_ev, container, false)

            //ricavo gli elementi che mi servono per popolare la lista
            val typeName = conn.optString("tipo", "Connettore Standard")
            val power = conn.optDouble("potenza", 0.0)
            val qta = conn.optInt("quantita", 1)
            val statoGrezzo = conn.optString("stato", "Unknown")

            //imposto l'icona del connettore
            itemView.findViewById<ImageView>(R.id.imgTipoPresa).setImageResource(
                ColonninaEV.getIconaConnettore(typeName)
            )

            //setup del numero di connettori e della potenza
            itemView.findViewById<TextView>(R.id.lblNomePresa).text = typeName

            val txtPotenza = itemView.findViewById<TextView>(R.id.lblPotenza)
            val infoTecnica = "${power.toInt()} kW • x$qta"

            txtPotenza.text = infoTecnica

            val txtStato = itemView.findViewById<TextView>(R.id.lblStatoPresa)
            val pallino = itemView.findViewById<View>(R.id.viewStatoColore)


            when {
                //STATO OPERATIVO
                statoGrezzo.equals("Operational", ignoreCase = true) -> {
                    txtStato.text = "OPERATIVA"
                    val colore = Color.parseColor("#2E7D32") // Verde scuro
                    txtStato.setTextColor(colore)
                    pallino.backgroundTintList = ColorStateList.valueOf(colore)
                }

                //STATI DI ATTESA O PARZIALI
                statoGrezzo.contains("Partly", ignoreCase = true) ||
                        statoGrezzo.contains("Planned", ignoreCase = true) ||
                        statoGrezzo.contains("Unknown", ignoreCase = true) -> {
                    txtStato.text = if (statoGrezzo.contains("Planned")) "IN ARRIVO" else "STATO IGNOTO"
                    val colore = Color.parseColor("#FBC02D") // Giallo/Ambra
                    txtStato.setTextColor(colore)
                    pallino.backgroundTintList = ColorStateList.valueOf(colore)
                }

                //TEMPORANEAMENTE NON DISPONIBILE
                statoGrezzo.contains("Temporarily", ignoreCase = true) -> {
                    txtStato.text = "TEMP. NON DISPONIBILE"
                    val colore = Color.parseColor("#F57C00") // Arancione
                    txtStato.setTextColor(colore)
                    pallino.backgroundTintList = ColorStateList.valueOf(colore)
                }

                //NON OPERATIVO O RIMOSSO
                else -> {
                    txtStato.text = "NON OPERATIVA"
                    val colore = Color.RED
                    txtStato.setTextColor(colore)
                    pallino.backgroundTintList = ColorStateList.valueOf(colore)
                }
            }

            //aggiungo al linearlayout l'elemento itemView che rappresenta una singola riga della lista (un tipo di connettore)
            container?.addView(itemView)
        }
    }

    //funzione di associazione colore pompa al tipo di carburante
    private fun getColoreCarburante(nome: String): String {

        val n = nome.lowercase()
        //in base al tipo di carburante associo un colore differente
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

    //funzione di creazione delle "macro-categorie" per i tipi di combustibile
    private fun getCategoriaPerMedia(nome: String): String {

        val n = nome.lowercase()

        //in base al tipo di carburante associo una categoria differente
        return when {
            n.contains("benzina") || n.contains("v-power") || n.contains("super") -> "Benzina"
            n.contains("diesel") || n.contains("gasolio") || n.contains("hvo") -> "Gasolio"
            n.contains("gpl") -> "GPL"
            n.contains("metano") -> "Metano"
            else -> ""
        }
    }

    private fun mostrateMessaggioVuoto(container: LinearLayout?, soloServito: Boolean) {

        //context?.let serve per ottenere il contesto della activity
        context?.let { ctx ->
            //alla variabile txt associo un TextView(con parametro ctx)
            val txt = TextView(ctx).apply {
                //proprietà text
                text = "Nessun prezzo disponibile per il ${if (soloServito) "servito" else "self"}."
                //setto il colore
                setTextColor(Color.GRAY)
                //proprietà gravity
                gravity = Gravity.CENTER
                //setto il padding
                setPadding(0, 50, 0, 50)
                try {
                    typeface = ResourcesCompat.getFont(ctx, R.font.dm_sans_medium)
                } catch (e: Exception) {}
            }
            //inserisco l'item nel LinearLayout
            container?.addView(txt)
        }
    }


    //funzione per visualizzare quando una stazione è stata aggiornata
    private fun formattaDataAggiornamento(dataString: String): String {


        return try {

            //formato della data
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ITALY)
            val data = sdf.parse(dataString) ?: return "Recentemente"
            val adesso = Calendar.getInstance()
            val dataCom = Calendar.getInstance().apply { time = data }

            //controllo sul giorno corrente
            if (adesso.get(Calendar.DAY_OF_YEAR) == dataCom.get(Calendar.DAY_OF_YEAR)) {
                "Oggi alle ${SimpleDateFormat("HH:mm", Locale.ITALY).format(data)}"
            } else {
                //formatto
                SimpleDateFormat("dd/MM", Locale.ITALY).format(data)
            }
        } catch (e: Exception) { "Recentemente" }
    }
}