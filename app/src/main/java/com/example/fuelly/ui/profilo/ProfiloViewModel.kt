package com.example.fuelly.ui.profilo

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fuelly.repository.data.BenzinaiRepository
import com.example.fuelly.repository.data.ColonnineRepository
import com.example.fuelly.repository.model.Recensione
import com.example.fuelly.repository.supabase.SupabaseInstance
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class ProfiloViewModel : ViewModel() {

    //inizializzazione dei repository
    private val benzinaiRepo = BenzinaiRepository()
    private val colonnineRepo = ColonnineRepository()

    private val _recensioni = MutableLiveData<List<Recensione>>()
    val recensioni: LiveData<List<Recensione>> = _recensioni

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _logoutSuccess = MutableLiveData<Boolean>()
    val logoutSuccess: LiveData<Boolean> = _logoutSuccess

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    fun caricaDatiUtente(userId: String?) {
        if (userId == null) return
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                //carichiamo entrambe le tipologie di recensioni in parallelo
                val defBenzina = async { benzinaiRepo.getRecensioniUtente(userId) }
                val defEV = async { colonnineRepo.getRecensioniUtente(userId) }

                //aspettiamo che finiscano la chiamata Supabase
                val listaRecBenzina = defBenzina.await()
                val listaRecEV = defEV.await()

                //uniamo le liste e le ordiniamo per data
                _recensioni.value = (listaRecBenzina + listaRecEV).sortedByDescending { it.idRecensione }
            } catch (e: Exception) {
                _error.value = e.message ?: "Errore nel caricamento delle recensioni"
            } finally {
                _isLoading.value = false
            }
        }
    }

    //funzione di eliminazione della recensione
    fun eliminaRecensione(recensione: Recensione) {
        viewModelScope.launch {
            try {
                //in base alla tipologia di stazione della recensione, richiamo la funzione di eliminazione
                //della recensione nel rispettivo repository
                if (recensione.tipo == "BENZINA") {
                    benzinaiRepo.eliminaRecensione(recensione.idRecensione, recensione.idUtente)
                } else {
                    colonnineRepo.eliminaRecensione(recensione.idRecensione, recensione.idUtente)
                }
                
                //aggiorniamo la lista locale dopo l'eliminazione
                val listaAttuale = _recensioni.value?.toMutableList()
                listaAttuale?.removeAll { it.idRecensione == recensione.idRecensione }
                _recensioni.value = listaAttuale ?: emptyList()
                
            } catch (e: Exception) {
                _error.value = "Impossibile eliminare la recensione"
            }
        }
    }

    //funzione di logout
    fun logout() {
        viewModelScope.launch {
            try {
                SupabaseInstance.client.auth.signOut() //effettuo la funzione di logout fornita da Supabase
                _logoutSuccess.value = true
            } catch (e: Exception) {
                _error.value = "Errore durante il logout"
            }
        }
    }
}
