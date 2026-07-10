package com.example.fuelly.ui.salvati

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.fuelly.repository.data.BenzinaiRepository
import com.example.fuelly.repository.data.ColonnineRepository

class SalvatiViewModel : ViewModel() {

    // Lista dei salvati da mostrare nella UI (polimorfica: Benzinaio o ColonninaEV)
    private val _listaSalvati = MutableLiveData<List<Any>>()
    val listaSalvati: LiveData<List<Any>> get() = _listaSalvati

    // Funzione che seleziona quale lista caricare dal Repository
    //PRECARICARE LA LISTA DEI BENZINAI SALVATI O DELLE COLONNINE
    fun selezionaTipo(checkedId: Int) {
        when (checkedId) {
            com.example.fuelly.R.id.btnSalvatiBenzina -> {

                //assegno alla variabile mutable la lista statica dei salvati del BenzinaioRepository
                _listaSalvati.value = BenzinaiRepository.listaSalvati
            }
            com.example.fuelly.R.id.btnSalvatiEV -> {
                //assegno alla variabile mutable la lista statica dei salvati del ColonnineRepository
                _listaSalvati.value = ColonnineRepository.listaSalvati
            }
        }
    }
}
