package com.example.fuelly

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fuelly.classes.Recensione

class RecensioniAdapter(private var lista: MutableList<Recensione>) :
    RecyclerView.Adapter<RecensioniAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nome: TextView = view.findViewById(R.id.lblNomeUtente)
        val testo: TextView = view.findViewById(R.id.lblTestoRecensione)
        val rating: RatingBar = view.findViewById(R.id.ratingSingolo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_recensione, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val r = lista[position]
        holder.nome.text = r.nome
        holder.testo.text = r.descRecensione
        holder.rating.rating = r.rating.toFloat()

    }

    override fun getItemCount() = lista.size

    // Funzione per aggiornare i dati dall'esterno
    fun updateData(nuovaLista: List<Recensione>) {
        lista.clear()
        lista.addAll(nuovaLista)
        notifyDataSetChanged()
    }
}