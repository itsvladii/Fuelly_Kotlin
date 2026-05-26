package com.example.fuelly

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fuelly.repository.model.Benzinaio
import com.example.fuelly.repository.model.ColonninaEV
import com.example.fuelly.databinding.FragmentSalvatiBinding

class SalvatiFragment : Fragment() {

    private var _binding: FragmentSalvatiBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: StazioneAdapter
    private var userLat: Double? = null
    private var userLon: Double? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSalvatiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //recuperiamo le coordinate dell'utente passate tramite gli argomenti
        userLat = arguments?.getDouble("USER_LAT")
        userLon = arguments?.getDouble("USER_LON")

        //richiamo la funzione che configura la recyclerView
        setupRecyclerView()

        //in base al pulsante selezionato nella toggleGroup, eseguo il "cambio" delle liste dei salvati in base alla tipologia
        binding.toggleGroupSalvati.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnSalvatiBenzina -> caricaBenzinaiSalvate()
                    R.id.btnSalvatiEV -> caricaColonnineSalvate()
                }
            }
        }

        //stato iniziale al caricamento del fragment (solo i salvati dei benzinai)
        binding.toggleGroupSalvati.check(R.id.btnSalvatiBenzina)
    }

    //funzione che configura la RecyclerView
    private fun setupRecyclerView() {
        binding.rvSalvati.layoutManager = LinearLayoutManager(requireContext())

        //imposta l'adapter polimorfico passandogli la lambda di click per navigare ai dettagli
        adapter = StazioneAdapter(emptyList(), userLat, userLon) { item ->
            val intent = Intent(requireContext(), DettagliActivity::class.java)
            when (item) {
                is Benzinaio -> {
                    intent.putExtra("ID_ELEMENTO", item.id.toLong())
                    intent.putExtra("TIPO_ELEMENTO", "BENZINA")
                }

                is ColonninaEV -> {
                    intent.putExtra("ID_ELEMENTO", item.id.toLong())
                    intent.putExtra("TIPO_ELEMENTO", "EV")
                }
            }
            // Passiamo la posizione utente ai dettagli per il calcolo delle distanze/percorsi
            intent.putExtra("USER_LAT", userLat ?: 0.0)
            intent.putExtra("USER_LON", userLon ?: 0.0)
            startActivity(intent)
        }
        binding.rvSalvati.adapter = adapter
    }

    //funzione di caricamento della lista benzinai salvati
    private fun caricaBenzinaiSalvate() {
        adapter.updateData(Benzinaio.listaSalvati)
        riportaInCima()
    }

    //funzione di caricamento della lista delle colonnine salvate
    private fun caricaColonnineSalvate() {
        adapter.updateData(ColonninaEV.listaSalvati)
        riportaInCima()
    }

    //funzione che riporta la recycleView al primo elemento (usato quando applicato il filtro)
    private fun riportaInCima() {
        binding.rvSalvati.scrollToPosition(0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
