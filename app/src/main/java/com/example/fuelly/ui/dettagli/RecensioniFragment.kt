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
        stationId = activity?.intent?.getLongExtra("ID_ELEMENTO", -1L) ?: -1L
        typeStation = activity?.intent?.getStringExtra("TIPO_ELEMENTO").toString()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_recensioni, container, false)

        txtMediaVoto = view.findViewById(R.id.txtMediaVoto)
        ratingMedia = view.findViewById(R.id.ratingMedia)
        rvRecensioni = view.findViewById(R.id.rvRecensioni)
        txtVuoto = view.findViewById(R.id.txtNessunaRecensione)

        adapter = RecensioniAdapter(mutableListOf()) { recensione ->
            viewModel.eliminaRecensione(recensione)
        }

        rvRecensioni.layoutManager = LinearLayoutManager(context)
        rvRecensioni.adapter = adapter

        if (typeStation == "EV") {
            setupGraficaEV(view)
        }

        view.findViewById<Button>(R.id.btnScriviRecensione).setOnClickListener {
            mostraDialogRecensione()
        }

        observeViewModel()
        return view
    }

    private fun observeViewModel() {
        viewModel.recensioni.observe(viewLifecycleOwner) { result ->
            if (result.isEmpty()) {
                txtVuoto.visibility = View.VISIBLE
                rvRecensioni.visibility = View.GONE
                aggiornaHeaderMedia(0.0)
            } else {
                txtVuoto.visibility = View.GONE
                rvRecensioni.visibility = View.VISIBLE
                val media = result.map { it.rating }.average()
                aggiornaHeaderMedia(media)
                adapter.updateData(result)
            }
        }
    }

    private fun aggiornaHeaderMedia(media: Double) {
        txtMediaVoto.text = String.format(Locale.ITALY, "%.1f", media)
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
            if (user == null) {
                Toast.makeText(context, getString(R.string.review_login_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val nomeCompleto = user.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull ?: "Utente Anonimo"
            val avatarUrl = user.userMetadata?.get("avatar_url")?.jsonPrimitive?.contentOrNull

            val nuova = Recensione(
                idUtente = user.id,
                idBenzinaio = stationId,
                rating = ratingInput.rating.toInt(),
                descRecensione = editCommento.text.toString(),
                nome = nomeCompleto,
                avatar_url = avatarUrl,
                tipo = typeStation
            )

            viewModel.inserisciRecensione(nuova)
            dialog.dismiss()
            Toast.makeText(context, getString(R.string.review_success), Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    private fun setupGraficaEV(rootView: View) {
        val coloreEV = Color.parseColor("#00FFC2")
        val btnScrivi = rootView.findViewById<Button>(R.id.btnScriviRecensione)
        btnScrivi?.backgroundTintList = ColorStateList.valueOf(coloreEV)
        btnScrivi?.setTextColor(Color.BLACK)
        val ratingMedia = rootView.findViewById<RatingBar>(R.id.ratingMedia)
        ratingMedia?.progressTintList = ColorStateList.valueOf(coloreEV)
        ratingMedia?.secondaryProgressTintList = ColorStateList.valueOf(coloreEV)
    }
}
