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


        //id dei componenti del activity
        val editNome = findViewById<TextInputEditText>(R.id.editNome)
        val editEmail = findViewById<TextInputEditText>(R.id.editEmail)
        val editPassword = findViewById<TextInputEditText>(R.id.editPassword)
        val btnRegistrati = findViewById<Button>(R.id.btnRegistrazione)
        val btnVaiALogin = findViewById<TextView>(R.id.btnVaiALogin)

        //listener del bottone "torna al login"
        btnVaiALogin.setOnClickListener {
            //passo alla pagina di login
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        //listener della conferma di registrazione
        btnRegistrati.setOnClickListener {
            val nome = editNome.text.toString().trim()
            val email = editEmail.text.toString().trim()
            val password = editPassword.text.toString()

            //verifico se i campi sono popolati (e che la password sia minimo 6 caratteri)
            if (nome.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_fields_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, getString(R.string.error_password_length), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    //salvo l'utente nella tabella auth di supabase (stessa tabella degli utenti google)
                    SupabaseInstance.client.auth.signUpWith(Email) {
                        this.email = email
                        this.password = password
                        // Salviamo il nome nei metadati (così lo avrai nelle recensioni!)
                        data = buildJsonObject {
                            put("full_name", nome)
                        }
                    }

                    //passo alla LoginActivity una volta registrato
                    runOnUiThread {
                        Toast.makeText(this@RegistrazioneActivity,
                            "Registrazione completata! Effettua il Login.",
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