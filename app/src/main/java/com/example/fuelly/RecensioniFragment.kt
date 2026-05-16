package com.example.fuelly

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.graphics.toColorInt
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
    private var typeStation: String=""
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
        typeStation= activity?.intent?.getStringExtra("TIPO_ELEMENTO").toString()


    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_recensioni, container, false)

        // Inizializzazione View
        txtMediaVoto = view.findViewById(R.id.txtMediaVoto)
        ratingMedia = view.findViewById(R.id.ratingMedia)
        rvRecensioni = view.findViewById(R.id.rvRecensioni)
        txtVuoto = view.findViewById(R.id.txtNessunaRecensione)

        // Setup RecyclerView passandogli la lambda di eliminazione
        adapter = RecensioniAdapter(listaRecensioni) { recensioneDaEliminare ->
            eliminaRecensione(recensioneDaEliminare)
        }

        rvRecensioni.layoutManager = LinearLayoutManager(context)
        rvRecensioni.adapter = adapter

        if(typeStation == "EV") {
            setupGraficaEV(view)
        }

        view.findViewById<Button>(R.id.btnScriviRecensione).setOnClickListener {
            mostraDialogRecensione()
        }

        caricaRecensioni()
        return view
    }

    private fun caricaRecensioni() {
        lifecycleScope.launch {
            try {

                var result: List<Recensione> = emptyList()

                if(typeStation=="BENZINA"){
                result = SupabaseInstance.client.from("recensioni_benzinai").select {
                    filter {
                        eq("idImpianto", stationId)
                    }
                    order("created_at", order = Order.DESCENDING)
                }.decodeList<Recensione>()
                }
                else if(typeStation=="EV"){
                    result = SupabaseInstance.client.from("recensioni_ev").select {
                        filter {
                            eq("idImpianto", stationId)
                        }
                        order("created_at", order = Order.DESCENDING)
                    }.decodeList<Recensione>()
                }

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

        if (typeStation == "EV") {
            val coloreEV = "#00FFC2".toColorInt()
            btnInvia?.backgroundTintList = ColorStateList.valueOf(coloreEV)
            btnInvia?.setTextColor(Color.BLACK)

            ratingInput?.progressTintList = ColorStateList.valueOf(coloreEV)
            ratingInput?.secondaryProgressTintList = ColorStateList.valueOf(coloreEV)
        }

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
                        tipo= typeStation
                    )

                    if(typeStation=="BENZINA") {
                        //inserisco nel DB
                        SupabaseInstance.client.from("recensioni_benzinai").insert(nuova)
                    }else if(typeStation=="EV")
                    {
                        SupabaseInstance.client.from("recensioni_ev").insert(nuova)
                    }

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

    private fun setupGraficaEV(rootView: View) {
        val coloreEV = Color.parseColor("#00FFC2")
        val coloreTesto = Color.BLACK

        val btnScrivi = rootView.findViewById<Button>(R.id.btnScriviRecensione)

        btnScrivi?.backgroundTintList = ColorStateList.valueOf(coloreEV)
        btnScrivi?.setTextColor(coloreTesto)

        //cambia il colore delle stelle della media
        val ratingMedia = rootView.findViewById<RatingBar>(R.id.ratingMedia)
        ratingMedia?.progressTintList = ColorStateList.valueOf(coloreEV)
        ratingMedia?.secondaryProgressTintList = ColorStateList.valueOf(coloreEV)
    }

    private fun eliminaRecensione(recensione: Recensione) {
        lifecycleScope.launch {
            try {
                // Selezioniamo la tabella corretta in base al tipo di stazione
                val tabella = if (typeStation == "BENZINA") "recensioni_benzinai" else "recensioni_ev"

                SupabaseInstance.client.from(tabella).delete {
                    filter {
                        eq("idRecensione",recensione.idRecensione)
                        eq("idUtente",recensione.idUtente)
                    }
                }

                activity?.runOnUiThread {
                    Toast.makeText(context, "Recensione eliminata", Toast.LENGTH_SHORT).show()
                    caricaRecensioni() // Eseguiamo il refresh per ricalcolare la media e aggiornare i dati
                }
            } catch (e: Exception) {
                Log.e("Fuelly", "Errore durante l'eliminazione: ${e.message}")
                activity?.runOnUiThread {
                    Toast.makeText(context, "Impossibile eliminare la recensione", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


}