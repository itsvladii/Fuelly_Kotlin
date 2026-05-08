package com.example.fuelly

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fuelly.classes.Benzinaio

// Adapter per la RecyclerView che mostra i benzinai salvati
class BenzinaioAdapter(
    private var lista: List<Benzinaio>,
    private val onClick: (Benzinaio) -> Unit
) : RecyclerView.Adapter<BenzinaioAdapter.ViewHolder>() {

    // ViewHolder per ogni elemento della lista
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtName: TextView = view.findViewById(R.id.txtStationName)
        val txtStationCity: TextView = view.findViewById(R.id.txtStationCity)
        val txtStationAddress: TextView = view.findViewById(R.id.txtStationAddress)
        val txtBenzina: TextView = view.findViewById(R.id.txtBenzina)
        val txtDiesel: TextView = view.findViewById(R.id.txtDiesel)
        val imgLogo: ImageView = view.findViewById(R.id.imgPompa)
    }

    // Crea un nuovo ViewHolder per ogni elemento della lista di benzinai
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_benzinaio, parent, false)
        return ViewHolder(view)
    }

    // Riempie i dati nel ViewHolder per ogni elemento della lista di benzinai
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val b = lista[position]
        holder.txtName.text = b.bandiera+" "

        holder.txtStationCity.text = b.comune + " (" + b.provincia + ")"
        holder.txtStationCity.isSelected = true

        holder.txtStationAddress.text = b.indirizzo
        holder.txtStationAddress.isSelected = true
        
        holder.txtBenzina.text = if (b.prezzoBenzina > 0) "Benzina: ${String.format("%.3f", b.prezzoBenzina)}€" else "Benzina: N.D."
        holder.txtDiesel.text = if (b.prezzoDiesel > 0) "Diesel: ${String.format("%.3f", b.prezzoDiesel)}€" else "Diesel: N.D."
        
        holder.imgLogo.setImageResource(b.getLogoResource())

        holder.itemView.setOnClickListener { onClick(b) }
    }

    // Restituisce il numero di elementi nella lista di benzinai
    override fun getItemCount() = lista.size

    // Aggiorna i dati nella lista e notifica l'adapter della modifica
    fun updateData(nuovaLista: List<Benzinaio>) {
        lista = nuovaLista
        notifyDataSetChanged()
    }
}