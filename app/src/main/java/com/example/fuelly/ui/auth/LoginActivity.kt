package com.example.fuelly.ui.auth

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
import com.example.fuelly.MainActivity
import com.example.fuelly.R
import com.google.android.libraries.identity.googleid.*
import kotlinx.coroutines.launch
import java.util.UUID
import com.example.fuelly.repository.supabase.SupabaseInstance
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
    private val WEB_CLIENT_ID = "348141692404-0fe09qj3s2a9msl9ndep5sf433hcv1rl.apps.googleusercontent.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        //setup grafico dell'activity
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

        //CHIAMO IL LOGIN MANUALE PASSANDO email e password
        val emailEdit = findViewById<TextInputEditText>(R.id.emailEdit)
        val passwordEdit = findViewById<TextInputEditText>(R.id.passwordEdit)
        findViewById<Button>(R.id.btnNext).setOnClickListener {
            val email = emailEdit.text.toString().trim()
            val password = passwordEdit.text.toString()
            startManualSignIn(email,password)

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
        //Avvia una Coroutine agganciata al ciclo di vita dell'Activity (lifecycleScope).
        lifecycleScope.launch {
            try {
                // 4. Esegue la richiesta effettiva. Questo comando farà apparire a schermo
                // il bottom sheet nativo di Android che mostra gli account Google disponibili sul telefono.
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@LoginActivity
                )
                //gestisco il risultato
                // 5. Se l'utente seleziona l'account e l'operazione ha successo,
                // passa il risultato (che contiene il token ID di Google) al metodo handleSignIn per completare l'autenticazione.
                handleSignIn(result)

            } catch (e: GetCredentialException) {
                Log.e("Auth", "Errore Google Sign-In: ${e.message}")
            }
        }
    }

    private fun startManualSignIn(email:String,password:String)
    {
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_login_failed), Toast.LENGTH_SHORT).show()
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

    private fun handleSignIn(result: GetCredentialResponse) {
        Log.d("Fuelly_Debug", "handleSignIn CHIAMATA! Tipo credenziale: ${result.credential.type}")

        // 2. Estrae l'oggetto credential dal risultato del Credential Manager
        val credential = result.credential

        // 3. Verifica che la credenziale sia di tipo "Custom" e che corrisponda nello specifico a un ID Token di Google
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            // 4. Converte i dati grezzi della credenziale in un oggetto strutturato GoogleIdTokenCredential
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

            // 5. Avvia una Coroutine per eseguire le chiamate di rete a Supabase in modo asincrono (non bloccante)
            //salvo l'utente su Supabase Auth
            lifecycleScope.launch {
                try {
                    Log.d("Supabase_Debug", "Sto inviando il token a Supabase...")

                    // 6. Invia l'ID Token di Google a Supabase Auth
                    //valida il token e crea l'utente su Supabase Auth
                    SupabaseInstance.client.auth.signInWith(IDToken) {
                        idToken = googleIdTokenCredential.idToken
                        provider = Google
                    }

                    // 7. Blocco try secondario per gestire la navigazione post-login in sicurezza
                    //controllo se l'utente ha gia effetuato l'accesso
                    try {

                        // 8. Recupera la sessione appena creata da Supabase (restituisce null se qualcosa è andato storto)
                        val session = SupabaseInstance.client.auth.currentSessionOrNull()

                        //se l'utente ha gia effettuato l'accesso, vado direttamente alla MainActivity, altrimenti alla login
                        if (session != null) {

                            // 10. Chiamate asincrone per scaricare dal database i benzinai e le colonnine preferite salvate dall'utente
                            //carico i preferiti dell'utente (se loggato)
                            Utils.benzinaiSalvati(session)
                            Utils.colonnineSalvate(session)

                            // 11. Imposta i flag dell'Intent per ripulire la cronologia delle Activity.
                            // Cancella tutte le schermate precedenti (incluso il Login) in modo che premendo "Indietro" l'app si chiuda semplicemente.
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
