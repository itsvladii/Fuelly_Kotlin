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

    private fun observeViewModel(view: View) {
        viewModel.prezziBenzinaio.observe(viewLifecycleOwner) { (prezzi, medie) ->
            popolaListaCarburante(view, prezzi, medie, view.findViewById<MaterialSwitch>(R.id.switchServito)?.isChecked ?: false)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            view.findViewById<ProgressBar>(R.id.loadingPrezzi)?.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    //funzione di setup dei listeners nel fragment
    private fun setupListeners(view: View) {
        //switch servito-self (attivo=servito, disattivato=self)
        val switchServito = view.findViewById<MaterialSwitch>(R.id.switchServito)
        switchServito?.setOnCheckedChangeListener { btn, isChecked ->
            btn.text = if (isChecked) "Servito" else "Self-Service"
            //se sono su un benzinaio, ricavo i prezzi in base allo stato dello switch
            if (tipoRicevuto == "BENZINA") {
                val stazione = BenzinaiRepository.listaCompleta.find { it.id == idRicevuto.toInt() }
                viewModel.caricaPrezzi(stazione?.provincia ?: "", isChecked)
            }
        }

        //se sono su una colonnina EV, nascondo lo switch
        if (tipoRicevuto == "EV") {
            switchServito?.visibility = View.GONE
            view.findViewById<TextView>(R.id.lblSezione)?.text = "INFO RICARICA"
        }
    }

    //funzione di prima inizializzazione dei prezzi/prese
    private fun inizializzaDati(view: View) {
        when (tipoRicevuto) {
            "BENZINA" -> {
                val stazione = BenzinaiRepository.listaCompleta.find { it.id == idRicevuto.toInt() }
                viewModel.caricaPrezzi(stazione?.provincia ?: "", false)
            }
            "EV" -> {
                val colonnina = ColonnineRepository.listaCompleta.find { it.id.toLong() == idRicevuto }
                colonnina?.let { ricavaInfoEV(view, it) }
            }
        }
    }

    //funzione di popolamento della lista dei carburanti
    private fun popolaListaCarburante(view: View, arrayPrezzi: JSONArray, arrayMedie: JSONArray, soloServito: Boolean) {
        val container = view.findViewById<LinearLayout>(R.id.containerListaDettagli)
        container?.removeAllViews()
        var pompeTrovate = 0

        //se i prezzi trovati sono piu di 0, aggiorno l'header con la data dell'ultimo aggiornamento
        if (arrayPrezzi.length() > 0) {
            val dataGrezza = arrayPrezzi.getJSONObject(0).optString("dtComu")
            val dataFormattata = formattaDataAggiornamento(dataGrezza)
            
            val txtDistance = activity?.findViewById<TextView>(R.id.txtDistance)
            val testoDistanza = txtDistance?.text?.toString()?.split(" • ")?.get(0) ?: ""
            txtDistance?.text = "$testoDistanza • Aggiornato $dataFormattata"
        }

        //ciclo per ogni pompa del benzinaio e riempo la lista
        for (i in 0 until arrayPrezzi.length()) {
            val obj = arrayPrezzi.getJSONObject(i)
            val isSelfDb = obj.optString("isSelf") == "1"

            if (soloServito && isSelfDb) continue
            if (!soloServito && !isSelfDb) continue

            pompeTrovate++
            val itemView = layoutInflater.inflate(R.layout.item_carburante, container, false)

            val nome = obj.optString("descCarburante")
            val prezzo = obj.optDouble("prezzo")
            itemView.findViewById<TextView>(R.id.lblNomeCarburante).text = nome
            itemView.findViewById<TextView>(R.id.lblValorePrezzo).text = "${String.format("%.3f", prezzo)} €"

            val imgIcona = itemView.findViewById<ImageView>(R.id.imgIconaCarburante)
            imgIcona.setColorFilter(getColoreCarburante(nome).toColorInt())

            gestisciDifferenzaMedia(itemView, nome, prezzo, arrayMedie)
            container?.addView(itemView)
        }

        if (pompeTrovate == 0) mostrateMessaggioVuoto(container, soloServito)
    }

    //funzione di calcolo della differenza tra prezzo del benzinaio e la media regionale
    private fun gestisciDifferenzaMedia(view: View, nome: String, prezzo: Double, arrayMedie: JSONArray) {
        val freccia = view.findViewById<ImageView>(R.id.imgFrecciaMedia)
        val txtDiff = view.findViewById<TextView>(R.id.txtDifferenzaMedia)
        val categoria = getCategoriaPerMedia(nome)

        var mediaRegionale = 0.0
        for (j in 0 until arrayMedie.length()) {
            val m = arrayMedie.getJSONObject(j)
            if (m.optString("tipologia").equals(categoria, ignoreCase = true)) {
                mediaRegionale = m.optDouble("prezzo_medio")
                break
            }
        }

        if (mediaRegionale > 0) {
            val diff = prezzo - mediaRegionale
            val isPremium = !nome.equals(categoria, ignoreCase = true)

            if (diff > 0) {
                freccia.setImageResource(R.drawable.ic_up_arrow)
                freccia.setColorFilter(Color.RED)
                txtDiff.text = "+${String.format("%.3f", diff)}" + (if (isPremium) " (Prem.)" else "")
                txtDiff.setTextColor(Color.RED)
            } else {
                freccia.setImageResource(R.drawable.ic_downarrow)
                freccia.setColorFilter("#2E7D32".toColorInt())
                txtDiff.text = String.format("%.3f", diff)
                txtDiff.setTextColor("#2E7D32".toColorInt())
            }
        } else {
            freccia.visibility = View.GONE
            txtDiff.visibility = View.GONE
        }
    }

    //funzione per ricavare le info delle colonnine EV dal DB
    private fun ricavaInfoEV(view: View, ev: ColonninaEV) {
        val container = view.findViewById<LinearLayout>(R.id.containerListaDettagli)
        val loader = view.findViewById<ProgressBar>(R.id.loadingPrezzi)

        loader?.visibility = View.VISIBLE
        container?.removeAllViews()

        // Usiamo il JSON che abbiamo già salvato nell'oggetto ColonninaEV
        if (!ev.connettoriJson.isNullOrEmpty()) {
            try {
                val connections = JSONArray(ev.connettoriJson)
                popolaListaPreseOCM(view, connections)
            } catch (e: Exception) {
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
        val container = view.findViewById<LinearLayout>(R.id.containerListaDettagli)

        //ciclo per tutte le prese della colonnina
        for (i in 0 until connettori.length()) {
            val conn = connettori.getJSONObject(i)
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

            container?.addView(itemView)
        }
    }

    //funzione di associazione colore pompa al tipo di carburante
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

    //funzione di creazione delle "macro-categorie" per i tipi di combustibile
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

    private fun mostrateMessaggioVuoto(container: LinearLayout?, soloServito: Boolean) {
        context?.let { ctx ->
            val txt = TextView(ctx).apply {
                text = "Nessun prezzo disponibile per il ${if (soloServito) "servito" else "self"}."
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
                setPadding(0, 50, 0, 50)
                try {
                    typeface = ResourcesCompat.getFont(ctx, R.font.dm_sans_medium)
                } catch (e: Exception) {}
            }
            container?.addView(txt)
        }
    }

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