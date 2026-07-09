package com.example.fuelly.data

/**
 * A generic class that holds a value with its loading status.
 * @param <T>
 */

// 1. Definisce una classe sigillata (sealed) chiamata 'Result'.
// Il parametro '<out T : Any>' indica che la classe gestisce un tipo generico 'T' (qualsiasi oggetto non nullo).
// La parola chiave 'out' (covarianza) permette di usare sottotipi più specifici dove è richiesto un tipo più generico.
sealed class Result<out T : Any> {

    // 2. Rappresenta il caso di successo. È una data class che "eredita" da Result<T>
    // e contiene al suo interno il dato reale ('data') restituito dall'operazione.
    data class Success<out T : Any>(val data: T) : Result<T>()

    // 3. Rappresenta il caso di errore. Eredita da Result<Nothing>.
    // 'Nothing' è un tipo speciale in Kotlin che rappresenta l'assenza di un valore,
    // perfetto qui perché in caso di errore non abbiamo nessun dato 'T' da restituire, ma solo un'eccezione ('exception').
    data class Error(val exception: Exception) : Result<Nothing>()

    // 4. Sovrascrive il metodo toString() predefinito per personalizzare la stampa testuale della classe.
    override fun toString(): String {
        return when (this) {
            // Se l'oggetto è di tipo Success, stampa la stringa con il contenuto dei dati
            is Success<*> -> "Success[data=$data]"
            // Se l'oggetto è di tipo Error, stampa la stringa con il dettaglio dell'eccezione verificatasi
            is Error -> "Error[exception=$exception]"
        }
    }
}