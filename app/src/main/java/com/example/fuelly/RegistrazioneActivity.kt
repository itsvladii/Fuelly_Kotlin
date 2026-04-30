package com.example.fuelly

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.fuelly.supabase.SupabaseInstance
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class RegistrazioneActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registrazione)
        enableEdgeToEdge()
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
        windowInsetsController.isAppearanceLightNavigationBars = true


        // Riferimenti ai nuovi ID del layout Material
        val editNome = findViewById<TextInputEditText>(R.id.editNome)
        val editEmail = findViewById<TextInputEditText>(R.id.editEmail)
        val editPassword = findViewById<TextInputEditText>(R.id.editPassword)
        val btnRegistrati = findViewById<Button>(R.id.btnRegistrazione)
        val btnVaiALogin = findViewById<TextView>(R.id.btnVaiALogin)

        // Torna alla login se hai già un account
        btnVaiALogin.setOnClickListener {
            //passo alla pagina di login
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        btnRegistrati.setOnClickListener {
            val nome = editNome.text.toString().trim()
            val email = editEmail.text.toString().trim()
            val password = editPassword.text.toString()

            // Validazione minima
            if (nome.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Tutti i campi sono obbligatori", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "La password deve avere almeno 6 caratteri", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    // REGISTRAZIONE TRAMITE SUPABASE AUTH
                    // Usiamo signUpWith per creare l'utente nel modulo Authentication
                    SupabaseInstance.client.auth.signUpWith(Email) {
                        this.email = email
                        this.password = password
                        // Salviamo il nome nei metadati (così lo avrai nelle recensioni!)
                        data = buildJsonObject {
                            put("full_name", nome)
                        }
                    }

                    runOnUiThread {
                        Toast.makeText(this@RegistrazioneActivity,
                            "Registrazione completata! Controlla la tua email per confermare.",
                            Toast.LENGTH_LONG).show()
                        //passo alla pagina di login
                        val intent = Intent(this@RegistrazioneActivity, LoginActivity::class.java)
                        startActivity(intent)
                        finish()
                    }

                } catch (e: Exception) {
                    Log.e("Fuelly_Auth", "Errore registrazione: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(this@RegistrazioneActivity, "Errore: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}