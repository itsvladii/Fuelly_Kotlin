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
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.Google

class LoginActivity : AppCompatActivity() {

    private lateinit var credentialManager: CredentialManager


    private val WEB_CLIENT_ID = "348141692404-0fe09qj3s2a9msl9ndep5sf433hcv1rl.apps.googleusercontent.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        enableEdgeToEdge()
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
        windowInsetsController.isAppearanceLightNavigationBars = true

        credentialManager = CredentialManager.create(this)

        val btnGoogle = findViewById<Button>(R.id.btnGoogle)
        btnGoogle.setOnClickListener {
            startGoogleSignIn()
        }
        val bottoneRegistra:Button=findViewById(R.id.Registra)

        bottoneRegistra.setOnClickListener{

            //passo alla pagina di registrazione
            val intent = Intent(this, RegistrazioneActivity::class.java)
            startActivity(intent)
            finish()

        }
    }



    private fun startGoogleSignIn() {
        //vado a ricavare le credenziali google
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId = WEB_CLIENT_ID)
            .setNonce(generateNonce()) // Opzionale ma raccomandato per sicurezza
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
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {

            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

            //salvo l'utente su Supabase Auth
            lifecycleScope.launch {
                try {
                    Log.d("Supabase_Debug", "Sto inviando il token a Supabase...")
                    // Questo comando valida il token e crea l'utente su Supabase Auth
                    SupabaseInstance.client.auth.signInWith(IDToken) {
                        idToken = googleIdTokenCredential.idToken
                        provider = Google
                    }
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    goToMappa()

                } catch (e: Exception) {
                Log.e("Supabase_Error", "Errore completo: ", e)
                e.printStackTrace()
            }
            }
        }
    }

    private fun generateNonce(): String {
        return UUID.randomUUID().toString()
    }

    private fun goToMappa() {
        // Evitiamo di aprire l'activity due volte
        if (!isFinishing) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}