package com.example.fuelly

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fuelly.classes.Benzinaio
import com.example.fuelly.databinding.FragmentCercaBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

class CercaFragment : Fragment() {
    // Aggiunto binding per il layout e inizializzazione del binding
    private var _binding: FragmentCercaBinding? = null

    // Aggiunto getter per il binding e settaggio del layout alla creazione dell'activity
    private val binding get() = _binding!!

    // Aggiunto adapter per il RecyclerView
    private lateinit var adapter: BenzinaioAdapter
    
    // Lista originale per resettare i filtri
    private var listaOriginale: List<Benzinaio> = emptyList()

    // onCreateView per il layout dell'activity
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCercaBinding.inflate(inflater, container, false)
        return binding.root
    }

    // onViewCreated per il fragment e inizializzazione del RecyclerView
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Imposta la navigation bar di sistema trasparente con icone scure
        requireActivity().window.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(this, decorView).isAppearanceLightNavigationBars = true
        }

        setupRecyclerView()
        setupListeners()
        caricaDati()
    }

    private fun caricaDati() {
        listaOriginale = Benzinaio.listaVicini
        adapter.updateData(listaOriginale)
    }

    // Aggiunto metodo per configurare il RecyclerView
    private fun setupRecyclerView() {
        // Aggiunto layout manager per il RecyclerView
        binding.rvCerca.layoutManager = LinearLayoutManager(requireContext())

        // Imposta l'adapter benzinaio per il RecyclerView
        adapter = BenzinaioAdapter(emptyList()) { benzinaio ->
            val intent = Intent(requireContext(), DettagliActivity::class.java).apply {
                putExtra("ID_ELEMENTO", benzinaio.id.toLong())
                putExtra("TIPO_ELEMENTO", "BENZINA")
            }
            startActivity(intent)
        }
        binding.rvCerca.adapter = adapter
    }

    private fun setupListeners() {
        // Listener per la ricerca testuale
        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applicaFiltri()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Listener per i Chip dei filtri
        binding.filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            applicaFiltri()
        }

        binding.searchLayout.setEndIconOnClickListener {
            mostraDialogFiltri()
        }

    }

    private fun mostraDialogFiltri() {
        val dialog =BottomSheetDialog(requireContext())
        // Usiamo il ViewBinding per il dialogo per accedere facilmente agli ID
        val dialogBinding = com.example.fuelly.databinding.DialogFiltriBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        // --- LOGICA PULSANTI DIALOGO ---

        // Pulsante Reset
        dialogBinding.btnReset.setOnClickListener {
            dialogBinding.toggleGroupTipo.clearChecked()
            dialogBinding.chipGroupCarburante.clearCheck()
            dialogBinding.switchSoloOperative.isChecked = false
        }

        // Pulsante Annulla
        dialogBinding.btnAnnulla.setOnClickListener {
            dialog.dismiss()
        }

        // Pulsante Applica
        dialogBinding.btnApplicaFiltri.setOnClickListener {
            // Recuperiamo i valori dal dialogo
            val soloOperative = dialogBinding.switchSoloOperative.isChecked

            // Eseguiamo il filtraggio
            //filtraListaAvanzata(soloOperative, soloSelf)

            dialog.dismiss()
        }

        dialog.show()
    }

    private fun applicaFiltri() {
        val query = binding.editSearch.text.toString().lowercase()

        // 1. Filtro testuale (Bandiera o Comune)
        var listaFiltrata = listaOriginale.filter {
            it.bandiera.lowercase().contains(query) || it.comune.lowercase().contains(query)
        }

        // 2. Ordinamento per prezzo in base al chip selezionato
        listaFiltrata = when (binding.filterChipGroup.checkedChipId) {
            R.id.chipBenzina -> listaFiltrata.filter { it.prezzoBenzina > 0 }.sortedBy { it.prezzoBenzina }
            R.id.chipDiesel -> listaFiltrata.filter { it.prezzoDiesel > 0 }.sortedBy { it.prezzoDiesel }
            else -> listaFiltrata // "Tutti" - ordine di vicinanza originale
        }

        adapter.updateData(listaFiltrata)

        // Scroll in alto dopo il filtraggio
        binding.rvCerca.scrollToPosition(0)
    }

    // Aggiunto metodo per distruggere il binding
    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
    }
}
