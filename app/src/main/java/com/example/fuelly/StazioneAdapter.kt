package com.example.fuelly

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.fuelly.repository.model.Benzinaio
import com.example.fuelly.repository.model.ColonninaEV
import org.json.JSONArray
import androidx.core.content.ContextCompat
import com.example.fuelly.utils.Utils

// Adapter per RecyclerView che mostra sia stazioni di servizio che colonnine EV
class StazioneAdapter(
    private var lista: List<Any>, //lista che può contenere sia Benzinaio che ColonninaEV
    private var userLat: Double? = null,
    private var userLon: Double? = null,
    private val onClick: (Any) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_STATION = 0
        private const val TYPE_FOOTER = 1
    }

    override fun getItemViewType(position: Int): Int {
        // 1. Controlla se la posizione dell'elemento che la RecyclerView sta per disegnare
        // corrisponde esattamente alla dimensione della lista dei dati.
        return if (position == lista.size) TYPE_FOOTER else TYPE_STATION
        // 2. restituisce il footer se siamo alla fine della lista, altrimenti restituisce la stazione
    }

    override fun getItemCount(): Int = lista.size + 1

    class StationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        //riferimenti alle view del layout item_benzinaio.xml
        val card: CardView = view.findViewById(R.id.stationCard)
        val txtName: TextView = view.findViewById(R.id.txtStationName)
        val txtCity: TextView = view.findViewById(R.id.txtStationCity)
        val txtAddress: TextView = view.findViewById(R.id.txtStationAddress)
        val txtInfo1: TextView = view.findViewById(R.id.txtBenzina)
        val txtInfo2: TextView = view.findViewById(R.id.txtDiesel)
        val imgLogo: ImageView = view.findViewById(R.id.imgPompa)
        val txtDistanza: TextView = view.findViewById(R.id.txtDistanza)
    }

    class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        // 1. Inizializza il LayoutInflater prendendo il contesto dal componente "parent" (la RecyclerView stessa).
        // Servirà per trasformare i file XML dei layout in oggetti View di Android.
        val inflater = LayoutInflater.from(parent.context)

        // 2. Controlla se il tipo di vista da creare è il Footer (piè di pagina)
        return if (viewType == TYPE_FOOTER) {

            // 3. Crea una View vuota (uno spazio bianco) direttamente da codice, senza usare un file XML
            val space = View(parent.context).apply {
                // Imposta le dimensioni di questa vista vuota:
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (130 * resources.displayMetrics.density).toInt()
                )
            }
            // 4. Inserisce la vista vuota appena creata dentro il contenitore specifico per il footer
            FooterViewHolder(space)
        } else {
            //entrambi caricano lo stesso file XML
            // 5. Caso alternativo: se è una normale stazione di servizio, "gonfia" (inflate) il layout XML dedicato
            // 'parent' serve per passare le giuste proprietà di layout, 'false' evita di attaccarlo subito alla RecyclerView
            val view = inflater.inflate(R.layout.item_benzinaio, parent, false)

            // 6. Inserisce la vista della stazione dentro il suo rispettivo contenitore (StationViewHolder)
            StationViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

        // 1. Sicurezza: esegue il codice solo se il ViewHolder è di tipo StationViewHolder e se l'indice è valido
        if (holder is StationViewHolder && position < lista.size) {
            val item = lista[position]
            var itemLat = 0.0
            var itemLon = 0.0

            //in base al tipo di item (Benzinaio o ColonninaEV) configuriamo la card in modo diverso
            if (item is Benzinaio) {
                itemLat = item.lat
                itemLon = item.lon

                // Recupera il contesto e i colori specifici per i distributori (Verde Scuro e Giallo Fluo)
                val context = holder.itemView.context
                val color = ContextCompat.getColor(context, R.color.fuelly_yellow_fluo)

                // Applica i colori della palette "Carburante" alla Card e ai testi
                holder.card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.fuelly_green_dark))
                holder.txtName.setTextColor(color)
                holder.txtInfo1.setTextColor(color)
                holder.txtInfo2.setTextColor(color)
                holder.txtDistanza.setTextColor(color)

                // Assegna i dati testuali: Nome del brand (es. Eni, IP) e posizione geografica
                holder.txtName.text = item.bandiera + " "
                holder.txtCity.text = "${item.comune} (${item.provincia})"
                holder.txtAddress.text = item.indirizzo
                holder.txtInfo1.text =
                    if (item.prezzoBenzina > 0) "B: ${String.format("%.3f", item.prezzoBenzina)}€" else "B: N.D."
                holder.txtInfo2.text =
                    if (item.prezzoDiesel > 0) "D: ${String.format("%.3f", item.prezzoDiesel)}€" else "D: N.D."
                holder.imgLogo.setImageResource(item.getLogoResource())

            } else if (item is ColonninaEV) {

                // Estrae le coordinate della colonnina elettrica
                itemLat = item.lat
                itemLon = item.lon

                // Recupera il contesto e i colori specifici per l'elettrico (Blu Notte e Ciano)
                val context = holder.itemView.context
                val color = ContextCompat.getColor(context, R.color.ev_cyan)

                //applica la palette dei colori alle componenti
                holder.card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.ev_dark_blue))
                holder.txtName.setTextColor(color)
                holder.txtInfo1.setTextColor(color)
                holder.txtInfo2.setTextColor(color)
                holder.txtDistanza.setTextColor(color)

                //setto i valori
                holder.txtName.text = item.titolo + " "
                holder.txtCity.text = item.indirizzo // Usiamo indirizzo come city se manca
                holder.txtAddress.text = "Stazione di ricarica elettrica"

                //calcolo numero di prese dal JSON come abbiamo fatto per la mappa
                var totalePrese = 0
                try {
                    val array = JSONArray(item.connettoriJson)
                    for (i in 0 until array.length()) {
                        totalePrese += array.getJSONObject(i).optInt("quantita", 1)
                    }
                } catch (e: Exception) {
                    totalePrese = item.numPunti
                }

                holder.txtInfo1.text = "${item.potenzaKW} kW"
                holder.txtInfo2.text = "$totalePrese prese"
                holder.imgLogo.setImageResource(item.getLogoResource())
            }

            //calcolo e visualizzazione distanza
            if (userLat != null && userLon != null && itemLat != 0.0) {
                val distanza = Utils.calcolaDistanza(userLat!!, userLon!!, itemLat, itemLon)
                holder.txtDistanza.text = if (distanza >= 1000) {
                    "${String.format("%.1f", distanza / 1000)} km"
                } else {
                    "${String.format("%.0f", distanza)} m"
                }
                holder.txtDistanza.visibility = View.VISIBLE
            } else {
                holder.txtDistanza.visibility = View.GONE
            }

            holder.itemView.setOnClickListener { onClick(item) }
        }
    }

    fun updateData(newLista: List<Any>, lat: Double? = null, lon: Double? = null) {
        lista = newLista
        if (lat != null && lon != null) {
            userLat = lat // 2. Salva la latitudine ricevuta nella variabile globale della classe
            userLon = lon // 3. Salva la longitudine ricevuta nella variabile globale della classe
        }

        // 4. Notifica alla RecyclerView che l'intero set di dati è cambiato.
        // Questo costringe la lista a ridisegnare immediatamente tutti gli elementi visibili a schermo,
        notifyDataSetChanged()
    }

}
