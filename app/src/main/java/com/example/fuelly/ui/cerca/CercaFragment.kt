package com.example.fuelly.ui.cerca

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fuelly.ui.dettagli.DettagliActivity
import com.example.fuelly.R
import com.example.fuelly.StazioneAdapter
import com.example.fuelly.databinding.DialogFiltriBinding
import com.example.fuelly.databinding.FragmentCercaBinding
import com.example.fuelly.repository.model.Benzinaio
import com.example.fuelly.repository.model.ColonninaEV
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetDialog

class CercaFragment : Fragment() {
    private var _binding: FragmentCercaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CercaViewModel by viewModels()
    private lateinit var adapter: StazioneAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCercaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        setupRecyclerView()
        observeViewModel()
        recuperaPosizioneECaricaDati()

        //ricerca testuale (mano a mano che l'utente scrive, applichiamo i filtri)
        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.query = s?.toString() ?: ""
                viewModel.applicaFiltri()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        //listener per il click sull'icona dei filtri all'interno della SearchView
        binding.searchLayout.setEndIconOnClickListener {
            mostraDialogFiltri()
        }

        //listener per i filtri rapidi (benzina economica, diesel economico, ect.)
        binding.filterChipGroup.setOnCheckedChangeListener { _, checkedId ->
            viewModel.selectedChipId = checkedId
            viewModel.applicaFiltri()
        }
    }

    private fun observeViewModel() {
        viewModel.listaFiltrata.observe(viewLifecycleOwner) { lista ->
            adapter.updateData(lista, viewModel.userLat, viewModel.userLon)
            binding.rvCerca.scrollToPosition(0)
        }
    }

    //funzione di configurazione del RecyclerView con il suo adapter e layout manager
    private fun setupRecyclerView() {
        binding.rvCerca.layoutManager = LinearLayoutManager(requireContext())
        adapter = StazioneAdapter(emptyList(), viewModel.userLat, viewModel.userLon) { item ->
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
            //passiamo la posizione utente ai dettagli
            intent.putExtra("USER_LAT", viewModel.userLat ?: 0.0)
            intent.putExtra("USER_LON", viewModel.userLon ?: 0.0)
            startActivity(intent)
        }
        binding.rvCerca.adapter = adapter
    }

    //funzione che recupera la posizione dell'utente (se permesso) nel caso in cui non sia già stata recuperata in precedenza
    private fun recuperaPosizioneECaricaDati() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    viewModel.userLat = location.latitude
                    viewModel.userLon = location.longitude
                }
                viewModel.caricaDatiIniziali()
            }
        } else {
            viewModel.caricaDatiIniziali()
        }
    }

    //funzione che gestisce il dialog con i filtri avanzati
    private fun mostraDialogFiltri() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogFiltriBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        // 1. INIZIALIZZAZIONE STATO INIZIALE DEL DIALOG
        dialogBinding.toggleGroupTipo.check(viewModel.filtroTipoSelezionatoId)
        dialogBinding.switchSoloOperative.isChecked = viewModel.soloOperative
        viewModel.carburantiSelezionatiIds.forEach { dialogBinding.chipGroupCarburante.check(it) }
        viewModel.connettoriSelezionatiIds.forEach { dialogBinding.chipGroupConnettori.check(it) }

        fun aggiornaSezioni(id: Int) {
            dialogBinding.sectionCarburante.isVisible = (id == R.id.btnTipoBenzina)
            dialogBinding.sectionEV.isVisible = (id == R.id.btnTipoEV)
        }
        aggiornaSezioni(viewModel.filtroTipoSelezionatoId)

        // 2. LISTENERS INTERNI AL DIALOG
        dialogBinding.toggleGroupTipo.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) aggiornaSezioni(checkedId)
        }

        // Listener per il tasto "Reset" che riporta tutti i filtri allo stato iniziale di default
        dialogBinding.btnReset.setOnClickListener {
            dialogBinding.toggleGroupTipo.check(R.id.btnTipoBenzina)
            dialogBinding.chipGroupCarburante.clearCheck()
            dialogBinding.chipGroupConnettori.clearCheck()
            dialogBinding.switchSoloOperative.isChecked = false
        }

        // Listener per il tasto "Annulla" che chiude semplicemente il dialog senza salvare le modifiche
        dialogBinding.btnAnnulla.setOnClickListener { dialog.dismiss() }

        // Listener per il tasto "Applica" che salva lo stato dei filtri, applica i filtri alla lista e chiude il dialog
        dialogBinding.btnApplicaFiltri.setOnClickListener {
            viewModel.filtroTipoSelezionatoId = dialogBinding.toggleGroupTipo.checkedButtonId
            viewModel.soloOperative = dialogBinding.switchSoloOperative.isChecked

            viewModel.carburantiSelezionatiIds.clear()
            viewModel.carburantiSelezionatiIds.addAll(dialogBinding.chipGroupCarburante.checkedChipIds)

            viewModel.connettoriSelezionatiIds.clear()
            viewModel.connettoriSelezionatiIds.addAll(dialogBinding.chipGroupConnettori.checkedChipIds)

            viewModel.applicaFiltri()
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
