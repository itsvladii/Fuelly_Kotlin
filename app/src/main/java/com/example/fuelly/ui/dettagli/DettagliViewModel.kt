package com.example.fuelly.ui.dettagli

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fuelly.repository.data.BenzinaiRepository
import com.example.fuelly.repository.data.ColonnineRepository
import com.example.fuelly.repository.model.Benzinaio
import com.example.fuelly.repository.model.ColonninaEV
import com.example.fuelly.repository.model.Recensione
import com.example.fuelly.repository.model.Info
import com.example.fuelly.repository.supabase.SupabaseInstance
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.json.JSONArray

class DettagliViewModel : ViewModel() {

    private val benzinaiRepo = BenzinaiRepository()
    private val colonnineRepo = ColonnineRepository()

    private val _stazione = MutableLiveData<Benzinaio?>()
    val stazione: LiveData<Benzinaio?> = _stazione

    private val _colonnina = MutableLiveData<ColonninaEV?>()
    val colonnina: LiveData<ColonninaEV?> = _colonnina

    private val _isSalvato = MutableLiveData<Boolean>()
    val isSalvato: LiveData<Boolean> = _isSalvato

    private val _recensioni = MutableLiveData<List<Recensione>>()
    val recensioni: LiveData<List<Recensione>> = _recensioni

    private val _infoBenzinaio = MutableLiveData<Info?>()
    val infoBenzinaio: LiveData<Info?> = _infoBenzinaio

    private val _prezziBenzinaio = MutableLiveData<Pair<JSONArray, JSONArray>>()
    val prezziBenzinaio: LiveData<Pair<JSONArray, JSONArray>> = _prezziBenzinaio

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private var currentId: Long = -1
    private var currentTipo: String = ""

    fun initData(id: Long, tipo: String?) {
        currentId = id
        currentTipo = tipo ?: ""
        
        loadElemento()
        checkSalvataggio()
        caricaRecensioni()
        if (currentTipo == "BENZINA") {
            caricaInfoBenzinaio()
        }
    }

    private fun loadElemento() {
        if (currentTipo == "BENZINA") {
            _stazione.value = BenzinaiRepository.listaCompleta.find { it.id.toLong() == currentId }
        } else if (currentTipo == "EV") {
            _colonnina.value = ColonnineRepository.listaCompleta.find { it.id.toLong() == currentId }
        }
    }

    private fun checkSalvataggio() {
        val session = SupabaseInstance.client.auth.currentSessionOrNull() ?: return
        viewModelScope.launch {
            try {
                val salvato = if (currentTipo == "BENZINA") {
                    benzinaiRepo.isBenzinaioSalvato(session.user?.id.toString(), currentId)
                } else {
                    colonnineRepo.isColonninaSalvata(session.user?.id.toString(), currentId)
                }
                _isSalvato.value = salvato
            } catch (e: Exception) {
                _isSalvato.value = false
            }
        }
    }

    fun toggleSalvataggio() {
        val session = SupabaseInstance.client.auth.currentSessionOrNull()
        if (session == null) {
            _error.value = "Devi effettuare il login"
            return
        }

        viewModelScope.launch {
            try {
                val oraSalvato = if (currentTipo == "BENZINA") {
                    benzinaiRepo.toggleSalvato(session.user?.id.toString(), currentId)
                } else {
                    colonnineRepo.toggleSalvata(session.user?.id.toString(), currentId)
                }
                _isSalvato.value = oraSalvato
            } catch (e: Exception) {
                _error.value = "Errore durante il salvataggio"
            }
        }
    }

    fun caricaPrezzi(siglaProvincia: String, soloServito: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val defMapping = async { SupabaseInstance.client.from("province_regioni").select { filter { eq("provincia", siglaProvincia) } } }
                val defPrezzi = async { SupabaseInstance.client.from("prezzi").select { filter { eq("idImpianto", currentId) } } }

                val resMapping = defMapping.await()
                val resPrezzi = defPrezzi.await()

                val nomeRegione = JSONArray(resMapping.data).optJSONObject(0)?.optString("regione") ?: ""
                val resMedie = SupabaseInstance.client.from("media_regionale").select {
                    filter {
                        eq("regione", nomeRegione)
                        eq("isSelf", if (soloServito) "0" else "1")
                    }
                }

                _prezziBenzinaio.value = Pair(JSONArray(resPrezzi.data), JSONArray(resMedie.data))
            } catch (e: Exception) {
                _error.value = "Errore nel caricamento dei prezzi"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun caricaRecensioni() {
        viewModelScope.launch {
            try {
                val lista = if (currentTipo == "BENZINA") {
                    benzinaiRepo.getRecensioni(currentId)
                } else {
                    colonnineRepo.getRecensioni(currentId)
                }
                _recensioni.value = lista
            } catch (e: Exception) {
                _recensioni.value = emptyList()
            }
        }
    }

    fun inserisciRecensione(recensione: Recensione) {
        viewModelScope.launch {
            try {
                if (currentTipo == "BENZINA") {
                    benzinaiRepo.inserisciRecensione(recensione)
                } else {
                    colonnineRepo.inserisciRecensione(recensione)
                }
                caricaRecensioni()
            } catch (e: Exception) {
                _error.value = "Errore nell'invio della recensione"
            }
        }
    }

    fun eliminaRecensione(recensione: Recensione) {
        viewModelScope.launch {
            try {
                if (currentTipo == "BENZINA") {
                    benzinaiRepo.eliminaRecensione(recensione.idRecensione, recensione.idUtente)
                } else {
                    colonnineRepo.eliminaRecensione(recensione.idRecensione, recensione.idUtente)
                }
                caricaRecensioni()
            } catch (e: Exception) {
                _error.value = "Errore nell'eliminazione della recensione"
            }
        }
    }

    private fun caricaInfoBenzinaio() {
        viewModelScope.launch {
            try {
                _infoBenzinaio.value = benzinaiRepo.getInfo(currentId)
            } catch (e: Exception) {
                _infoBenzinaio.value = null
            }
        }
    }

    fun salvaInfoBenzinaio(info: Info) {
        viewModelScope.launch {
            try {
                val nuovaInfo = benzinaiRepo.salvaInfo(info)
                _infoBenzinaio.value = nuovaInfo
            } catch (e: Exception) {
                _error.value = "Errore nel salvataggio delle informazioni"
            }
        }
    }
    
    fun getGestoreBenzinaio(callback: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val gestore = benzinaiRepo.getGestore(currentId)
                callback(gestore)
            } catch (e: Exception) {
                callback("Sconosciuto")
            }
        }
    }
}
