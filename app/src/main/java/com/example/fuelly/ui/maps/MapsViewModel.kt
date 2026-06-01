package com.example.fuelly.ui.maps

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fuelly.repository.data.BenzinaiRepository
import com.example.fuelly.repository.data.ColonnineRepository
import com.example.fuelly.repository.model.Benzinaio
import com.example.fuelly.repository.model.ColonninaEV
import kotlinx.coroutines.launch

class MapsViewModel : ViewModel() {

    //inizializzo i repository, creando gli oggetti BenzinaiRepository e ColonnineRepository
    private val benzinaiRepository = BenzinaiRepository()
    private val colonnineRepository = ColonnineRepository()

    //lista effettiva dei benzinai
    private val _benzinai = MutableLiveData(Benzinaio.listaVicini) //lista accessibile solo dal ViewModel (posso fare le operazioni CRUD)
    val benzinai: LiveData<List<Benzinaio>> get() = _benzinai //lista "pubblica" di sola lettura, funge da "vista" per l'esterno
    //ogni volta che qualcuno accede a benzinai, viene richiamata la funzione get() che restituisce l'istanza attuale di _benzinai)

    //lista effettiva dei benzinai
    private val _colonnine = MutableLiveData(ColonninaEV.listaVicini) //lista accessibile solo dal ViewModel (posso fare le operazioni CRUD)
    val colonnine: LiveData<List<ColonninaEV>> get() = _colonnine //lista "pubblica" di sola lettura, funge da "vista" per l'esterno

    //variabili booleane per i filtri dei marker
    private val _isBenzinaActive = MutableLiveData(true) //versione "privata" della variabile, usata solo dal ViewModel
    val isBenzinaActive: LiveData<Boolean> = _isBenzinaActive //versione "pubblica" della variabile, funge come "vista"

    private val _isEVActive = MutableLiveData(true)
    val isEVActive: LiveData<Boolean> = _isEVActive

    //da vedere se effetivamente serve
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    //funzione di filtraggio dei marker solo benzinai
    fun toggleBenzinaFilter() {
        _isBenzinaActive.value = !(_isBenzinaActive.value ?: true)
    }

    //funzione di filtraggio dei marker solo elettrico
    fun toggleEVFilter() {
        _isEVActive.value = !(_isEVActive.value ?: true)
    }

    //funzione che effettua la ricerca delle stazioni nella posizione dell'utente
    fun eseguiRicerca(lat: Double, lon: Double) {
        //la "durata" delle operazioni è legata alla durata del ViewModel, e dunque del fragment MapsFragment
        viewModelScope.launch {
            _isLoading.value = true
            try {
                //ricerco i benzinai richiamando la funzione getBenzinaiVicini dalla rispettiva repository
                val listaBenzinai = benzinaiRepository.getBenzinaiVicini(lat, lon)
                //passo i valori dell'output
                _benzinai.value = listaBenzinai
                Benzinaio.listaVicini = listaBenzinai

                //ricerco le colonnine EV richiamando la funzione getColonnineVicine dalla rispettiva repository
                val listaEV = colonnineRepository.getColonnineVicine(lat, lon)
                _colonnine.value = listaEV
                ColonninaEV.listaVicini = listaEV

            } catch (e: Exception) {
                Log.e("MapsViewModel", "Errore ricerca: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
}
