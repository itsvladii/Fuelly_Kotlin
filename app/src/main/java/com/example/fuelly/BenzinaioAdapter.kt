package com.example.fuelly

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fuelly.classes.Benzinaio

// Nota: Ora l'Adapter estende <RecyclerView.ViewHolder> generico
class BenzinaioAdapter(
    private var lista: List<Benzinaio>,
    private val onClick: (Benzinaio) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_ITEM = 0
    private val TYPE_FOOTER = 1

    // 1. ViewHolder per gli elementi normali
    class BenzinaioViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtName: TextView = view.findViewById(R.id.txtStationName)
        val txtStationCity: TextView = view.findViewById(R.id.txtStationCity)
        val txtStationAddress: TextView = view.findViewById(R.id.txtStationAddress)
        val txtBenzina: TextView = view.findViewById(R.id.txtBenzina)
        val txtDiesel: TextView = view.findViewById(R.id.txtDiesel)
        val imgLogo: ImageView = view.findViewById(R.id.imgPompa)
    }

    // 2. ViewHolder per il footer (spazio vuoto)
    class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun getItemViewType(position: Int): Int {
        return if (position == lista.size) TYPE_FOOTER else TYPE_ITEM
    }

    override fun getItemCount(): Int {
        return lista.size + 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_FOOTER) {
            val space = View(parent.context)
            // Definiamo l'altezza (es. 100dp convertiti in pixel)
            val heightInPx = (130 * parent.context.resources.displayMetrics.density).toInt()

            // Usiamo ViewGroup.LayoutParams.MATCH_PARENT
            space.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                heightInPx
            )
            FooterViewHolder(space)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_benzinaio, parent, false)
            BenzinaioViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // Fondamentale: eseguiamo il binding solo se NON siamo sul footer
        if (holder is BenzinaioViewHolder && position < lista.size) {
            val b = lista[position]

            holder.txtName.text = b.bandiera + " "
            holder.txtStationCity.text = "${b.comune} (${b.provincia})"
            holder.txtStationCity.isSelected = true
            holder.txtStationAddress.text = b.indirizzo
            holder.txtStationAddress.isSelected = true

            holder.txtBenzina.text = if (b.prezzoBenzina > 0) "Benzina: ${String.format("%.3f", b.prezzoBenzina)}€" else "Benzina: N.D."
            holder.txtDiesel.text = if (b.prezzoDiesel > 0) "Diesel: ${String.format("%.3f", b.prezzoDiesel)}€" else "Diesel: N.D."

            holder.imgLogo.setImageResource(b.getLogoResource())
            holder.itemView.setOnClickListener { onClick(b) }
        }
    }

    fun updateData(nuovaLista: List<Benzinaio>) {
        lista = nuovaLista
        notifyDataSetChanged()
    }
}