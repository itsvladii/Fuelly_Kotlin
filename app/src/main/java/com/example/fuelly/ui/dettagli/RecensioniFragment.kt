package com.example.fuelly.ui.dettagli

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fuelly.R
import com.example.fuelly.repository.model.Recensione
import com.example.fuelly.repository.supabase.SupabaseInstance
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.github.jan.supabase.auth.auth
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

import java.util.Locale

class RecensioniFragment : Fragment() {

    private var stationId: Long = -1L
    private var typeStation: String = ""
    private lateinit var adapter: RecensioniAdapter
    private val viewModel: DettagliViewModel by activityViewModels()

    private lateinit var txtMediaVoto: TextView
    private lateinit var ratingMedia: RatingBar
    private lateinit var rvRecensioni: RecyclerView
    private lateinit var txtVuoto: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //recupero l'id e il tipo della stazione dalla mapsFragment
        stationId = activity?.intent?.getLongExtra("ID_ELEMENTO", -1L) ?: -1L
        typeStation = activity?.intent?.getStringExtra("TIPO_ELEMENTO").toString()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        //INFLATE DEL FRAGMENT RECENSIONI
        val view = inflater.inflate(R.layout.fragment_recensioni, container, false)

        //variabili
        txtMediaVoto = view.findViewById(R.id.txtMediaVoto)
        ratingMedia = view.findViewById(R.id.ratingMedia)
        rvRecensioni = view.findViewById(R.id.rvRecensioni)
        txtVuoto = view.findViewById(R.id.txtNessunaRecensione)

        //ASSEGNO ALL'ADAPTER LA LISTA VUOTA
        adapter = RecensioniAdapter(mutableListOf()) { recensione ->
            viewModel.eliminaRecensione(recensione)
        }

        // ASSEGNO IL RECYCLER VIEW (layoutmanager si occupa gi gestire la disposizione degli elementi)
        rvRecensioni.layoutManager = LinearLayoutManager(context)

        //collego l'adapter alla recycler view
        //RECYCLERVIEW.ADAPTER = MIO ADAPTER CHE HO CREATO
        rvRecensioni.adapter = adapter

        if (typeStation == "EV") {
            setupGraficaEV(view)
        }

        view.findViewById<Button>(R.id.btnScriviRecensione).setOnClickListener {

            //richiamo la funzione che mi apre il pannello per scrivere la recensione
            mostraDialogRecensione()
        }

        //richiamo la funzione osservatrice
        observeViewModel()
        return view
    }

    private fun observeViewModel() {

        //osservo il livedata della lista delle recensioni
        viewModel.recensioni.observe(viewLifecycleOwner) { result ->

            //se la lista è vuota mostro il messaggio di lista vuota e nascondo la recycler view
            if (result.isEmpty()) {
                txtVuoto.visibility = View.VISIBLE
                rvRecensioni.visibility = View.GONE
                aggiornaHeaderMedia(0.0)
            } else {
                txtVuoto.visibility = View.GONE
                rvRecensioni.visibility = View.VISIBLE
                val media = result.map { it.rating }.average()
                //aggiorno la media delle recensioni
                aggiornaHeaderMedia(media)
                adapter.updateData(result)
            }
        }
    }

    private fun aggiornaHeaderMedia(media: Double) {
        txtMediaVoto.text = String.format(Locale.ITALY, "%.1f", media)
        ratingMedia.rating = media.toFloat()
    }

    //Funzione: --> mostra PANNELLO PER SCRIIVERE LA RECENSIONE
    private fun mostraDialogRecensione() {

        val dialog = BottomSheetDialog(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_nuova_recensione, null)

        //imposto il layout del dialog
        dialog.setContentView(dialogView)

        //variabili
        val btnInvia = dialogView.findViewById<Button>(R.id.btnInviaRecensione)
        val ratingInput = dialogView.findViewById<RatingBar>(R.id.ratingInput)
        val editCommento = dialogView.findViewById<EditText>(R.id.editCommento)

        //setto il colore del pulsante in base al tipo di stazione
        if (typeStation == "EV") {
            val coloreEV = "#00FFC2".toColorInt()
            btnInvia?.backgroundTintList = ColorStateList.valueOf(coloreEV)
            btnInvia?.setTextColor(Color.BLACK)
            ratingInput?.progressTintList = ColorStateList.valueOf(coloreEV)
            ratingInput?.secondaryProgressTintList = ColorStateList.valueOf(coloreEV)
        }

        //al click del pulsante invio recensione
        btnInvia.setOnClickListener {

            //recupero utente loggato
            val user = SupabaseInstance.client.auth.currentUserOrNull()

            if (user == null) {
                Toast.makeText(context, getString(R.string.review_login_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            //recupero il nome dell'utente e l'avatar_url
            val nomeCompleto = user.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull ?: "Utente Anonimo"
            val avatarUrl = user.userMetadata?.get("avatar_url")?.jsonPrimitive?.contentOrNull

            //nuovo oggetto di tipo recensione
            val nuova = Recensione(
                idUtente = user.id,
                idBenzinaio = stationId, //recuperato dall'intent
                rating = ratingInput.rating.toInt(), //barra di rating di input
                descRecensione = editCommento.text.toString(), //text di input
                nome = nomeCompleto,
                avatar_url = avatarUrl,
                tipo = typeStation //recuperato dall'intent
            )

            //inserisco la recensione nel DB
            viewModel.inserisciRecensione(nuova)

            dialog.dismiss()

            Toast.makeText(context, getString(R.string.review_success), Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    private fun setupGraficaEV(rootView: View) {

        val coloreEV = Color.parseColor("#00FFC2")

        //BOTTONE SCRIVI RECENSIONE E VARI SET GRAFICI
        val btnScrivi = rootView.findViewById<Button>(R.id.btnScriviRecensione)
        btnScrivi?.backgroundTintList = ColorStateList.valueOf(coloreEV)
        btnScrivi?.setTextColor(Color.BLACK)

        //BARRA DI RATING STELLINE CON VARI SET GRAFICI
        val ratingMedia = rootView.findViewById<RatingBar>(R.id.ratingMedia)
        ratingMedia?.progressTintList = ColorStateList.valueOf(coloreEV)
        ratingMedia?.secondaryProgressTintList = ColorStateList.valueOf(coloreEV)
    }
}
