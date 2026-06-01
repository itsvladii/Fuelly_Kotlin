package com.example.fuelly.ui.cerca

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.fuelly.R
import com.example.fuelly.repository.data.BenzinaiRepository
import com.example.fuelly.repository.data.ColonnineRepository
import com.example.fuelly.repository.model.Benzinaio
import com.example.fuelly.repository.model.ColonninaEV
import com.example.fuelly.utils.Utils

class CercaViewModel : ViewModel() {

    private val _listaFiltrata = MutableLiveData<List<Any>>()
    val listaFiltrata: LiveData<List<Any>> = _listaFiltrata

    private var listaTotaleOriginale: List<Any> = emptyList()

    // Stato dei filtri
    var query: String = ""
    var filtroTipoSelezionatoId: Int = R.id.btnTipoBenzina
    var soloOperative: Boolean = false
    val carburantiSelezionatiIds = mutableSetOf<Int>()
    val connettoriSelezionatiIds = mutableSetOf<Int>()
    var selectedChipId: Int = R.id.chipAll

    var userLat: Double? = null
    var userLon: Double? = null

    fun caricaDatiIniziali() {
        listaTotaleOriginale = BenzinaiRepository.listaVicini + ColonnineRepository.listaVicini
        applicaFiltri()
    }

    fun applicaFiltri() {
        val q = query.lowercase().trim()

        var filtrata = listaTotaleOriginale.filter { item ->
            // --- A. FILTRO PER CATEGORIA (Benzinai/EV) ---
            val passaTipo = when (filtroTipoSelezionatoId) {
                R.id.btnTipoBenzina -> item is Benzinaio
                R.id.btnTipoEV -> item is ColonninaEV
                else -> true
            }

            // --- B. FILTRO TESTUALE ---
            val passaRicerca = when (item) {
                is Benzinaio -> item.bandiera.lowercase().contains(q) || item.comune.lowercase().contains(q)
                is ColonninaEV -> item.titolo.lowercase().contains(q) || item.indirizzo.lowercase().contains(q)
                else -> false
            }

            // --- C. FILTRO SPECIFICO BENZINAIO ---
            val passaBenzinaio = if (item is Benzinaio && carburantiSelezionatiIds.isNotEmpty()) {
                var match = false
                if (carburantiSelezionatiIds.contains(R.id.chipBenzina) && item.prezzoBenzina > 0) match = true
                if (carburantiSelezionatiIds.contains(R.id.chipGasolio) && item.prezzoDiesel > 0) match = true
                if (carburantiSelezionatiIds.contains(R.id.chipMetano) && item.prezzoMetano > 0) match = true
                if (carburantiSelezionatiIds.contains(R.id.chipGPL) && item.prezzoGPL > 0) match = true
                match
            } else true

            // --- D. FILTRO SPECIFICO EV ---
            val passaEV = if (item is ColonninaEV) {
                val passaOperativo = if (soloOperative) item.stato.contains("Operational", true) else true
                val passaConnettori = if (connettoriSelezionatiIds.isNotEmpty()) {
                    val json = item.connettoriJson?.lowercase() ?: ""
                    var matchConnettore = false
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
        filtrata = when (selectedChipId) {
            R.id.chipBenzinaEconomica -> {
                filtrata.filterIsInstance<Benzinaio>()
                    .filter { it.prezzoBenzina > 0 }
                    .sortedBy { it.prezzoBenzina }
            }
            R.id.chipDieselEconomico -> {
                filtrata.filterIsInstance<Benzinaio>()
                    .filter { it.prezzoDiesel > 0 }
                    .sortedBy { it.prezzoDiesel }
            }
            R.id.chipTopSalvati -> {
                filtrata.filterIsInstance<Benzinaio>()
                    .filter { BenzinaiRepository.listaTopSalvatiIds.contains(it.id) }
                    .sortedBy { BenzinaiRepository.listaTopSalvatiIds.indexOf(it.id) }
            }
            R.id.chipPiuVicini -> {
                filtrata.filterIsInstance<Benzinaio>()
                    .sortedBy { Utils.calcolaDistanza(userLat ?: 0.0, userLon ?: 0.0, it.lat, it.lon) }
            }
            else -> filtrata
        }

        _listaFiltrata.value = filtrata
    }
}
