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

    private val _listaFiltrata = MutableLiveData<List<Any>>() //versione "privata" della listaFiltrata (visibile e modificabile sono nello scope della ViewModel
    val listaFiltrata: LiveData<List<Any>> = _listaFiltrata //versione "pubblica" della listaFiltrata (read-only e visibile all'esterno)

    private var listaTotaleOriginale: List<Any> = emptyList()


    var query: String = "" //stringa di query (della barra di ricerca)
    var filtroTipoSelezionatoId: Int = R.id.btnTipoBenzina
    var soloOperative: Boolean = false
    val carburantiSelezionatiIds = mutableSetOf<Int>() //per il filtro avanzato dei carburanti selezionati
    val connettoriSelezionatiIds = mutableSetOf<Int>() //per il filtro avanzato dei connettori selezionati
    var selectedChipId: Int = R.id.chipAll //per il filtro rapido nel fragment

    //latitudine e longitudine dell'utente
    var userLat: Double? = null
    var userLon: Double? = null

    //funzione di caricamento dei dati quando l'utente passa a Cerca fragment
    fun caricaDatiIniziali() {
        listaTotaleOriginale = BenzinaiRepository.listaVicini + ColonnineRepository.listaVicini
        applicaFiltri() //richiamo la funzione di applicazione dei filtri (in questo caso non fa nulla)
    }

    //funzione di applicazione dei filtri
    fun applicaFiltri() {
        val q = query.lowercase().trim() //

        var filtrata = listaTotaleOriginale.filter { item ->
            // --- A. FILTRO PER CATEGORIA ( pulsanti Benzinai/EV nei filtri avanzati) ---
            val passaTipo = when (filtroTipoSelezionatoId) {
                R.id.btnTipoBenzina -> item is Benzinaio
                R.id.btnTipoEV -> item is ColonninaEV
                else -> true
            }

            // --- B. FILTRO TESTUALE (barra di ricerca) ---
            val passaRicerca = when (item) {
                is Benzinaio -> item.bandiera.lowercase().contains(q) || item.comune.lowercase().contains(q)
                is ColonninaEV -> item.titolo.lowercase().contains(q) || item.indirizzo.lowercase().contains(q)
                else -> false
            }

            // --- C. FILTRO SPECIFICO CARBURANTE (filtri avanzati) ---
            val passaBenzinaio = if (item is Benzinaio && carburantiSelezionatiIds.isNotEmpty()) {
                var match = false
                if (carburantiSelezionatiIds.contains(R.id.chipBenzina) && item.prezzoBenzina > 0) match = true
                if (carburantiSelezionatiIds.contains(R.id.chipGasolio) && item.prezzoDiesel > 0) match = true
                if (carburantiSelezionatiIds.contains(R.id.chipMetano) && item.prezzoMetano > 0) match = true
                if (carburantiSelezionatiIds.contains(R.id.chipGPL) && item.prezzoGPL > 0) match = true
                match
            } else true

            // --- D. FILTRO SPECIFICO CONNETTORE EV (filtri avanzati)---
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

        // ---FILTRI RAPIDI ---
        filtrata = when (selectedChipId) {
            //in base al chip selezionato, filtro la lista se rispetta i rispettivi parametri
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
                filtrata.filter { item ->
                    when (item) {
                        is Benzinaio -> BenzinaiRepository.listaTopSalvatiIds.contains(item.id)
                        is ColonninaEV -> ColonnineRepository.listaTopSalvatiIds.contains(item.id)
                        else -> false
                    }
                }.sortedBy { item ->
                    when (item) {
                        is Benzinaio -> BenzinaiRepository.listaTopSalvatiIds.indexOf(item.id)
                        is ColonninaEV -> ColonnineRepository.listaTopSalvatiIds.indexOf(item.id)
                        else -> Int.MAX_VALUE
                    }
                }
            }
            R.id.chipPiuVicini -> {
                filtrata.sortedBy { item ->
                    val (lat, lon) = when (item) {
                        is Benzinaio -> item.lat to item.lon
                        is ColonninaEV -> item.lat to item.lon
                        else -> (userLat ?: 0.0) to (userLon ?: 0.0)
                    }
                    Utils.calcolaDistanza(userLat ?: 0.0, userLon ?: 0.0, lat, lon)
                }
            }
            else -> filtrata
        }

        _listaFiltrata.value = filtrata
    }
}
