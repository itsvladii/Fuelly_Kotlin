package com.example.fuelly

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.credentials.*
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.*
import kotlinx.coroutines.launch
import java.util.UUID
import com.example.fuelly.supabase.SupabaseInstance
import com.example.fuelly.utils.Utils
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email

class LoginActivity : AppCompatActivity() {

    private lateinit var credentialManager: CredentialManager

    //client ID ottenuto dalla Google Cloud Console per l'autenticazione OAuth 2.0
    //TODO: da mettere nell' .env e non nel codice, per sicurezza
    private val WEB_CLIENT_ID = "348141692404-0fe09qj3s2a9msl9ndep5sf433hcv1rl.apps.googleusercontent.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        //setup grafico dell'activity
        //
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        enableEdgeToEdge()
        //configuro i colori della status bar e della navigation bar
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
        windowInsetsController.isAppearanceLightNavigationBars = true

        credentialManager = CredentialManager.create(this)

        //listener per il pulsante login con google
        val btnGoogle = findViewById<Button>(R.id.btnGoogle)
        btnGoogle.setOnClickListener {
            startGoogleSignIn()
        }

        //listener per il pulsante registrazione manuale con email e password
        findViewById<TextView>(R.id.btnVaiALogin).setOnClickListener {
            //passo alla pagina di registrazione
            val intent = Intent(this, RegistrazioneActivity::class.java)
            startActivity(intent)
            finish()
        }

        //TODO: da migrare in una funzione a parte, per evitare di avere tutto il codice del login manuale in un unico posto
        val emailEdit = findViewById<TextInputEditText>(R.id.emailEdit)
        val passwordEdit = findViewById<TextInputEditText>(R.id.passwordEdit)
        findViewById<Button>(R.id.btnNext).setOnClickListener {
            val email = emailEdit.text.toString().trim()
            val password = passwordEdit.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_login_failed), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    //eseguiamo
                    SupabaseInstance.client.auth.signInWith(Email) {
                        this.email = email
                        this.password = password
                    }

                    //se il login ha successo, andiamo alla mappa
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, getString(R.string.welcome_back), Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish() // chiude l' activity con il login così non si torna indietro con il tasto back
                    }

                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "Accesso fallito: Credenziali errate", Toast.LENGTH_LONG)
                            .show()
                    }
                }
            }
        }
    }


    private fun startGoogleSignIn() {
        //vado a ricavare le credenziali google
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId = WEB_CLIENT_ID)
            .setNonce(generateNonce())
            .build()

        //creo la richiesta
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        //lancio la richiesta
        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@LoginActivity
                )
                //gestisco il risultato
                handleSignIn(result)
            } catch (e: GetCredentialException) {
                Log.e("Auth", "Errore Google Sign-In: ${e.message}")
            }
        }
    }

    private fun handleSignIn(result: GetCredentialResponse) {
        Log.d("Fuelly_Debug", "handleSignIn CHIAMATA! Tipo credenziale: ${result.credential.type}")
        val credential = result.credential

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {

            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

            //salvo l'utente su Supabase Auth
            lifecycleScope.launch {
                try {
                    Log.d("Supabase_Debug", "Sto inviando il token a Supabase...")
                    //valida il token e crea l'utente su Supabase Auth
                    SupabaseInstance.client.auth.signInWith(IDToken) {
                        idToken = googleIdTokenCredential.idToken
                        provider = Google
                    }

                    //controllo se l'utente ha gia effetuato l'accesso
                    try {
                        val session = SupabaseInstance.client.auth.currentSessionOrNull()
                        //se l'utente ha gia effettuato l'accesso, vado direttamente alla MainActivity, altrimenti alla login
                        if (session != null) {
                            //carico i preferiti dell'utente (se loggato)
                            Utils.benzinaiSalvati(session)
                            Utils.colonnineSalvate(session)

                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            goToMappa()
                            finish()
                        }
                    } catch (e: Exception) {
                        Log.e("Fuelly", "Errore passaggio: ${e.message}")
                    }

                } catch (e: Exception) {
                    Log.e("Supabase_Error", "Errore completo: ", e)
                    e.printStackTrace()
                }
            }
        }
    }

    //funzione per generare un UUID casuale,
    //utilizzato per la sicurezza dell'autenticazione
    private fun generateNonce(): String {
        return UUID.randomUUID().toString()
    }

    //funzione per andare alla mappa tramite intent
    private fun goToMappa() {
        // Evitiamo di aprire l'activity due volte
        if (!isFinishing) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
