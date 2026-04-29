package com.example.fuelly

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.fuelly.classes.Benzinaio
import com.example.fuelly.classes.ColonninaEV
import com.example.fuelly.supabase.SupabaseInstance
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_prezzi, container, false)
        
        idRicevuto = activity?.intent?.getLongExtra("ID_ELEMENTO", -1L) ?: -1L
        tipoRicevuto = activity?.intent?.getStringExtra("TIPO_ELEMENTO")
        userLat = activity?.intent?.getDoubleExtra("USER_LAT", 0.0) ?: 0.0
        userLon = activity?.intent?.getDoubleExtra("USER_LON", 0.0) ?: 0.0

        setupListeners(view)
        inizializzaDati(view)
        
        return view
    }

    private fun setupListeners(view: View) {
        val switchServito = view.findViewById<Switch>(R.id.switchServito)
        switchServito?.setOnCheckedChangeListener { btn, isChecked ->
            btn.text = if (isChecked) "Servito" else "Self-Service"
            if (tipoRicevuto == "BENZINA") {
                ricavaPrezziBenzinaio(view, idRicevuto.toInt(), isChecked)
            }
        }
        
        if (tipoRicevuto == "EV") {
            switchServito?.visibility = View.GONE
            view.findViewById<TextView>(R.id.lblSezione)?.text = "INFO RICARICA"
        }
    }

    private fun inizializzaDati(view: View) {
        when (tipoRicevuto) {
            "BENZINA" -> ricavaPrezziBenzinaio(view, idRicevuto.toInt(), false)
            "EV" -> {
                val colonnina = ColonninaEV.listaVicini.find { it.id.toLong() == idRicevuto }
                colonnina?.let { ricavaInfoEV(view, it) }
            }
        }
    }

    private fun ricavaPrezziBenzinaio(view: View, idImpianto: Int, soloServito: Boolean) {
        val loader = view.findViewById<ProgressBar>(R.id.loadingPrezzi)
        val stazione = Benzinaio.listaVicini.find { it.id == idImpianto }
        val siglaProvincia = stazione?.provincia ?: ""

        lifecycleScope.launch {
            loader?.visibility = View.VISIBLE
            try {
                val defMapping = async { SupabaseInstance.client.from("province_regioni").select{ filter{ eq("provincia", siglaProvincia) } } }
                val defPrezzi = async { SupabaseInstance.client.from("prezzi").select{ filter{ eq("idImpianto", idImpianto) } } }

                val resMapping = defMapping.await()
                val resPrezzi = defPrezzi.await()

                val nomeRegione = JSONArray(resMapping.data).optJSONObject(0)?.optString("regione") ?: ""
                val resMedie = SupabaseInstance.client.from("media_regionale").select {
                    filter {
                        eq("regione", nomeRegione)
                        eq("isSelf", if (soloServito) "0" else "1")
                    }
                }

                val arrayPrezzi = JSONArray(resPrezzi.data)
                val arrayMedie = JSONArray(resMedie.data)

                activity?.runOnUiThread {
                    popolaListaCarburante(view, arrayPrezzi, arrayMedie, soloServito)
                    loader?.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("Fuelly", "Errore caricamento dati: ${e.message}")
                loader?.visibility = View.GONE
            }
        }
    }

    private fun popolaListaCarburante(view: View, arrayPrezzi: JSONArray, arrayMedie: JSONArray, soloServito: Boolean) {
        val container = view.findViewById<LinearLayout>(R.id.containerListaDettagli)
        container?.removeAllViews()
        var pompeTrovate = 0

        // Aggiornamento data ultimo aggiornamento nel header dell'activity
        if (arrayPrezzi.length() > 0) {
            val dataGrezza = arrayPrezzi.getJSONObject(0).optString("dtComu")
            val dataFormattata = formattaDataAggiornamento(dataGrezza)
            
            val txtDistance = activity?.findViewById<TextView>(R.id.txtDistance)
            val testoDistanza = txtDistance?.text?.toString()?.split(" • ")?.get(0) ?: ""
            txtDistance?.text = "$testoDistanza • Aggiornato $dataFormattata"
        }

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
            freccia.visibility = View.GONE
            txtDiff.visibility = View.GONE
        }
    }

    private fun ricavaInfoEV(view: View, ev: ColonninaEV) {
        val container = view.findViewById<LinearLayout>(R.id.containerListaDettagli)
        val loader = view.findViewById<ProgressBar>(R.id.loadingPrezzi)
        val client = okhttp3.OkHttpClient()
        val apiKey = BuildConfig.EV_API_KEY

        val url = "https://api.openchargemap.io/v3/poi/?output=json" +
                "&latitude=${ev.lat}&longitude=${ev.lon}" +
                "&distance=0.1&distanceunit=km&maxresults=1&key=$apiKey"

        val request = okhttp3.Request.Builder().url(url).build()
        loader?.visibility = View.VISIBLE

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                activity?.runOnUiThread { loader?.visibility = View.GONE }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!response.isSuccessful) return
                    val body = response.body.string()
                    val jsonArray = JSONArray(body)
                    if (jsonArray.length() > 0) {
                        val poi = jsonArray.getJSONObject(0)
                        val connections = poi.getJSONArray("Connections")

                        activity?.runOnUiThread {
                            container?.removeAllViews()
                            popolaListaPreseOCM(view, connections)
                            loader?.visibility = View.GONE
                        }
                    }
                }
            }
        })
    }

    private fun popolaListaPreseOCM(view: View, connections: JSONArray) {
        val container = view.findViewById<LinearLayout>(R.id.containerListaDettagli)
        for (i in 0 until connections.length()) {
            val conn = connections.getJSONObject(i)
            val itemView = layoutInflater.inflate(R.layout.item_ev, container, false)

            val connectionType = conn.optJSONObject("ConnectionType")
            val typeName = connectionType?.optString("Title") ?: "Connettore Standard"
            val power = conn.optDouble("PowerKW", 0.0)
            val quantity = conn.optInt("Quantity", 1)
            val statusType = conn.optJSONObject("StatusType")
            val isOperational = statusType?.optBoolean("IsOperational") ?: true

            itemView.findViewById<ImageView>(R.id.imgTipoPresa).setImageResource(getIconaConnettore(typeName))
            val txtNome = itemView.findViewById<TextView>(R.id.lblNomePresa)
            txtNome.text = if (quantity > 1) "$typeName (x$quantity)" else typeName

            val txtPotenza = itemView.findViewById<TextView>(R.id.lblPotenza)
            txtPotenza.text = if (power > 0.0) "${power.toInt()} kW" else "Potenza N/D"

            val txtStato = itemView.findViewById<TextView>(R.id.lblStatoPresa)
            val pallino = itemView.findViewById<View>(R.id.viewStatoColore)

            if (isOperational) {
                txtStato.text = "OPERATIVA"
                val verde = Color.parseColor("#2E7D32")
                txtStato.setTextColor(verde)
                pallino.backgroundTintList = ColorStateList.valueOf(verde)
            } else {
                txtStato.text = "NON DISPONIBILE"
                val rosso = Color.RED
                txtStato.setTextColor(rosso)
                pallino.backgroundTintList = ColorStateList.valueOf(rosso)
            }
            container?.addView(itemView)
        }
    }

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

    private fun getIconaConnettore(typeName: String?): Int {
        if (typeName == null) return R.drawable.ev_logo
        val name = typeName.lowercase()
        return when {
            name.contains("type 2") || name.contains("mennekes") -> R.drawable.type2_logo
            name.contains("ccs") || name.contains("combo") -> R.drawable.ccs_type2_logo
            name.contains("chademo") -> R.drawable.chademo_logo
            else -> R.drawable.ev_logo
        }
    }

    private fun mostrateMessaggioVuoto(container: LinearLayout?, soloServito: Boolean) {
        context?.let { ctx ->
            val txt = TextView(ctx).apply {
                text = "Nessun prezzo disponibile per il ${if (soloServito) "servito" else "self"}."
                setTextColor(Color.GRAY)
                gravity = android.view.Gravity.CENTER
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