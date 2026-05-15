package com.example.fuelly

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fuelly.classes.Benzinaio
import com.example.fuelly.classes.ColonninaEV
import com.example.fuelly.databinding.DialogFiltriBinding
import com.example.fuelly.databinding.FragmentCercaBinding
import com.example.fuelly.utils.Utils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.ChipGroup
//TODO: filtro testuale e per tipo funziona, da sistemare i chip "rapidi" e i chip dei tipi di carburante/prese
class CercaFragment : Fragment() {
    private var _binding: FragmentCercaBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: StazioneAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var listaTotaleOriginale: List<Any> = emptyList()

    // Stato dei filtri persistente nel Fragment
    private var filtroTipoSelezionatoId: Int = R.id.btnTipoBenzina // ID predefinito per "Tutti"
    private var soloOperative: Boolean = false
    private val carburantiSelezionatiIds = mutableSetOf<Int>()
    private val connettoriSelezionatiIds = mutableSetOf<Int>()

    // Coordinate dell'utente
    private var userLat: Double? = null
    private var userLon: Double? = null

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
        recuperaPosizioneECaricaDati()

        // Ricerca testuale reattiva
        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applicaFiltri()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Pulsante filtri avanzati
        binding.searchLayout.setEndIconOnClickListener {
            mostraDialogFiltri()
        }

        // Filtri rapidi (Piu vicini, Economici, ecc.) nell'header
        binding.filterChipGroup.setOnCheckedChangeListener { _, _ ->
            applicaFiltri()
        }
    }

    private fun setupRecyclerView() {
        binding.rvCerca.layoutManager = LinearLayoutManager(requireContext())
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
            // Passiamo la posizione utente ai dettagli
            intent.putExtra("USER_LAT", userLat ?: 0.0)
            intent.putExtra("USER_LON", userLon ?: 0.0)
            startActivity(intent)
        }
        binding.rvCerca.adapter = adapter
    }

    private fun recuperaPosizioneECaricaDati() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    userLat = location.latitude
                    userLon = location.longitude
                }
                caricaDatiIniziali()
            }
        } else {
            caricaDatiIniziali()
        }
    }

    private fun caricaDatiIniziali() {
        listaTotaleOriginale = Benzinaio.listaVicini + ColonninaEV.listaVicini
        adapter.updateData(Benzinaio.listaVicini, userLat, userLon)
    }

    private fun mostraDialogFiltri() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogFiltriBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        // 1. RIPRISTINO STATO
        dialogBinding.toggleGroupTipo.check(filtroTipoSelezionatoId)
        dialogBinding.switchSoloOperative.isChecked = soloOperative
        carburantiSelezionatiIds.forEach { dialogBinding.chipGroupCarburante.check(it) }
        connettoriSelezionatiIds.forEach { dialogBinding.chipGroupConnettori.check(it) }

        fun aggiornaSezioni(id: Int) {
            dialogBinding.sectionCarburante.isVisible = (id == R.id.btnTipoBenzina)
            dialogBinding.sectionEV.isVisible = (id == R.id.btnTipoEV)
        }
        aggiornaSezioni(filtroTipoSelezionatoId)

        // 2. LISTENERS INTERNI AL DIALOG
        dialogBinding.toggleGroupTipo.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) aggiornaSezioni(checkedId)
        }

        dialogBinding.btnReset.setOnClickListener {
            dialogBinding.toggleGroupTipo.check(R.id.btnTipoBenzina)
            dialogBinding.chipGroupCarburante.clearCheck()
            dialogBinding.chipGroupConnettori.clearCheck()
            dialogBinding.switchSoloOperative.isChecked = false
        }

        dialogBinding.btnAnnulla.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnApplicaFiltri.setOnClickListener {
            // SALVATAGGIO STATO
            filtroTipoSelezionatoId = dialogBinding.toggleGroupTipo.checkedButtonId
            soloOperative = dialogBinding.switchSoloOperative.isChecked

            carburantiSelezionatiIds.clear()
            carburantiSelezionatiIds.addAll(dialogBinding.chipGroupCarburante.checkedChipIds)

            connettoriSelezionatiIds.clear()
            connettoriSelezionatiIds.addAll(dialogBinding.chipGroupConnettori.checkedChipIds)

            applicaFiltri()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun applicaFiltri() {
        val query = binding.editSearch.text.toString().lowercase().trim()

        var listaFiltrata = listaTotaleOriginale.filter { item ->
            // --- A. FILTRO PER CATEGORIA (Benzinai/EV) ---
            val passaTipo = when (filtroTipoSelezionatoId) {
                R.id.btnTipoBenzina -> item is Benzinaio
                R.id.btnTipoEV -> item is ColonninaEV
                else -> true
            }

            // --- B. FILTRO TESTUALE ---
            val passaRicerca = when (item) {
                is Benzinaio -> item.bandiera.lowercase().contains(query) || item.comune.lowercase().contains(query)
                is ColonninaEV -> item.titolo.lowercase().contains(query) || item.indirizzo.lowercase().contains(query)
                else -> false
            }

            // --- C. FILTRO SPECIFICO BENZINAIO ---
            val passaBenzinaio = if (item is Benzinaio && carburantiSelezionatiIds.isNotEmpty()) {
                // Controlliamo se il benzinaio ha almeno uno dei carburanti scelti nel Dialog
                var match = false
                if (carburantiSelezionatiIds.contains(R.id.chipBenzina) && item.prezzoBenzina > 0) match = true
                if (carburantiSelezionatiIds.contains(R.id.chipGasolio) && item.prezzoDiesel > 0) match = true
                if (carburantiSelezionatiIds.contains(R.id.chipMetano) && item.prezzoMetano > 0) match = true
                if (carburantiSelezionatiIds.contains(R.id.chipGPL) && item.prezzoGPL > 0) match = true
                match
            } else true

            // --- D. FILTRO SPECIFICO EV ---
            val passaEV = if (item is ColonninaEV) {
                // Filtro operatività
                val passaOperativo = if (soloOperative) item.stato.contains("Operational", true) else true

                // Filtro connettori (Presa Type 2, CCS, ecc)
                val passaConnettori = if (connettoriSelezionatiIds.isNotEmpty()) {
                    val json = item.connettoriJson?.lowercase() ?: ""
                    var matchConnettore = false

                    // Mappa i tuoi ID chip alle stringhe contenute nel JSON di Supabase
                    if (connettoriSelezionatiIds.contains(R.id.chipType2) && json.contains("type 2")) matchConnettore = true
                    if (connettoriSelezionatiIds.contains(R.id.chipCCS2) && (json.contains("ccs") || json.contains("combo"))) matchConnettore = true
                    if (connettoriSelezionatiIds.contains(R.id.chipCHAdeMO) && json.contains("chademo")) matchConnettore = true
                    if (connettoriSelezionatiIds.contains(R.id.chipTesla) && json.contains("tesla")) matchConnettore = true

                    matchConnettore
                } else true

                passaOperativo && passaConnettori
            } else true

            passaTipo && passaRicerca && passaBenzinaio && passaEV
        }

        // --- ORDINAMENTO E FILTRAGGIO FINALE ---
        listaFiltrata = when (binding.filterChipGroup.checkedChipId) {
            R.id.chipBenzinaEconomica -> {
                listaFiltrata.filterIsInstance<Benzinaio>()
                    .filter { it.prezzoBenzina > 0 }
                    .sortedBy { it.prezzoBenzina }
            }

            R.id.chipDieselEconomico -> {
                listaFiltrata.filterIsInstance<Benzinaio>()
                    .filter { it.prezzoDiesel > 0 }
                    .sortedBy { it.prezzoDiesel }
            }

            R.id.chipTopSalvati -> {
                // Mostra solo i benzinai che compaiono nella classifica "Top" globale
                // e ordinali in base alla loro posizione in classifica (indexOf)
                listaFiltrata.filterIsInstance<Benzinaio>()
                    .filter { Benzinaio.listaTopSalvatiIds.contains(it.id) }
                    .sortedBy { Benzinaio.listaTopSalvatiIds.indexOf(it.id) }
            }

            R.id.chipPiuVicini -> {
                // Restituisce la lista filtrata mantenendo l'ordine di vicinanza
                listaFiltrata.filterIsInstance<Benzinaio>()
                    .sortedBy { Utils.calcolaDistanza(userLat ?: 0.0, userLon ?: 0.0, it.lat, it.lon) }
            }

            R.id.chipAll -> {
                listaFiltrata
            }

            else -> listaFiltrata
        }

        adapter.updateData(listaFiltrata, userLat, userLon)
        binding.rvCerca.scrollToPosition(0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}