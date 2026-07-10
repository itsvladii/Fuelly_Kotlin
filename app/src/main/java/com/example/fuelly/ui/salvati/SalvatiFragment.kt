package com.example.fuelly.ui.salvati

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fuelly.R
import com.example.fuelly.StazioneAdapter
import com.example.fuelly.databinding.FragmentSalvatiBinding
import com.example.fuelly.repository.model.Benzinaio
import com.example.fuelly.repository.model.ColonninaEV
import com.example.fuelly.ui.dettagli.DettagliActivity

class SalvatiFragment : Fragment() {

    private var _binding: FragmentSalvatiBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SalvatiViewModel by viewModels()
    private lateinit var adapter: StazioneAdapter //ADAPTER POLIMORFO
    private var userLat: Double? = null
    private var userLon: Double? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        //INFLATE DEL FRAGMENT
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
        observeViewModel()

        //in base al pulsante selezionato nella toggleGroup, eseguo il "cambio" delle liste dei salvati in base alla tipologia
        binding.toggleGroupSalvati.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                //chiamo il metodo del SALVATI_VIEWMODEL PER CARICARE LA LISTA CORRETTA
                viewModel.selezionaTipo(checkedId)
            }
        }

        //stato iniziale al caricamento del fragment (solo i salvati dei benzinai)
        binding.toggleGroupSalvati.check(R.id.btnSalvatiBenzina)
    }

    private fun observeViewModel() {
        //osserva l'aggiornamento della lista dei salvati
        viewModel.listaSalvati.observe(viewLifecycleOwner) { lista ->

            //aggiorna l'adapter con la nuova lista
            adapter.updateData(lista)

            //riporta la RecyclerView al primo elemento
            riportaInCima()
        }
    }

    //funzione che configura la RecyclerView
    private fun setupRecyclerView() {

        //RECYCLERVIEW
        binding.rvSalvati.layoutManager = LinearLayoutManager(requireContext())

        //imposta l'adapter polimorfico passandogli la lambda di click per navigare ai dettagli
        adapter = StazioneAdapter(emptyList(), userLat, userLon) { item ->

            //assegno l'intent
            val intent = Intent(requireContext(), DettagliActivity::class.java)

            //quando ho "cliccato" passo dei parametri all'intent
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
        //assegno l'adapter alla RecyclerView
        //RECYCLERVIEW.ADAPTER = MIO ADAPTER
        binding.rvSalvati.adapter = adapter
    }

    //funzione che riporta la recycleView al primo elemento (usato quando applicato il filtro)
    private fun riportaInCima() {
        //scroll della RecyclerView al primo elemento
        binding.rvSalvati.scrollToPosition(0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}