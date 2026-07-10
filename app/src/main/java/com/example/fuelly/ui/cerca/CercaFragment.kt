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


    private val viewModel: CercaViewModel by viewModels() //"dichiarazione" della viewModel associata al fragment
    private lateinit var adapter: StazioneAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        //INFLATE DEL FRAGMENT PER L'INTERFACCIA
        _binding = FragmentCercaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //impostazione del fusedLocationClient per ottenere la posizione dell'utente
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        setupRecyclerView() //richiamo della funzione di setup della RecyclerView che contiene le stazioni
        observeViewModel() //richiamo della funzione che contiene gli observe per la comunicazione tra View e ViewModel
        recuperaPosizioneECaricaDati() //funzione di "fallback" in caso in cui non abbiamo la posizione dell'utente
        aggiornaVisibilitaFiltriRapidi() //aggiorna la visibilità dei filtri rapidi in base al tipo di stazione selezionato

        //RICERCA TEASTUALE (mano a mano che l'utente scrive, applichiamo i filtri)
        binding.editSearch.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.query = s?.toString() ?: "" //setto come "query per la barra di ricerca" cosa sto scrivendo nella textbox
                viewModel.applicaFiltri() //richiamo la funzione del viemodel
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        //APERTURA PANNELLO FILTRI AVANZATI
        binding.searchLayout.setEndIconOnClickListener {
            mostraDialogFiltri()
        }

        //FILTRI RAPIDI (BENZ.ECNOMICA, VICINO A ME....)
        binding.filterChipGroup.setOnCheckedChangeListener { _, checkedId ->
            viewModel.selectedChipId = checkedId
            viewModel.applicaFiltri() //richiamo la funzione nel ViewModel che applica i filtri (sia quelli rapidi che avanzati)
        }
    }

    //funzione che contiene gli observe per la comunicazione tra View e ViewModel
    private fun observeViewModel() {
        //durante tutto il ciclo di vita della View, essa osserva listaFiltrata su CercaViewModel per eventuali cambiamenti
        viewModel.listaFiltrata.observe(viewLifecycleOwner) { lista ->
            //ad ogni cambiamento su listaFiltrata, aggiorna l'adapter della RecycleView affinche sia aggiornata
            adapter.updateData(lista, viewModel.userLat, viewModel.userLon)
            //scroll al primo elemento della RecycleView
            binding.rvCerca.scrollToPosition(0)
        }
    }

    //funzione di configurazione del RecyclerView con il suo adapter e layout manager
    private fun setupRecyclerView() {
        binding.rvCerca.layoutManager = LinearLayoutManager(requireContext())
        adapter = StazioneAdapter(emptyList(), viewModel.userLat, viewModel.userLon) { item ->
            val intent = Intent(requireContext(), DettagliActivity::class.java) //imposto l'intent di passaggio alla DettagliActivity quando seleziono una stazione
            when (item) {
                //in base alla tipologia di stazione, passo un Extra specifico all'intent
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
            //passo a DettagliActivity
            startActivity(intent)
        }
        binding.rvCerca.adapter = adapter
    }

    //funzione che recupera la posizione dell'utente (se permesso) nel caso in cui non sia già stata recuperata in precedenza
    private fun recuperaPosizioneECaricaDati() {

        //controllo dei permessi
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            //recupero posizione utente e salvo le sue coordinate nel ViewModel
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    viewModel.userLat = location.latitude
                    viewModel.userLon = location.longitude
                }
                //richiamo la funzione di caricamento dei dati iniziali del viewmodel
                viewModel.caricaDatiIniziali() //richiamo la funzione carciaDatiIniziali dal viewModel
            }
        } else {
            viewModel.caricaDatiIniziali() //richiamo la funzione caricaDatiIniziali dal viewModel (anche se non ho problemi)
        }
    }

    //funzione che gestisce il dialog con i filtri avanzati
    private fun mostraDialogFiltri() {

        // Crea l'oggetto del dialog che scorrerà dal basso verso l'alto, associandolo al contesto del Fragment
        val dialog = BottomSheetDialog(requireContext())

        // Sfrutta il View Binding per gonfiare il layout XML del dialog, rendendo i componenti UI accessibili via codice
        val dialogBinding = DialogFiltriBinding.inflate(layoutInflater)

        // Imposta la vista radice del layout appena gonfiato come contenuto visivo del dialog
        dialog.setContentView(dialogBinding.root)

        // Imposta il pulsante del Toggle Group (es. Benzina o EV) recuperando l'ultimo ID salvato nel ViewModel
        dialogBinding.toggleGroupTipo.check(viewModel.filtroTipoSelezionatoId)

        // Imposta lo stato dello Switch (attivato/disattivato) in base al valore booleano presente nel ViewModel
        dialogBinding.switchSoloOperative.isChecked = viewModel.soloOperative

        // Cicla sulla lista degli ID dei carburanti salvati e seleziona i rispettivi Chip nella UI
        viewModel.carburantiSelezionatiIds.forEach { dialogBinding.chipGroupCarburante.check(it) }

        // Cicla sulla lista degli ID dei connettori salvati e seleziona i rispettivi Chip nella UI
        viewModel.connettoriSelezionatiIds.forEach { dialogBinding.chipGroupConnettori.check(it) }

        //funzione interna per mostrare/nascondere le sezioni in base al tipo di stazione selezionata
        fun aggiornaSezioni(id: Int) {

            // Mostra la sezione carburante solo se l'ID corrisponde al pulsante della benzina/diesel
            dialogBinding.sectionCarburante.isVisible = (id == R.id.btnTipoBenzina)

            // Mostra la sezione EV (elettrico) solo se l'ID corrisponde al pulsante delle stazioni elettriche
            dialogBinding.sectionEV.isVisible = (id == R.id.btnTipoEV)
        }

        // Esegue subito la funzione appena creata per mostrare la sezione corretta all'apertura del dialog
        aggiornaSezioni(viewModel.filtroTipoSelezionatoId)

        // Configura un listener che si attiva ogni volta che l'utente cambia selezione nel Toggle Group principale
        dialogBinding.toggleGroupTipo.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) aggiornaSezioni(checkedId)
        }

        //listener per il tasto "Reset" che riporta tutti i filtri allo stato iniziale di default
        dialogBinding.btnReset.setOnClickListener {
            dialogBinding.toggleGroupTipo.check(R.id.btnTipoBenzina) //Seleziona di default il pulsante del tipo Benzina
            dialogBinding.chipGroupCarburante.clearCheck() //ripulisce la selezione dei carburanti
            dialogBinding.chipGroupConnettori.clearCheck() //ripulisce la selezione dei connettori
            dialogBinding.switchSoloOperative.isChecked = false //disattiva lo switch solo per le stazioni operative
        }

        //listener per il tasto "Annulla" (chiude semplicemente il dialog senza salvare le modifiche)
        dialogBinding.btnAnnulla.setOnClickListener { dialog.dismiss() }

        //listener per il tasto "Applica" (salva lo stato dei filtri, applica i filtri alla lista e chiude il dialog)
        dialogBinding.btnApplicaFiltri.setOnClickListener {

            //passo i valori della UI del filtro alle rispettive variabili "pubbliche" nel ViewModel
            viewModel.filtroTipoSelezionatoId = dialogBinding.toggleGroupTipo.checkedButtonId
            viewModel.soloOperative = dialogBinding.switchSoloOperative.isChecked

            viewModel.carburantiSelezionatiIds.clear()
            viewModel.carburantiSelezionatiIds.addAll(dialogBinding.chipGroupCarburante.checkedChipIds)

            viewModel.connettoriSelezionatiIds.clear()
            viewModel.connettoriSelezionatiIds.addAll(dialogBinding.chipGroupConnettori.checkedChipIds)

            viewModel.applicaFiltri() //richiamo la funzione nel ViewModel che applica i filtri
            aggiornaVisibilitaFiltriRapidi() //aggiorna la visibilità dei filtri rapidi dopo l'applicazione dei filtri avanzati
            dialog.dismiss()
        }

        dialog.show()
    }


     //Aggiorna la visibilità dei chip "Benzina Economica" e "Diesel Economico".
     //Se il filtro selezionato è EV, questi chip vengono nascosti e, se selezionati, resettati a "Tutti".
    private fun aggiornaVisibilitaFiltriRapidi() {

        // Nasconde i filtri "Benzina Economica" e "Diesel Economico" se siamo in modalità EV
        val isEV = viewModel.filtroTipoSelezionatoId == R.id.btnTipoEV
        binding.chipBenzinaEconomica.isVisible = !isEV
        binding.chipDieselEconomico.isVisible = !isEV

        // Se uno dei filtri benzina/diesel era selezionato ma ora siamo in modalità EV, resettiamo a chipAll
        if (isEV && (viewModel.selectedChipId == R.id.chipBenzinaEconomica || viewModel.selectedChipId == R.id.chipDieselEconomico)) {
            binding.filterChipGroup.check(R.id.chipAll)
            viewModel.selectedChipId = R.id.chipAll
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
