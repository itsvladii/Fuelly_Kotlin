package com.example.fuelly.data

import com.example.fuelly.data.model.LoggedInUser
import java.io.IOException

/**
 * Class that handles authentication w/ login credentials and retrieves user information.
 */
class LoginDataSource {

    fun login(username: String, password: String): Result<LoggedInUser> {
        try {
            // TODO: handle loggedInUser authentication
            //Crea un utente fittizio (Mock) temporaneo per testare l'applicazione.
            // Genera un ID univoco casuale usando UUID e assegna il nome fisso "Jane Doe".
            val fakeUser = LoggedInUser(java.util.UUID.randomUUID().toString(), "Jane Doe")

            //Se tutto va bene nel blocco try, restituisce il risultato di Successo avvolto nella classe 'Result.Success'
            return Result.Success(fakeUser)
        } catch (e: Throwable) {
            return Result.Error(IOException("Error logging in", e))
        }
    }

    fun logout() {
        // TODO: revoke authentication
    }
}