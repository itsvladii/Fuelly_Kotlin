package com.example.fuelly.ui.splash

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fuelly.repository.data.BenzinaiRepository
import com.example.fuelly.repository.data.ColonnineRepository
import com.example.fuelly.repository.supabase.SupabaseInstance
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class SplashViewModel : ViewModel() {

    private val benzinaiRepository = BenzinaiRepository() //nuovo oggetto BenzinaiRepository
    private val colonnineRepository = ColonnineRepository() //nuovo oggetto ColonnineRepository

    // true -> Vai in Home, false -> Vai al Login
    // MutableLiveData: dati che possono essere modificati
    //LiveData: dati che non possono essere modificati
    private val _isUserLoggedIn = MutableLiveData<Boolean>() //se l'utente è loggato o meno
    val isUserLoggedIn: LiveData<Boolean> = _isUserLoggedIn //se l'utente è loggato o meno

    private val _isLoading = MutableLiveData<Boolean>() //se stiamo caricando i dati
    val isLoading: LiveData<Boolean> = _isLoading //se stiamo caricando i dati

    private val _error = MutableLiveData<String>() //se c'è un errore
    val error: LiveData<String> = _error //se c'è un errore

    fun precaricaDati(lat: Double, lon: Double) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Carica benzinai vicini
                val viciniBenzina = benzinaiRepository.getBenzinaiVicini(lat, lon)
                BenzinaiRepository.listaVicini = viciniBenzina

                // Carica benzinai più salvati
                val topIds = benzinaiRepository.getTopSalvatiIds()
                BenzinaiRepository.listaTopSalvatiIds = topIds

                // Carica colonnine vicine
                val vicineEV = colonnineRepository.getColonnineVicine(lat, lon)
                ColonnineRepository.listaVicini = vicineEV

                // Carica colonnine più salvate
                val topEVIds = colonnineRepository.getTopSalvatiIds()
                ColonnineRepository.listaTopSalvatiIds = topEVIds

                // Gestisci dati salvati se l'utente è loggato
                val session = SupabaseInstance.client.auth.currentSessionOrNull()
                if (session != null) {
                    val userId = session.user?.id ?: ""
                    if (userId.isNotEmpty()) {
                        BenzinaiRepository.listaSalvati = benzinaiRepository.getBenzinaiSalvati(userId)
                        ColonnineRepository.listaSalvati = colonnineRepository.getColonnineSalvate(userId)
                    }
                    _isUserLoggedIn.value = true
                } else {
                    _isUserLoggedIn.value = false
                }

            } catch (e: Exception) {
                _error.value = e.message ?: "Errore durante il caricamento"
                _isUserLoggedIn.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }
}
