package com.example.fuelly

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fuelly.classes.Recensione
import com.example.fuelly.supabase.SupabaseInstance
import io.github.jan.supabase.auth.auth

class RecensioniAdapter(private var lista: MutableList<Recensione>, private val onEliminaClick: (Recensione) -> Unit) :
    RecyclerView.Adapter<RecensioniAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nome: TextView = view.findViewById(R.id.lblNomeUtente)
        val testo: TextView = view.findViewById(R.id.lblTestoRecensione)
        val rating: RatingBar = view.findViewById(R.id.ratingSingolo)
        val btnElimina: ImageButton = view.findViewById(R.id.btnEliminaRecensione)
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

        val idUtenteLoggato = SupabaseInstance.client.auth.currentSessionOrNull()?.user?.id

        if (r.idUtente == idUtenteLoggato) {
            holder.btnElimina.visibility = View.VISIBLE
            holder.btnElimina.setOnClickListener {
                //alert di conferma prima di eliminare la recensione
                val dialog = AlertDialog.Builder(holder.itemView.context)
                    .setTitle("Elimina Recensione")
                    .setMessage("Sei sicuro di voler eliminare definitivamente questo commento?")
                    .setPositiveButton("Elimina") { _, _ ->
                        onEliminaClick(r)
                    }
                    .setNegativeButton("Annulla", null)
                    .create()

                //impostazione dei colori dei pulsanti dell'AlertDialog
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    ?.setTextColor(android.graphics.Color.parseColor("#CC3838"))
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    ?.setTextColor(android.graphics.Color.parseColor("#666666"))

                dialog.show()

            }
        } else {
            holder.btnElimina.visibility = View.GONE
        }

    }

    override fun getItemCount() = lista.size

    // Funzione per aggiornare i dati dall'esterno
    fun updateData(nuovaLista: List<Recensione>) {
        lista.clear()
        lista.addAll(nuovaLista)
        notifyDataSetChanged()
    }
}
