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


    //Riceve l'ID e il tipo di impianto, inizializzando lo stato del ViewModel e avviando i controlli sul salvataggio e il caricamento dei dati correlati.
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

    //Cerca la stazione o la colonnina specifica all'interno dei rispettivi repository locali in base all'ID e ne aggiorna il relativo LiveData.
    private fun loadElemento() {
        if (currentTipo == "BENZINA") {
            //cerca la stazione nel repository locale
            _stazione.value = BenzinaiRepository.listaCompleta.find { it.id.toLong() == currentId }
        } else if (currentTipo == "EV") {
            //cerca la colonnina nel repository locale
            _colonnina.value = ColonnineRepository.listaCompleta.find { it.id.toLong() == currentId }
        }
    }

    //Interroga il database Supabase per verificare se l'elemento corrente è già presente tra i preferiti dell'utente loggato.
    private fun checkSalvataggio() {

        //recupero della sessione dell'utente loggato
        val session = SupabaseInstance.client.auth.currentSessionOrNull() ?: return

        //lancio coroutine
        viewModelScope.launch {
            try {
                //controllo se l'elemento corrente è già salvato
                val salvato = if (currentTipo == "BENZINA") {

                    //se è un benzinaio, uso il metodo isBenzinaioSalvato del repository di benzinaio
                    benzinaiRepo.isBenzinaioSalvato(session.user?.id.toString(), currentId)
                } else {

                    //se è una colonnina, uso il metodo isColonninaSalvata del repository di colonnine
                    colonnineRepo.isColonninaSalvata(session.user?.id.toString(), currentId)
                }
                //aggiorno il LiveData isSalvato con il risultato
                _isSalvato.value = salvato

            } catch (e: Exception) {
                _isSalvato.value = false
            }
        }
    }

    // Aggiunge o rimuove l'elemento corrente dai preferiti dell'utente su Supabase, richiedendo obbligatoriamente una sessione di login attiva.
    fun toggleSalvataggio() {

        //recupero della sessione dell'utente loggato
        val session = SupabaseInstance.client.auth.currentSessionOrNull()

        if (session == null) {
            _error.value = "Devi effettuare il login"
            return
        }

        viewModelScope.launch {
            try {
                //controllo se l'elemento corrente è già salvato
                val oraSalvato = if (currentTipo == "BENZINA") {
                    benzinaiRepo.toggleSalvato(session.user?.id.toString(), currentId)
                } else {
                    //applico toggleSalvata dal ColonnineRepository
                    colonnineRepo.toggleSalvata(session.user?.id.toString(), currentId)
                }
                //aggiorno il LiveData isSalvato con il risultato
                _isSalvato.value = oraSalvato
            } catch (e: Exception) {
                _error.value = "Errore durante il salvataggio"
            }
        }
    }

    //Recupera asincronamente da Supabase i prezzi correnti dell'impianto, la mappatura regionale e la media dei prezzi della regione per il confronto.
    fun caricaPrezzi(siglaProvincia: String, soloServito: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 1. Avvia una chiamata asincrona (async) per cercare a quale regione appartiene la provincia corrente.
                // Interroga la tabella "province_regioni" filtrando per la colonna "provincia" usando la variabile 'siglaProvincia'.
                // Essendo 'async', la query parte subito in background senza bloccare il codice successivo.
                val defMapping = async { SupabaseInstance.client.from("province_regioni").select { filter { eq("provincia", siglaProvincia) } } }

                // 2. Avvia una seconda chiamata asincrona in parallelo alla prima.
                // Interroga la tabella "prezzi" per recuperare lo storico o i prezzi attuali dell'impianto specifico ('currentId').
                val defPrezzi = async { SupabaseInstance.client.from("prezzi").select { filter { eq("idImpianto", currentId) } } }

                // 3. Sospende l'esecuzione della coroutine finché la query sulla regione non ha terminato e restituisce il risultato in 'resMapping'.
                val resMapping = defMapping.await()

                // 4. Sospende l'esecuzione finché anche la query sui prezzi non ha terminato, salvando il risultato in 'resPrezzi'.
                val resPrezzi = defPrezzi.await()

                // 5. Converte i dati grezzi ricevuti da Supabase (resMapping.data) in un array JSON.
                // Prende il primo oggetto utile (indice 0) ed estrae il testo associato alla chiave "regione".
                // Se l'array è vuoto o la chiave non esiste, applica il fallback del valore stringa vuota ("").
                val nomeRegione = JSONArray(resMapping.data).optJSONObject(0)?.optString("regione") ?: ""

                //ricavo la media dei prezzi della regione
                val resMedie = SupabaseInstance.client.from("media_regionale").select {
                    filter {
                        eq("regione", nomeRegione)
                        eq("isSelf", if (soloServito) "0" else "1")
                    }
                }
                //setto il livedata con i valori recuperati
                _prezziBenzinaio.value = Pair(JSONArray(resPrezzi.data), JSONArray(resMedie.data))
            } catch (e: Exception) {
                _error.value = "Errore nel caricamento dei prezzi"
            } finally {
                _isLoading.value = false
            }
        }
    }

    //Recupera la lista dei feedback e dei voti degli utenti associati all'ID dell'impianto (sia distributore che colonnina).
    fun caricaRecensioni() {
        viewModelScope.launch {
            try {
                val lista = if (currentTipo == "BENZINA") {

                    //applico getRecensioni dal BenzinaiRepository
                    benzinaiRepo.getRecensioni(currentId)
                } else {

                    //applico getRecensioni dal ColonnineRepository
                    colonnineRepo.getRecensioni(currentId)
                }
                //aggiorno il LiveData con la lista di recensioni
                _recensioni.value = lista

            } catch (e: Exception) {
                _recensioni.value = emptyList()
            }
        }
    }

    //Invia una nuova recensione al database tramite il repository corretto e avvia il successivo aggiornamento della lista visualizzata.
    fun inserisciRecensione(recensione: Recensione) {
        viewModelScope.launch {
            try {
                if (currentTipo == "BENZINA") {
                    //(riferimento al BenzinaioRepository)
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

    //Rimuove una recensione esistente dal database (verificando ID recensione e ID utente) e aggiorna l'elenco dei commenti.
    fun eliminaRecensione(recensione: Recensione) {
        viewModelScope.launch {
            try {
                if (currentTipo == "BENZINA") {
                    //(riferimento al BenzinaioRepository)
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

    //Recupera dal database le informazioni per i distributori di carburante (es. orari, presenza bar/bagno).
    //(riferimento al BenzinaioRepository)
    private fun caricaInfoBenzinaio() {
        viewModelScope.launch {
            try {
                _infoBenzinaio.value = benzinaiRepo.getInfo(currentId)
            } catch (e: Exception) {
                _infoBenzinaio.value = null
            }
        }
    }

    //Aggiorna o inserisce nel database i dati extra di un benzinaio, notificando poi l'interfaccia con le modifiche salvate.
    fun salvaInfoBenzinaio(info: Info) {
        viewModelScope.launch {
            try {
                //(riferimento al BenzinaioRepository)
                val nuovaInfo = benzinaiRepo.salvaInfo(info)
                _infoBenzinaio.value = nuovaInfo
            } catch (e: Exception) {
                _error.value = "Errore nel salvataggio delle informazioni"
            }
        }
    }

    //Esegue una query asincrona per ottenere il nome del gestore dell'impianto e lo restituisce alla UI tramite una funzione di callback.
    fun getGestoreBenzinaio(callback: (String) -> Unit) {
        viewModelScope.launch {
            try {
                //(riferimento al BenzinaioRepository)
                val gestore = benzinaiRepo.getGestore(currentId)
                callback(gestore)
            } catch (e: Exception) {
                callback("Sconosciuto")
            }
        }
    }
}
