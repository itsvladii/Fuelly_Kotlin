package com.example.fuelly

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import android.widget.ImageButton
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fuelly.repository.model.Recensione
import com.example.fuelly.repository.supabase.SupabaseInstance
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
                val context = holder.itemView.context
                val dialog = AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.review_delete_title))
                    .setMessage(context.getString(R.string.review_delete_confirm))
                    .setPositiveButton(context.getString(R.string.review_delete_button)) { _, _ ->
                        onEliminaClick(r)
                    }
                    .setNegativeButton(context.getString(R.string.cancel), null)
                    .create()

                //impostazione dei colori dei pulsanti dell'AlertDialog
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    ?.setTextColor(ContextCompat.getColor(context, R.color.error_red))
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    ?.setTextColor(ContextCompat.getColor(context, R.color.dialog_grey))

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
