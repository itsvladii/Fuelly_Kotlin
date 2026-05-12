package com.example.fuelly

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.example.fuelly.classes.Benzinaio
import com.example.fuelly.classes.ColonninaEV
import org.json.JSONArray

class StazioneAdapter(
    private var items: List<Any>,
    private val onClick: (Any) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_STATION = 0 // Usiamo lo stesso tipo per entrambi
        private const val TYPE_FOOTER = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == items.size) TYPE_FOOTER else TYPE_STATION
    }

    override fun getItemCount(): Int = items.size + 1

    class StationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.stationCard)
        val txtName: TextView = view.findViewById(R.id.txtStationName)
        val txtCity: TextView = view.findViewById(R.id.txtStationCity)
        val txtAddress: TextView = view.findViewById(R.id.txtStationAddress)
        val txtInfo1: TextView = view.findViewById(R.id.txtBenzina) // Ricicliamo per info EV
        val txtInfo2: TextView = view.findViewById(R.id.txtDiesel)  // Ricicliamo per info EV
        val imgLogo: ImageView = view.findViewById(R.id.imgPompa)
    }

    class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_FOOTER) {
            val space = View(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (130 * resources.displayMetrics.density).toInt()
                )
            }
            FooterViewHolder(space)
        } else {
            // Entrambi caricano lo stesso file XML
            val view = inflater.inflate(R.layout.item_benzinaio, parent, false)
            StationViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is StationViewHolder && position < items.size) {
            val item = items[position]

            if (item is Benzinaio) {
                // --- CONFIGURAZIONE FUEL (Giallo) ---
                holder.card.setCardBackgroundColor("#0B3D2E".toColorInt())
                holder.txtName.setTextColor("#DFFF00".toColorInt())
                holder.txtInfo1.setTextColor("#DFFF00".toColorInt())
                holder.txtInfo2.setTextColor("#DFFF00".toColorInt())

                holder.txtName.text = item.bandiera
                holder.txtCity.text = "${item.comune} (${item.provincia})"
                holder.txtAddress.text = item.indirizzo
                holder.txtInfo1.text = if (item.prezzoBenzina > 0) "B: ${String.format("%.3f", item.prezzoBenzina)}€" else "B: N.D."
                holder.txtInfo2.text = if (item.prezzoDiesel > 0) "D: ${String.format("%.3f", item.prezzoDiesel)}€" else "D: N.D."
                holder.imgLogo.setImageResource(item.getLogoResource())

            } else if (item is ColonninaEV) {
                // --- CONFIGURAZIONE EV (Ciano) ---
                holder.card.setCardBackgroundColor("#0B101E".toColorInt())
                holder.txtName.setTextColor("#00FFC2".toColorInt())
                holder.txtInfo1.setTextColor("#00FFC2".toColorInt())
                holder.txtInfo2.setTextColor("#00FFC2".toColorInt())

                holder.txtName.text = item.titolo
                holder.txtCity.text = item.indirizzo // Usiamo indirizzo come city se manca
                holder.txtAddress.text = "Stazione di ricarica elettrica"

                // Calcolo prese dal JSON come abbiamo fatto per la mappa
                var totalePrese = 0
                try {
                    val array = JSONArray(item.connettoriJson)
                    for (i in 0 until array.length()) {
                        totalePrese += array.getJSONObject(i).optInt("quantita", 1)
                    }
                } catch (e: Exception) { totalePrese = item.numPunti }

                holder.txtInfo1.text = "${item.potenzaKW} kW"
                holder.txtInfo2.text = "$totalePrese prese"
                holder.imgLogo.setImageResource(item.getLogoResource())
            }

            holder.itemView.setOnClickListener { onClick(item) }
        }
    }

    fun updateData(newItems: List<Any>) {
        items = newItems
        notifyDataSetChanged()
    }
}