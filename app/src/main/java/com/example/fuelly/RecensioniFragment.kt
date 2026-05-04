package com.example.fuelly

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fuelly.classes.Recensione
import com.example.fuelly.supabase.SupabaseInstance
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class RecensioniFragment : Fragment() {

    private var stationId: Long = -1L
    private lateinit var adapter: RecensioniAdapter
    private val listaRecensioni = mutableListOf<Recensione>()

    // Riferimenti UI
    private lateinit var txtMediaVoto: TextView
    private lateinit var ratingMedia: RatingBar
    private lateinit var rvRecensioni: RecyclerView
    private lateinit var txtVuoto: TextView

    companion object {
        fun newInstance(id: Long): RecensioniFragment {
            val fragment = RecensioniFragment()
            val args = Bundle()
            args.putLong("station_id", id)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        stationId = activity?.intent?.getLongExtra("ID_ELEMENTO", -1L) ?: -1L
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_recensioni, container, false)

        // Inizializzazione View
        txtMediaVoto = view.findViewById(R.id.txtMediaVoto)
        ratingMedia = view.findViewById(R.id.ratingMedia)
        rvRecensioni = view.findViewById(R.id.rvRecensioni)
        txtVuoto = view.findViewById(R.id.txtNessunaRecensione)

        // Setup RecyclerView
        adapter = RecensioniAdapter(listaRecensioni)
        rvRecensioni.layoutManager = LinearLayoutManager(context)
        rvRecensioni.adapter = adapter

        // Listener per scrivere una recensione
        view.findViewById<Button>(R.id.btnScriviRecensione).setOnClickListener {
            mostraDialogRecensione()
        }

        caricaRecensioni()

        return view
    }

    private fun caricaRecensioni() {
        lifecycleScope.launch {
            try {
                // Scarichiamo le recensioni e le ordiniamo dalla più recente
                val result = SupabaseInstance.client.from("recensioni").select {
                    filter { eq("idImpianto", stationId) }
                    order("created_at", order = Order.DESCENDING) //ordinamento discendente delle recensioni
                }.decodeList<Recensione>()

                activity?.runOnUiThread {
                    //se non ho recensioni, mostro un txt che dice "non c'è nulla"
                    if (result.isEmpty()) {
                        txtVuoto.visibility = View.VISIBLE
                        rvRecensioni.visibility = View.GONE
                    } else {
                        //se c'è una recensione, aggiorno il fragment con tutte le recensioni
                        txtVuoto.visibility = View.GONE
                        rvRecensioni.visibility = View.VISIBLE

                        //calcolo media recensioni
                        val media = result.map { it.rating }.average()
                        aggiornaHeaderMedia(media)

                        //update del adapter
                        adapter.updateData(result)
                    }
                }
            } catch (e: Exception) {
                Log.e("Fuelly", "Errore caricamento: ${e.message}")
            }
        }
    }

    private fun aggiornaHeaderMedia(media: Double) {
        txtMediaVoto.text = String.format("%.1f", media)
        ratingMedia.rating = media.toFloat()
    }

    private fun mostraDialogRecensione() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_nuova_recensione, null)
        dialog.setContentView(dialogView)

        val btnInvia = dialogView.findViewById<Button>(R.id.btnInviaRecensione)
        val ratingInput = dialogView.findViewById<RatingBar>(R.id.ratingInput)
        val editCommento = dialogView.findViewById<EditText>(R.id.editCommento)

        btnInvia.setOnClickListener {
            val user = SupabaseInstance.client.auth.currentUserOrNull()
            val nomeCompleto = user?.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull ?: "Utente Anonimo"
            val avatarUrl = user?.userMetadata?.get("avatar_url")?.jsonPrimitive?.contentOrNull
            if (user == null) {
                Toast.makeText(context, "Effettua il login per recensire", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            //creazione di una nuova recensione
            lifecycleScope.launch {
                try {
                    //creo nuova istanza di classe Recensione
                    val nuova = Recensione(

                        idUtente = user.id,
                        idBenzinaio = stationId,
                        rating = ratingInput.rating.toInt(),
                        descRecensione = editCommento.text.toString(),
                        nome=nomeCompleto,
                        avatar_url = avatarUrl,

                    )

                    //inserisco nel DB
                    SupabaseInstance.client.from("recensioni").insert(nuova)

                    activity?.runOnUiThread {
                        dialog.dismiss()
                        caricaRecensioni() //refresh della lista recensioni post-inserimento
                        Toast.makeText(context, "Recensione inviata!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("Fuelly", "Errore invio: ${e.message}")
                }
            }
        }
        dialog.show()
    }
}